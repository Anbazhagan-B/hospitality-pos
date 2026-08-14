# ---------------------------------------------------------------------------
# S3 - cook-service batch output
#
# Replaces the pod-local ./output/cook-orders path backed by an emptyDir, which
# loses the file on restart and is invisible to every other replica.
# ---------------------------------------------------------------------------

resource "aws_s3_bucket" "cook_output" {
  bucket = "${local.name}-cook-output-${data.aws_caller_identity.current.account_id}"
  tags   = merge(local.tags, { Purpose = "cook-service batch output" })
}

resource "aws_s3_bucket_public_access_block" "cook_output" {
  bucket = aws_s3_bucket.cook_output.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "cook_output" {
  bucket = aws_s3_bucket.cook_output.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_versioning" "cook_output" {
  bucket = aws_s3_bucket.cook_output.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "cook_output" {
  bucket = aws_s3_bucket.cook_output.id

  rule {
    id     = "expire-batch-output"
    status = "Enabled"

    filter {}

    # Batch output is a transient artifact a terminal downloads once. Keeping it
    # for 90 days is generous; keeping it forever is a slowly growing bill.
    expiration {
      days = 90
    }

    noncurrent_version_expiration {
      noncurrent_days = 30
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

# ---------------------------------------------------------------------------
# Secrets Manager
#
# The JWT signing key. Currently committed as a literal in docker-compose.yml
# and configmap.yml, and shared by all seven services - so compromising the
# lowest-value service lets an attacker mint a valid token for the payment
# service.
#
# Terraform generates the initial value; nobody ever types or reads it. The
# database password is not here because RDS manages its own via
# manage_master_user_password in data-stores.tf.
# ---------------------------------------------------------------------------

resource "random_password" "jwt_secret" {
  length  = 64
  special = false # base64-safe; HS256 needs >= 256 bits of key material
}

resource "aws_secretsmanager_secret" "jwt" {
  name        = "${local.name}/jwt-signing-key"
  description = "HMAC signing key for POS service JWTs"

  # 7 days rather than the 30-day default so a mistaken destroy in dev can be
  # cleaned up without waiting a month for the name to be reusable.
  recovery_window_in_days = var.environment == "prod" ? 30 : 7

  tags = local.tags
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id = aws_secretsmanager_secret.jwt.id
  secret_string = jsonencode({
    JWT_SECRET = random_password.jwt_secret.result
  })

  lifecycle {
    # Lets the value be rotated out of band without Terraform reverting it to
    # the originally generated one on the next apply.
    ignore_changes = [secret_string]
  }
}

# ---------------------------------------------------------------------------
# External Secrets custom resources are NOT defined here.
#
# Two reasons, both of which make a Terraform-managed CR fail on a fresh
# cluster:
#
#   1. Ordering. A ClusterSecretStore is an instance of a CRD that only exists
#      once the External Secrets Operator has been installed. Terraform would
#      try to create it against a cluster where its own kind is unknown.
#
#   2. kubernetes_manifest validates against the live API server at *plan* time,
#      not apply time. On a cluster that does not exist yet, planning fails
#      outright - so `terraform plan` on a clean account never succeeds.
#
# The CRs live in infrastructure/kubernetes/external-secrets.yml and are applied
# after the operator is installed. That is also the better separation: this
# stack provisions infrastructure, and cluster contents are deployed the same
# way every other manifest is.
#
# The two values those manifests need are exposed as outputs:
#   terraform output -raw jwt_secret_name
#   terraform output -raw rds_master_user_secret_arn
# ---------------------------------------------------------------------------
