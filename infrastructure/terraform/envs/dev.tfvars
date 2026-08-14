# Development - optimised for cost, not resilience.
environment = "dev"
region      = "ap-south-1"

# One NAT gateway instead of three. Saves roughly two thirds of the NAT cost and
# accepts that losing that AZ removes outbound internet from every private
# subnet.
single_nat_gateway = true
az_count           = 3

kubernetes_version      = "1.31"
node_instance_types     = ["t3.large"]
node_capacity_type      = "SPOT"
node_group_min_size     = 2
node_group_max_size     = 4
node_group_desired_size = 2

rds_instance_class    = "db.t4g.micro"
rds_allocated_storage = 20
rds_multi_az          = false

redis_node_type = "cache.t4g.micro"

# Kafka runs in-cluster in dev. MSK bills for three brokers continuously and
# there is no scale to zero.
enable_msk = false

extra_tags = {
  CostCenter = "engineering"
  AutoStop   = "true"
}
