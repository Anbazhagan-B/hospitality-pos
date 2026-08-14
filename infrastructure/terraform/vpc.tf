module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = "${local.name}-vpc"
  cidr = var.vpc_cidr
  azs  = local.azs

  private_subnets = local.private_subnets
  public_subnets  = local.public_subnets
  intra_subnets   = local.intra_subnets

  enable_nat_gateway = true
  single_nat_gateway = var.single_nat_gateway

  # Required by EKS: the kubelet and in-cluster DNS resolution both depend on
  # VPC-provided DNS.
  enable_dns_hostnames = true
  enable_dns_support   = true

  # These tags are how the AWS Load Balancer Controller discovers where to place
  # load balancers. Without them, a Service of type LoadBalancer or an Ingress
  # stays in Pending with no obvious explanation.
  public_subnet_tags = {
    "kubernetes.io/role/elb" = 1
  }
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb" = 1
  }

  tags = local.tags
}

# ---------------------------------------------------------------------------
# VPC endpoints
#
# Without these, every image pull from ECR and every Secrets Manager lookup
# leaves the VPC through the NAT gateway and is billed per GB. Container images
# are large and pulled on every scale-up, so the S3 and ECR endpoints usually
# pay for themselves quickly. The S3 gateway endpoint in particular is free.
# ---------------------------------------------------------------------------
module "vpc_endpoints" {
  source  = "terraform-aws-modules/vpc/aws//modules/vpc-endpoints"
  version = "~> 5.0"

  vpc_id = module.vpc.vpc_id

  endpoints = {
    s3 = {
      service         = "s3"
      service_type    = "Gateway"
      route_table_ids = module.vpc.private_route_table_ids
      tags            = { Name = "${local.name}-s3-endpoint" }
    }
    ecr_api = {
      service             = "ecr.api"
      private_dns_enabled = true
      subnet_ids          = module.vpc.private_subnets
      security_group_ids  = [aws_security_group.vpc_endpoints.id]
    }
    ecr_dkr = {
      service             = "ecr.dkr"
      private_dns_enabled = true
      subnet_ids          = module.vpc.private_subnets
      security_group_ids  = [aws_security_group.vpc_endpoints.id]
    }
    secretsmanager = {
      service             = "secretsmanager"
      private_dns_enabled = true
      subnet_ids          = module.vpc.private_subnets
      security_group_ids  = [aws_security_group.vpc_endpoints.id]
    }
  }

  tags = local.tags
}

resource "aws_security_group" "vpc_endpoints" {
  name_prefix = "${local.name}-vpce-"
  description = "Allow HTTPS from inside the VPC to interface endpoints"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description = "HTTPS from within the VPC"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = merge(local.tags, { Name = "${local.name}-vpce" })

  lifecycle {
    create_before_destroy = true
  }
}
