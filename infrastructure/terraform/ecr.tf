resource "aws_ecr_repository" "services" {
  for_each = toset(var.services)

  name = "${var.project}/${each.value}"

  # IMMUTABLE is the fix for the ":latest" problem in the current manifests. A
  # mutable tag means two pods in the same ReplicaSet can pull different builds,
  # and there is no fixed artifact to roll back to because the tag has moved.
  #
  # NOTE: this makes `docker push .../<service>:latest` fail on the second build.
  # check-service/Jenkinsfile pushes both an immutable tag and :latest - the
  # :latest push has to be removed. The pipeline already computes a perfectly
  # good immutable tag (${BUILD_NUMBER}-${GIT_COMMIT}); the manifests just need
  # to use it.
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = merge(local.tags, { Service = each.value })
}

# Untagged layers accumulate on every rebuild and are billed per GB-month.
resource "aws_ecr_lifecycle_policy" "services" {
  for_each = aws_ecr_repository.services

  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after 7 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 7
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep the 30 most recent tagged images"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["v", "main", "release"]
          countType     = "imageCountMoreThan"
          countNumber   = 30
        }
        action = { type = "expire" }
      },
    ]
  })
}

# Lets the EKS node role pull without embedding registry credentials in a
# Kubernetes imagePullSecret.
data "aws_iam_policy_document" "ecr_pull" {
  statement {
    sid    = "AllowClusterNodesToPull"
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = [module.eks.eks_managed_node_groups["general"].iam_role_arn]
    }

    actions = [
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:BatchCheckLayerAvailability",
    ]
  }
}

resource "aws_ecr_repository_policy" "services" {
  for_each = aws_ecr_repository.services

  repository = each.value.name
  policy     = data.aws_iam_policy_document.ecr_pull.json
}
