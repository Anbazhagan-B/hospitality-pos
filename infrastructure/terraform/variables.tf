variable "project" {
  description = "Project name, used as the prefix for every resource name."
  type        = string
  default     = "pos"
}

variable "environment" {
  description = "Environment name. Drives resource naming and sizing."
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of: dev, staging, prod."
  }
}

variable "region" {
  description = "AWS region."
  type        = string
  default     = "ap-south-1"
}

# ---------------------------------------------------------------------------
# Networking
# ---------------------------------------------------------------------------

variable "vpc_cidr" {
  description = "CIDR for the VPC. Must be large enough for the VPC CNI - see the note in vpc.tf."
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Number of availability zones. Three is the minimum for a quorum-based broker like MSK."
  type        = number
  default     = 3

  validation {
    condition     = var.az_count >= 2 && var.az_count <= 4
    error_message = "az_count must be between 2 and 4."
  }
}

variable "single_nat_gateway" {
  description = <<-EOT
    Use one NAT gateway for all AZs instead of one per AZ.

    Cheaper (a NAT gateway is billed hourly plus per GB) but it is a
    single-AZ dependency: losing that AZ removes outbound internet for every
    private subnet. Acceptable in dev, not in prod.
  EOT
  type        = bool
  default     = true
}

# ---------------------------------------------------------------------------
# EKS
# ---------------------------------------------------------------------------

variable "kubernetes_version" {
  description = "EKS control plane version."
  type        = string
  default     = "1.31"
}

variable "cluster_endpoint_public_access" {
  description = "Expose the Kubernetes API to the internet. Should be false in prod, with access via VPN or a bastion."
  type        = bool
  default     = true
}

variable "cluster_endpoint_public_access_cidrs" {
  description = <<-EOT
    CIDRs permitted to reach the public API endpoint.

    The default of 0.0.0.0/0 leaves the API reachable from anywhere. It is still
    authenticated, but it should be narrowed to your office or VPN range.
  EOT
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "node_instance_types" {
  description = "Instance types for the general managed node group."
  type        = list(string)
  default     = ["t3.large"]
}

variable "node_group_min_size" {
  description = "Minimum nodes in the general node group."
  type        = number
  default     = 2
}

variable "node_group_max_size" {
  description = "Maximum nodes. Must leave headroom above the sum of every HPA maxReplicas, or pods sit Pending."
  type        = number
  default     = 6
}

variable "node_group_desired_size" {
  description = "Initial node count."
  type        = number
  default     = 2
}

variable "node_capacity_type" {
  description = "ON_DEMAND or SPOT. Spot is far cheaper but nodes can be reclaimed with two minutes' notice."
  type        = string
  default     = "ON_DEMAND"

  validation {
    condition     = contains(["ON_DEMAND", "SPOT"], var.node_capacity_type)
    error_message = "node_capacity_type must be ON_DEMAND or SPOT."
  }
}

# ---------------------------------------------------------------------------
# Data stores
# ---------------------------------------------------------------------------

variable "rds_instance_class" {
  description = "RDS instance class for the POS Postgres database."
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_allocated_storage" {
  description = "Initial storage in GB. Autoscales up to rds_max_allocated_storage."
  type        = number
  default     = 20
}

variable "rds_max_allocated_storage" {
  description = "Storage autoscaling ceiling in GB."
  type        = number
  default     = 100
}

variable "rds_multi_az" {
  description = "Synchronous standby in a second AZ. Roughly doubles cost; required for any real RTO commitment."
  type        = bool
  default     = false
}

variable "postgres_version" {
  description = "Postgres engine version. Matches the postgres:15 image used in docker-compose."
  type        = string
  default     = "15.7"
}

variable "redis_node_type" {
  description = "ElastiCache node type for the service-layer cache."
  type        = string
  default     = "cache.t4g.micro"
}

variable "redis_version" {
  description = "Redis engine version. Matches redis:7.2-alpine in docker-compose."
  type        = string
  default     = "7.1"
}

variable "enable_msk" {
  description = <<-EOT
    Provision Amazon MSK for the order-events and cache-invalidation topics.

    MSK is the single most expensive component in this stack - three brokers run
    continuously and there is no scale-to-zero. Off by default so a dev
    environment can be stood up cheaply; run Kafka in-cluster there instead.
  EOT
  type        = bool
  default     = false
}

variable "msk_instance_type" {
  description = "MSK broker instance type."
  type        = string
  default     = "kafka.t3.small"
}

variable "kafka_version" {
  description = "MSK Kafka version."
  type        = string
  default     = "3.6.0"
}

# ---------------------------------------------------------------------------
# Application
# ---------------------------------------------------------------------------

variable "services" {
  description = "POS services that get an ECR repository. Must match the Maven module names."
  type        = list(string)
  default = [
    "employee-service",
    "enterprise-management-service",
    "check-service",
    "payment-gateway-service",
    "kitchen-display-service",
    "admin-panel-service",
    "cook-service",
  ]
}

variable "kubernetes_namespace" {
  description = "Namespace the POS services run in. Must match the manifests in infrastructure/kubernetes."
  type        = string
  default     = "pos-system"
}

variable "extra_tags" {
  description = "Additional tags merged into every resource."
  type        = map(string)
  default     = {}
}
