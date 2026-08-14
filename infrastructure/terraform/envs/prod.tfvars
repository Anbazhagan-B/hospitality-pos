# Production - optimised for resilience and auditability.
environment = "prod"
region      = "ap-south-1"

# One NAT gateway per AZ. A NAT gateway is a zonal resource, so sharing one
# makes every private subnet in the VPC depend on a single AZ.
single_nat_gateway = false
az_count           = 3

kubernetes_version = "1.31"

# The Kubernetes API is not exposed to the internet. Access is via VPN or a
# bastion inside the VPC.
cluster_endpoint_public_access = false

# On-demand, not spot. A spot reclamation gives two minutes' notice, which is
# survivable for stateless services but not something to accept by default on a
# payment path.
node_instance_types     = ["m6i.large"]
node_capacity_type      = "ON_DEMAND"
node_group_min_size     = 3
node_group_max_size     = 12
node_group_desired_size = 3

rds_instance_class        = "db.m6g.large"
rds_allocated_storage     = 100
rds_max_allocated_storage = 500
# Synchronous standby in a second AZ. Roughly doubles RDS cost and is the
# difference between a real RTO commitment and hoping.
rds_multi_az = true

redis_node_type = "cache.m6g.large"

enable_msk        = true
msk_instance_type = "kafka.m5.large"

extra_tags = {
  CostCenter = "operations"
  Compliance = "pci-dss"
  DataClass  = "cardholder-adjacent"
}
