terraform {
  # Partial configuration on purpose. Backend blocks cannot interpolate
  # variables, so the bucket and per-environment state key are supplied at init:
  #
  #   terraform init -backend-config=envs/dev.backend.hcl
  #
  # Each environment gets its own state key, so a mistake while applying dev can
  # never corrupt prod state.
  backend "s3" {
    # use_lockfile puts the lock in S3 itself rather than a DynamoDB table.
    # Requires Terraform >= 1.10. On older versions replace with
    # dynamodb_table = "<lock-table>".
    use_lockfile = true
    encrypt      = true
  }
}
