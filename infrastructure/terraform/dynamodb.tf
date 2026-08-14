# ---------------------------------------------------------------------------
# DynamoDB table backing check-service.
#
# One item per check, holding its lines, modifiers and payments. Access patterns
# are fixed and there are only four, so the index design follows directly from
# them - which is the whole discipline of DynamoDB modelling: the queries come
# first, the table second.
#
#   by id                -> GetItem on pk = CHECK#<id>
#   by check number      -> GetItem (the number encodes the id)
#   open checks for org  -> Query gsi1-org-status
#   all checks for org   -> Query gsi2-org-opened, newest first
#
# No access pattern requires a Scan, which is what keeps latency and cost flat
# as the table grows.
# ---------------------------------------------------------------------------
resource "aws_dynamodb_table" "checks" {
  name = "${local.name}-checks"

  # Restaurant traffic is spiky and predictable in shape but not in magnitude:
  # dead at 15:00, peaking at 19:30. On-demand bills per request with no
  # capacity planning and no throttling when a Friday runs hot. Provisioned
  # capacity is cheaper only at sustained, flat load - which this is not.
  billing_mode = "PAY_PER_REQUEST"

  hash_key = "pk"

  # Only attributes that are keys need declaring. Everything else on the item is
  # schemaless, which is precisely why a check's line items can carry modifiers
  # of varying shape without a migration.
  attribute {
    name = "pk"
    type = "S"
  }

  attribute {
    name = "gsi1pk"
    type = "S"
  }

  attribute {
    name = "gsi1sk"
    type = "S"
  }

  attribute {
    name = "gsi2pk"
    type = "S"
  }

  attribute {
    name = "gsi2sk"
    type = "S"
  }

  # Open checks for one organisation, sorted by when they were opened. The
  # partition key leads with the organisation, so a query structurally cannot
  # cross tenants - isolation by key design rather than by a WHERE clause
  # someone has to remember.
  global_secondary_index {
    name            = "gsi1-org-status"
    hash_key        = "gsi1pk"
    range_key       = "gsi1sk"
    projection_type = "ALL"
  }

  # Every check for one organisation, newest first.
  global_secondary_index {
    name      = "gsi2-org-opened"
    hash_key  = "gsi2pk"
    range_key = "gsi2sk"
    # ALL rather than KEYS_ONLY because the caller renders a list of checks and
    # would otherwise need a GetItem per row - the classic N+1, moved to the
    # index layer.
    projection_type = "ALL"
  }

  # Continuous backups with restore to any second in the last 35 days. A check
  # is a financial record; losing a day of them to a bad deploy is not
  # recoverable by any other means.
  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }

  # NEW_AND_OLD_IMAGES gives a consumer both states of every change. This is the
  # clean fix for the transactional-outbox problem in check-service: the current
  # code writes to the database and then publishes to Kafka, and a broker
  # failure loses the event while the check is already committed. With Streams
  # the change log IS the commit, so an event cannot be lost - a Lambda or KCL
  # consumer forwards it to Kafka at least once.
  stream_enabled   = true
  stream_view_type = "NEW_AND_OLD_IMAGES"

  # Refuses `terraform destroy` in production. Deliberate friction.
  deletion_protection_enabled = var.environment == "prod"

  tags = merge(local.tags, { Service = "check-service" })

  lifecycle {
    # PAY_PER_REQUEST means these are absent; ignoring them stops a provider
    # upgrade from proposing a spurious capacity change.
    ignore_changes = [read_capacity, write_capacity]
  }
}

# ---------------------------------------------------------------------------
# IRSA role for check-service
#
# Scoped to this one table and its indexes. No dynamodb:Scan and no
# dynamodb:DeleteTable - the application has no legitimate need for either, and
# a Scan issued by mistake against a large table is both a cost incident and a
# latency incident.
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "check_service_dynamodb" {
  statement {
    sid    = "CheckTableAccess"
    effect = "Allow"

    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem",
      "dynamodb:DeleteItem",
      "dynamodb:Query",
      "dynamodb:BatchGetItem",
      "dynamodb:BatchWriteItem",
      "dynamodb:ConditionCheckItem",
      "dynamodb:TransactGetItems",
      "dynamodb:TransactWriteItems",
    ]

    resources = [
      aws_dynamodb_table.checks.arn,
      "${aws_dynamodb_table.checks.arn}/index/*",
    ]
  }
}

resource "aws_iam_policy" "check_service_dynamodb" {
  name        = "${local.name}-check-service-dynamodb"
  description = "Scoped access to the checks table for check-service"
  policy      = data.aws_iam_policy_document.check_service_dynamodb.json
  tags        = local.tags
}

module "check_service_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.39"

  role_name = "${local.name}-check-service"

  role_policy_arns = {
    dynamodb = aws_iam_policy.check_service_dynamodb.arn
  }

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["${var.kubernetes_namespace}:check-service"]
    }
  }

  tags = local.tags
}

resource "kubernetes_service_account_v1" "check_service" {
  metadata {
    name      = "check-service"
    namespace = kubernetes_namespace_v1.pos.metadata[0].name
    annotations = {
      "eks.amazonaws.com/role-arn" = module.check_service_irsa.iam_role_arn
    }
  }
}
