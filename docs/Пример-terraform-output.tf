db_host = "10.10.0.5"
db_jdbc_url = "jdbc:postgresql://10.10.0.5:5432/greeting_db"
db_port = 5432
devtools_private_ip = "10.10.0.4"
devtools_public_ip = "186.246.13.7"
k8s_cluster_id = "1094091"
k8s_cluster_status = "started"
kubeconfig = <sensitive>
s3_access_key = <sensitive>
s3_full_name = "greeting-service-artifacts-dev"
s3_hostname = "https://s3.twcstorage.ru"
s3_secret_key = <sensitive>
summary = <<EOT
=========================================================
Инфраструктура greeting-service развёрнута.
=========================================================

Kubernetes cluster ID : 1094091
Kubernetes status     : started

Devtools server IP    : 186.246.13.7
(Bitbucket + Registry)

S3 hostname           : https://s3.twcstorage.ru
S3 full name          : greeting-service-artifacts-dev

Следующий шаг:
1. terraform output -raw kubeconfig > ~/.kube/timeweb-greeting.yaml
2. export KUBECONFIG=~/.kube/timeweb-greeting.yaml
3. kubectl get nodes
=========================================================

EOT