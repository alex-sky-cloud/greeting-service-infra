terraform {
  required_version = ">= 1.9.0"

  required_providers {
    serverspace = {
      source  = "itglobalcom/serverspace"
      version = "~> 0.3.2"
    }
  }
}

# Configure the Serverspace Provider
provider "serverspace" {
  key = var.api_key
}

# — рекомендую проверить актуальную схему аргументов через
#      `terraform providers schema -json`
# перед сдачей работы,