# ---------------------------------------------------------------------------
# IAM Roles for Service Accounts
#
# The point of IRSA is that no long-lived AWS credential exists anywhere. The
# ServiceAccount is issued a projected OIDC token, IAM trusts this cluster's
# OIDC issuer, and the pod exchanges that token for credentials valid for an
# hour. Nothing to leak, nothing to rotate.
#
# This is what replaces the plaintext JWT_SECRET and database password currently
# committed in infrastructure/kubernetes/configmap.yml.
# ---------------------------------------------------------------------------

module "ebs_csi_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.39"

  role_name             = "${local.name}-ebs-csi"
  attach_ebs_csi_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:ebs-csi-controller-sa"]
    }
  }

  tags = local.tags
}

module "load_balancer_controller_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.39"

  role_name                              = "${local.name}-aws-lb-controller"
  attach_load_balancer_controller_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:aws-load-balancer-controller"]
    }
  }

  tags = local.tags
}

# External Secrets Operator - syncs from AWS Secrets Manager into Kubernetes
# Secrets. Chosen over the raw Secrets Store CSI driver because the same
# operator abstracts Secrets Manager, Azure Key Vault and GCP Secret Manager, so
# the manifests do not have to be rewritten if a deployment lands on another
# cloud.
module "external_secrets_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.39"

  role_name                      = "${local.name}-external-secrets"
  attach_external_secrets_policy = true

  # Scoped to this environment's secrets only. A wildcard here would let the dev
  # cluster read production credentials.
  external_secrets_secrets_manager_arns = [
    "arn:aws:secretsmanager:${var.region}:${data.aws_caller_identity.current.account_id}:secret:${local.name}/*"
  ]

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["external-secrets:external-secrets"]
    }
  }

  tags = local.tags
}

module "cluster_autoscaler_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.39"

  role_name                        = "${local.name}-cluster-autoscaler"
  attach_cluster_autoscaler_policy = true
  cluster_autoscaler_cluster_names = [module.eks.cluster_name]

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:cluster-autoscaler"]
    }
  }

  tags = local.tags
}

# ---------------------------------------------------------------------------
# cook-service: write batch output to S3
#
# Fixes a real defect. CookOrderJsonFileItemWriter writes to ./output/cook-orders
# on the pod filesystem, mounted as emptyDir. That output dies with the pod and
# is invisible to the other replicas, so a client polling for its file has a
# coin-flip chance of hitting the wrong one. It also breaks the "all services
# stateless" constraint the architecture is built on.
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "cook_service_s3" {
  statement {
    sid    = "ReadWriteBatchOutput"
    effect = "Allow"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
    ]
    # Object-level actions on objects; the bucket ARN itself is not valid here.
    resources = ["${aws_s3_bucket.cook_output.arn}/*"]
  }

  statement {
    sid       = "ListBatchOutput"
    effect    = "Allow"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.cook_output.arn]
  }
}

resource "aws_iam_policy" "cook_service_s3" {
  name        = "${local.name}-cook-service-s3"
  description = "Read/write access to the cook-service batch output bucket"
  policy      = data.aws_iam_policy_document.cook_service_s3.json
  tags        = local.tags
}

module "cook_service_irsa" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.39"

  role_name = "${local.name}-cook-service"

  role_policy_arns = {
    s3 = aws_iam_policy.cook_service_s3.arn
  }

  oidc_providers = {
    main = {
      provider_arn = module.eks.oidc_provider_arn
      # Bound to one namespace and one ServiceAccount. A pod in any other
      # namespace presenting a token cannot assume this role.
      namespace_service_accounts = ["${var.kubernetes_namespace}:cook-service"]
    }
  }

  tags = local.tags
}

# ServiceAccount referenced by the deployment manifests. The annotation is the
# link that makes IRSA work - without it the pod falls back to the node role.
resource "kubernetes_service_account_v1" "cook_service" {
  metadata {
    name      = "cook-service"
    namespace = kubernetes_namespace_v1.pos.metadata[0].name
    annotations = {
      "eks.amazonaws.com/role-arn" = module.cook_service_irsa.iam_role_arn
    }
  }
}

# The generic ServiceAccount the other six services run under. Referenced as
# pos-service-account in infrastructure/kubernetes/*.yml, where it was named but
# never actually defined - so those pods would fail to schedule.
resource "kubernetes_service_account_v1" "pos_service_account" {
  metadata {
    name      = "pos-service-account"
    namespace = kubernetes_namespace_v1.pos.metadata[0].name
  }
}
