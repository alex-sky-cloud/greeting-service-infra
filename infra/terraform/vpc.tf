# =============================================================================
# vpc.tf — Virtual Private Cloud для изоляции сети.
# Документация провайдера: https://registry.terraform.io/providers/timeweb-cloud/timeweb-cloud/latest/docs/resources/vpc
# =============================================================================

resource "twc_vpc" "main" {
  name        = "${var.project_name}-vpc"
  description = "Приватная сеть проекта ${var.project_name}"
  subnet_v4   = var.vpc_subnet
  location    = var.location
}
