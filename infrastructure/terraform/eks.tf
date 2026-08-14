module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = local.cluster_name
  cluster_version = var.kubernetes_version

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # Control plane ENIs land in their own small subnets, isolated from pod IP
  # consumption in the main private subnets.
  control_plane_subnet_ids = module.vpc.intra_subnets

  cluster_endpoint_public_access       = var.cluster_endpoint_public_access
  cluster_endpoint_public_access_cidrs = var.cluster_endpoint_public_access_cidrs
  cluster_endpoint_private_access      = true

  # EKS access entries rather than the aws-auth ConfigMap. The ConfigMap was
  # unversioned, un-auditable, and a bad edit locked everyone out of the cluster
  # with no way back short of recreating it. API mode makes access an IAM
  # resource like everything else. Kept alongside CONFIG_MAP so any existing
  # tooling that still writes aws-auth keeps working during migration.
  authentication_mode                      = "API_AND_CONFIG_MAP"
  enable_cluster_creator_admin_permissions = true

  cluster_addons = {
    coredns = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
    vpc-cni = {
      most_recent = true
      # Must exist before any node joins, or the first nodes come up without a
      # working CNI and every pod sits in ContainerCreating.
      before_compute = true
      configuration_values = jsonencode({
        env = {
          # Prefix delegation assigns each node a /28 slice rather than
          # individual IPs, which raises pod density per node and slows down
          # subnet IP exhaustion considerably.
          ENABLE_PREFIX_DELEGATION = "true"
          WARM_PREFIX_TARGET       = "1"
        }
      })
    }
    # aws-ebs-csi-driver is deliberately NOT here - see the aws_eks_addon
    # resource below for why.
  }

  eks_managed_node_groups = {
    general = {
      name           = "${local.name}-general"
      instance_types = var.node_instance_types
      capacity_type  = var.node_capacity_type

      min_size     = var.node_group_min_size
      max_size     = var.node_group_max_size
      desired_size = var.node_group_desired_size

      # Bottlerocket has a far smaller attack surface than Amazon Linux: no
      # shell, no package manager, and atomic image-based updates.
      ami_type = "BOTTLEROCKET_x86_64"

      block_device_mappings = {
        xvdb = {
          device_name = "/dev/xvdb"
          ebs = {
            volume_size           = 50
            volume_type           = "gp3"
            encrypted             = true
            delete_on_termination = true
          }
        }
      }

      labels = {
        workload = "general"
      }

      # Instance metadata v2 only, with a hop limit of 1. Without this a
      # compromised pod can reach the metadata endpoint and assume the node's
      # IAM role, bypassing IRSA entirely - the classic container escape to
      # cloud credentials.
      metadata_options = {
        http_endpoint               = "enabled"
        http_tokens                 = "required"
        http_put_response_hop_limit = 1
      }

      tags = local.tags
    }
  }

  # Control plane logs go to CloudWatch. "audit" is the one that answers
  # "who deleted that deployment", and it cannot be enabled retroactively.
  cluster_enabled_log_types              = ["api", "audit", "authenticator", "controllerManager", "scheduler"]
  cloudwatch_log_group_retention_in_days = var.environment == "prod" ? 90 : 14

  # Kubernetes Secrets in etcd are base64, not encrypted, unless a KMS key is
  # attached here. This is what makes `kubectl get secret -o yaml` the only way
  # to read them, rather than anyone with etcd access.
  create_kms_key                  = true
  enable_kms_key_rotation         = true
  kms_key_deletion_window_in_days = 7
  cluster_encryption_config = {
    resources = ["secrets"]
  }

  tags = local.tags
}

# ---------------------------------------------------------------------------
# EBS CSI driver
#
# Created outside the module to break a dependency cycle: the addon needs the
# IRSA role ARN, the IRSA role needs the cluster's OIDC provider ARN, and the
# OIDC provider is an output of the same module the addon would live in.
# Terraform cannot resolve that at module granularity.
# ---------------------------------------------------------------------------
resource "aws_eks_addon" "ebs_csi" {
  cluster_name             = module.eks.cluster_name
  addon_name               = "aws-ebs-csi-driver"
  service_account_role_arn = module.ebs_csi_irsa.iam_role_arn

  # PRESERVE keeps volumes in place if the addon is removed; OVERWRITE lets
  # Terraform take ownership of settings changed by hand.
  resolve_conflicts_on_create = "OVERWRITE"
  resolve_conflicts_on_update = "PRESERVE"

  tags = local.tags
}

# gp3 rather than the built-in gp2 default: cheaper per GB, and IOPS and
# throughput are configurable independently of size.
resource "kubernetes_storage_class_v1" "gp3" {
  metadata {
    name = "gp3"
    annotations = {
      "storageclass.kubernetes.io/is-default-class" = "true"
    }
  }

  storage_provisioner = "ebs.csi.aws.com"
  reclaim_policy      = "Delete"
  # WaitForFirstConsumer, not Immediate. An EBS volume is zonal, so provisioning
  # it before the pod is scheduled can place it in an AZ with no capacity for
  # that pod, leaving it permanently unschedulable.
  volume_binding_mode    = "WaitForFirstConsumer"
  allow_volume_expansion = true

  parameters = {
    type      = "gp3"
    encrypted = "true"
    fsType    = "ext4"
  }

  depends_on = [aws_eks_addon.ebs_csi]
}

resource "kubernetes_namespace_v1" "pos" {
  metadata {
    name = var.kubernetes_namespace
    labels = {
      name        = var.kubernetes_namespace
      environment = var.environment
    }
  }

  depends_on = [module.eks]
}
