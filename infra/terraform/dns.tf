# =============================================================================
# dns.tf — A-записи DNS для доступа к приложению через Ingress.
#
# ЗАЧЕМ ЭТО НУЖНО
# ---------------
# После Helm-деплоя Ingress принимает HTTP-запросы по hostname
# (greeting-dev.example.com, greeting.example.com — см. values-dev.yaml / values-prod.yaml).
# Чтобы имя домена из браузера «доходило» до Ingress, в DNS нужны A-записи:
#   greeting-dev.<ваш-домен>  →  INGRESS_IP
#   greeting.<ваш-домен>      →  INGRESS_IP
#
# INGRESS_IP — публичный IPv4 Service ingress-nginx-controller (LoadBalancer).
# Это НЕ IP devtools-сервера (DEVTOOLS_IP). Ingress IP появляется только после
# развёртывания приложения в K8s и назначения LoadBalancer (Раздел 12.10).
#
# КОГДА ИСПОЛЬЗОВАТЬ ЭТОТ ФАЙЛ
# -----------------------------
# Только если ваш домен делегирован в DNS Timeweb Cloud (NS-записи домена указывают
# на Timeweb). Тогда Terraform создаст A-записи через API.
#
# Если домен у другого регистратора (reg.ru, Cloudflare и т.д.) — создайте A-записи
# вручную в их панели; dns.tf не нужен (enable_dns = false).
#
# КАК ПОЛУЧИТЬ INGRESS_IP (Git Bash, после п. 12.8–12.10)
# ---------------------------------------------------------
#   export KUBECONFIG=/c/Users/sky/.kube/timeweb-greeting.yaml
#   kubectl get svc -n ingress-nginx
#   # EXTERNAL-IP у ingress-nginx-controller — это INGRESS_IP
#
#   INGRESS_IP=$(kubectl get svc -n ingress-nginx \
#     -o jsonpath='{.items[?(@.spec.type=="LoadBalancer")].status.loadBalancer.ingress[0].ip}')
#   echo "$INGRESS_IP"
#
# КАК ПРИМЕНИТЬ (WSL, после первого деплоя в K8s)
# -----------------------------------------------
#   cd /mnt/d/.../greeting-service-infra/infra/terraform
#   export TF_VAR_enable_dns=true
#   export TF_VAR_dns_domain="example.com"      # ваш домен в Timeweb DNS
#   export TF_VAR_ingress_ip="185.250.123.45"   # INGRESS_IP из kubectl
#   terraform plan    # убедитесь, что создаются только twc_dns_rr
#   terraform apply
#
# Документация ресурса:
# https://registry.terraform.io/providers/timeweb-cloud/timeweb-cloud/latest/docs/resources/dns_rr
# =============================================================================

# DNS-зона должна уже существовать в панели Timeweb Cloud (Домены → DNS).
data "twc_dns_zone" "app" {
  count = var.enable_dns ? 1 : 0
  name  = var.dns_domain
}

# dev: greeting-dev.example.com → INGRESS_IP
# Имя должно совпадать с ingress.host в infra/helm/greeting-service/values-dev.yaml
resource "twc_dns_rr" "greeting_dev" {
  count   = var.enable_dns ? 1 : 0
  zone_id = data.twc_dns_zone.app[0].id
  name    = "greeting-dev"
  type    = "A"
  value   = var.ingress_ip
}

# prod: greeting.example.com → INGRESS_IP
# Имя должно совпадать с ingress.host в infra/helm/greeting-service/values-prod.yaml
resource "twc_dns_rr" "greeting_prod" {
  count   = var.enable_dns ? 1 : 0
  zone_id = data.twc_dns_zone.app[0].id
  name    = "greeting"
  type    = "A"
  value   = var.ingress_ip
}
