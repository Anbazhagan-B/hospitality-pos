locals {
  name         = "${var.project}-${var.environment}"
  cluster_name = "${local.name}-eks"

  azs = slice(data.aws_availability_zones.available.names, 0, var.az_count)

  # /20 per private subnet (4091 usable) because the AWS VPC CNI assigns every
  # pod a real VPC IP. A /24 would cap the whole AZ at ~250 pods regardless of
  # how much CPU and memory the nodes have - the most common way an EKS cluster
  # runs out of capacity for reasons nobody expects.
  private_subnets = [for i, _ in local.azs : cidrsubnet(var.vpc_cidr, 4, i)]
  public_subnets  = [for i, _ in local.azs : cidrsubnet(var.vpc_cidr, 8, i + 100)]

  # Small dedicated subnets for the EKS control plane ENIs, kept separate so
  # control plane addressing is never affected by pod IP exhaustion.
  intra_subnets = [for i, _ in local.azs : cidrsubnet(var.vpc_cidr, 8, i + 200)]

  tags = merge(
    {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
      Repository  = "hospitality-pos"
    },
    var.extra_tags,
  )
}

data "aws_availability_zones" "available" {
  state = "available"

  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

data "aws_caller_identity" "current" {}
