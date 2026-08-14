# ---------------------------------------------------------------------------
# Security groups
#
# Each data store accepts traffic only from the EKS node security group, never
# from a CIDR. Referencing a security group means the rule keeps working when
# nodes are replaced and cannot accidentally admit anything else in the VPC.
# ---------------------------------------------------------------------------

resource "aws_security_group" "rds" {
  name_prefix = "${local.name}-rds-"
  description = "Postgres access from EKS nodes only"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description     = "Postgres from EKS nodes"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }

  tags = merge(local.tags, { Name = "${local.name}-rds" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "redis" {
  name_prefix = "${local.name}-redis-"
  description = "ElastiCache access from EKS nodes only"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description     = "Redis from EKS nodes"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }

  tags = merge(local.tags, { Name = "${local.name}-redis" })

  lifecycle {
    create_before_destroy = true
  }
}

# ---------------------------------------------------------------------------
# RDS Postgres
# ---------------------------------------------------------------------------

resource "aws_db_subnet_group" "main" {
  name       = "${local.name}-db"
  subnet_ids = module.vpc.private_subnets
  tags       = local.tags
}

resource "aws_db_parameter_group" "postgres" {
  name_prefix = "${local.name}-pg-"
  family      = "postgres15"

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  # Logs every DDL statement. With ddl-auto still in play this is the only
  # record of what Hibernate actually changed.
  parameter {
    name  = "log_statement"
    value = "ddl"
  }

  tags = local.tags

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_db_instance" "main" {
  identifier = "${local.name}-postgres"

  engine         = "postgres"
  engine_version = var.postgres_version
  instance_class = var.rds_instance_class

  allocated_storage     = var.rds_allocated_storage
  max_allocated_storage = var.rds_max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "pos_db"
  username = "pos_admin"

  # No password in Terraform, therefore none in state. RDS generates it, stores
  # it in Secrets Manager and rotates it. This is what replaces the
  # "pos_password" literal currently committed in configmap.yml - the value now
  # exists only in Secrets Manager and is never seen by a human.
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.postgres.name
  publicly_accessible    = false

  multi_az                = var.rds_multi_az
  backup_retention_period = var.environment == "prod" ? 30 : 7
  backup_window           = "17:00-18:00" # UTC, outside restaurant service hours
  maintenance_window      = "Mon:18:30-Mon:19:30"

  # Point-in-time recovery back to any second inside the retention window. This
  # is the difference between "we restored last night's backup" and "we restored
  # to the second before the bad migration".
  copy_tags_to_snapshot = true

  performance_insights_enabled = var.environment == "prod"
  monitoring_interval          = var.environment == "prod" ? 60 : 0
  monitoring_role_arn          = var.environment == "prod" ? aws_iam_role.rds_monitoring[0].arn : null

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  auto_minor_version_upgrade = true
  apply_immediately          = var.environment != "prod"

  deletion_protection       = var.environment == "prod"
  skip_final_snapshot       = var.environment != "prod"
  final_snapshot_identifier = var.environment == "prod" ? "${local.name}-postgres-final" : null

  tags = local.tags
}

resource "aws_iam_role" "rds_monitoring" {
  count = var.environment == "prod" ? 1 : 0

  name = "${local.name}-rds-monitoring"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "monitoring.rds.amazonaws.com" }
    }]
  })

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  count = var.environment == "prod" ? 1 : 0

  role       = aws_iam_role.rds_monitoring[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# ---------------------------------------------------------------------------
# ElastiCache Redis - service-layer cache
# ---------------------------------------------------------------------------

resource "aws_elasticache_subnet_group" "main" {
  name       = "${local.name}-redis"
  subnet_ids = module.vpc.private_subnets
  tags       = local.tags
}

resource "aws_elasticache_parameter_group" "redis" {
  # Unlike most AWS resources this one has no name_prefix argument, so the name
  # is fixed. Changing family therefore forces a replace under the same name.
  name   = "${local.name}-redis7"
  family = "redis7"

  # Matches the docker-compose configuration. allkeys-lru evicts the
  # least-recently-used key when memory fills; the default of noeviction would
  # start returning OOM errors on write instead, turning a full cache into
  # failing requests rather than cache misses.
  parameter {
    name  = "maxmemory-policy"
    value = "allkeys-lru"
  }
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id = "${local.name}-redis"
  description          = "POS service-layer cache"

  engine               = "redis"
  engine_version       = var.redis_version
  node_type            = var.redis_node_type
  port                 = 6379
  parameter_group_name = aws_elasticache_parameter_group.redis.name

  # One replica in prod so a node failure does not empty the cache and send
  # every read through to Postgres at once.
  num_cache_clusters         = var.environment == "prod" ? 2 : 1
  automatic_failover_enabled = var.environment == "prod"
  multi_az_enabled           = var.environment == "prod"

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.redis.id]

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true

  # No AOF or snapshots. This is a cache, not a datastore - Postgres is the
  # source of truth and everything here is reconstructible. Persisting it would
  # mean stale menu prices surviving a restart, for no benefit.
  snapshot_retention_limit = 0

  auto_minor_version_upgrade = true
  apply_immediately          = var.environment != "prod"

  tags = local.tags
}

