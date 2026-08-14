# Backend configuration for the prod environment.
#   terraform init -backend-config=envs/prod.backend.hcl
#
# A separate state key per environment means a mistake while applying dev can
# never corrupt prod state. Ideally a separate AWS account entirely.
bucket = "pos-terraform-state-CHANGE-ME"
key    = "prod/eks/terraform.tfstate"
region = "ap-south-1"
