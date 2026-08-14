output "cluster_name" {
  description = "EKS cluster name."
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "Kubernetes API endpoint."
  value       = module.eks.cluster_endpoint
}

output "cluster_oidc_issuer_url" {
  description = "OIDC issuer URL. Trust anchor for IRSA, and for workload identity federation from another cloud."
  value       = module.eks.cluster_oidc_issuer_url
}

output "oidc_provider_arn" {
  description = "IAM OIDC provider ARN backing the issuer above."
  value       = module.eks.oidc_provider_arn
}

output "configure_kubectl" {
  description = "Command to point kubectl at this cluster."
  value       = "aws eks update-kubeconfig --region ${var.region} --name ${module.eks.cluster_name}"
}

output "vpc_id" {
  value       = module.vpc.vpc_id
  description = "VPC ID."
}

output "ecr_repository_urls" {
  description = "ECR repository URL per service. Use these in the Kubernetes manifests and Jenkinsfile."
  value       = { for name, repo in aws_ecr_repository.services : name => repo.repository_url }
}

output "rds_endpoint" {
  description = "Postgres endpoint. Supply as SPRING_DATASOURCE_URL."
  value       = aws_db_instance.main.endpoint
}

output "rds_master_user_secret_arn" {
  description = "Secrets Manager ARN holding the RDS-managed master password."
  value       = aws_db_instance.main.master_user_secret[0].secret_arn
}

output "redis_primary_endpoint" {
  description = "ElastiCache primary endpoint. Supply as SPRING_DATA_REDIS_HOST."
  value       = aws_elasticache_replication_group.main.primary_endpoint_address
}

output "msk_bootstrap_brokers_tls" {
  description = "MSK bootstrap servers. Supply as KAFKA_BOOTSTRAP_SERVERS. Null when enable_msk is false."
  value       = var.enable_msk ? aws_msk_cluster.main[0].bootstrap_brokers_tls : null
}

output "cook_output_bucket" {
  description = "S3 bucket for cook-service batch output."
  value       = aws_s3_bucket.cook_output.id
}

output "jwt_secret_arn" {
  description = "Secrets Manager ARN for the JWT signing key. The value itself is never an output."
  value       = aws_secretsmanager_secret.jwt.arn
}

output "jwt_secret_name" {
  description = "Secrets Manager name for the JWT key. Substitute into external-secrets.yml."
  value       = aws_secretsmanager_secret.jwt.name
}

output "irsa_role_arns" {
  description = "IRSA role ARNs, for annotating ServiceAccounts."
  value = {
    ebs_csi                  = module.ebs_csi_irsa.iam_role_arn
    load_balancer_controller = module.load_balancer_controller_irsa.iam_role_arn
    external_secrets         = module.external_secrets_irsa.iam_role_arn
    cluster_autoscaler       = module.cluster_autoscaler_irsa.iam_role_arn
    cook_service             = module.cook_service_irsa.iam_role_arn
  }
}