# ---------------------------------------------------------------------------
# MSK - order-events and cache-invalidation topics
#
# Off by default. MSK bills for three brokers continuously with no scale to
# zero, and is comfortably the most expensive line item in this stack. In dev,
# run Kafka in-cluster instead.
# ---------------------------------------------------------------------------

resource "aws_security_group" "msk" {
  count = var.enable_msk ? 1 : 0

  name_prefix = "${local.name}-msk-"
  description = "Kafka access from EKS nodes only"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description     = "Kafka TLS from EKS nodes"
    from_port       = 9094
    to_port         = 9094
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }

  ingress {
    description     = "Kafka plaintext from EKS nodes"
    from_port       = 9092
    to_port         = 9092
    protocol        = "tcp"
    security_groups = [module.eks.node_security_group_id]
  }

  tags = merge(local.tags, { Name = "${local.name}-msk" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_msk_configuration" "main" {
  count = var.enable_msk ? 1 : 0

  name           = "${local.name}-msk-config"
  kafka_versions = [var.kafka_version]

  # auto.create.topics.enable is left false deliberately. A typo'd topic name in
  # a producer would otherwise silently create a new topic and the events would
  # vanish into it with no error - which is exactly how "orders never reached
  # the kitchen" happens.
  server_properties = <<-PROPERTIES
    auto.create.topics.enable=false
    default.replication.factor=3
    min.insync.replicas=2
    num.partitions=6
    log.retention.hours=168
    unclean.leader.election.enable=false
  PROPERTIES
}

resource "aws_msk_cluster" "main" {
  count = var.enable_msk ? 1 : 0

  cluster_name           = "${local.name}-kafka"
  kafka_version          = var.kafka_version
  number_of_broker_nodes = var.az_count

  broker_node_group_info {
    instance_type   = var.msk_instance_type
    client_subnets  = module.vpc.private_subnets
    security_groups = [aws_security_group.msk[0].id]

    storage_info {
      ebs_storage_info {
        volume_size = 100
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.main[0].arn
    revision = aws_msk_configuration.main[0].latest_revision
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  open_monitoring {
    prometheus {
      jmx_exporter {
        enabled_in_broker = true
      }
      node_exporter {
        enabled_in_broker = true
      }
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.msk[0].name
      }
    }
  }

  tags = local.tags
}

resource "aws_cloudwatch_log_group" "msk" {
  count = var.enable_msk ? 1 : 0

  name              = "/aws/msk/${local.name}"
  retention_in_days = var.environment == "prod" ? 30 : 7
  tags              = local.tags
}
