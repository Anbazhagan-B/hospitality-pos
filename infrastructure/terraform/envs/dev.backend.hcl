# Backend configuration for the dev environment.
#   terraform init -backend-config=envs/dev.backend.hcl
#
# The bucket must exist before the first init - Terraform cannot create the
# backend it is about to store its own state in. See the bootstrap section of
# the README.
bucket = "pos-terraform-state-CHANGE-ME"
key    = "dev/eks/terraform.tfstate"
region = "ap-south-1"
