## Раздел 4. Схема Terraform-инфраструктуры

```plantuml
@startuml
left to right direction
skinparam backgroundColor transparent
skinparam linetype polyline
skinparam shadowing false
skinparam defaultTextAlignment center
skinparam nodesep 80
skinparam ranksep 90
skinparam packageStyle rectangle
skinparam ArrowColor #666666
skinparam ArrowFontColor #333333
skinparam ArrowFontSize 12

skinparam package {
  BackgroundColor #f6f3ee
  BorderColor #b89b84
  FontColor #333333
}

skinparam rectangle {
  BackgroundColor #fbf8f3
  BorderColor #b89b84
  FontColor #333333
  RoundCorner 0
}

skinparam node {
  BackgroundColor #d9eef2
  BorderColor #5f8f99
  FontColor #222222
}

skinparam database {
  BackgroundColor #d9eef2
  BorderColor #5f8f99
  FontColor #222222
}

skinparam cloud {
  BackgroundColor #d9eef2
  BorderColor #5f8f99
  FontColor #222222
}

rectangle "Локальный ПК" as LOCAL #f5eee5 {
  rectangle "Terraform файлы\n.tf" as TF_FILES
  rectangle "terraform apply" as TF_APPLY
}

cloud "Timeweb Cloud API\nhttps://api.timeweb.cloud" as API

node "Создаваемые ресурсы" as RESOURCES {

  rectangle "twc_ssh_key\noperator-key" as SSH

  node "Внутри VPC" as VPCBOX {
    node "twc_vpc\ngreeting-service-vpc\n10.10.0.0/24" as VPC
    node "twc_server\ndevtools\n4 CPU • 8 GB RAM • 100 GB" as DEVTOOLS
    node "twc_k8s_cluster\ngreeting-service-k8s\nv1.28 • flannel • ingress=true" as K8S
    node "twc_k8s_node_group\nworkers × 2\n2 CPU • 4 GB RAM • 50 GB" as K8S_NODES
    database "twc_database_cluster\nPostgreSQL\ngreeting-db" as DB_CLUSTER
    database "twc_database_instance\ngreeting_db" as DB_INSTANCE
    rectangle "twc_database_user\ngreeting_user" as DB_USER
  }

  rectangle "twc_s3_bucket\ngreeting-service-artifacts" as S3
}

node "Terraform Outputs" as OUTPUTS {
  rectangle "kubeconfig (sensitive)" as OUT_KUBECONFIG
  rectangle "db_host" as OUT_DB_HOST
  rectangle "db_port" as OUT_DB_PORT
  rectangle "s3_access_key (sensitive)" as OUT_S3_KEY
  rectangle "devtools_public_ip" as OUT_DEVTOOLS_IP
}

TF_FILES --> TF_APPLY
TF_APPLY --> API

API --> SSH
API --> VPC
API --> S3

SSH --> DEVTOOLS
VPC --> DEVTOOLS
VPC --> K8S
VPC --> DB_CLUSTER

K8S --> K8S_NODES
DB_CLUSTER --> DB_INSTANCE
DB_CLUSTER --> DB_USER

K8S --> OUT_KUBECONFIG
DB_INSTANCE --> OUT_DB_HOST
DB_INSTANCE --> OUT_DB_PORT
S3 --> OUT_S3_KEY
DEVTOOLS --> OUT_DEVTOOLS_IP

@enduml
```


### Пояснение

**Что показывает схема**

- Схема показывает, какие ресурсы создаёт команда **`terraform apply`** в **Timeweb Cloud**.
- Источником является локальная Terraform-конфигурация, которая через **Timeweb Cloud API** создаёт облачные ресурсы.
- Отдельно показаны не только сами ресурсы, но и **Terraform Outputs**, которые потом используются в настройке и эксплуатации.

**Что создаётся через Terraform**

- **`twc_ssh_key`** — SSH-ключ для доступа к серверу.
- **`twc_vpc`** — приватная сеть **10.10.0.0/24**, в которой размещаются основные сервисы.
- **`twc_server`** — сервер **devtools**, на котором затем могут работать **Bitbucket Server**, **Docker Registry** и другие утилиты.
- **`twc_k8s_cluster`** — управляемый кластер **Kubernetes**.
- **`twc_k8s_node_group`** — группа worker-нод для запуска приложений в Kubernetes.
- **`twc_database_cluster`** — кластер управляемой базы данных **PostgreSQL**.
- **`twc_database_instance`** — конкретный инстанс базы внутри database cluster.
- **`twc_database_user`** — пользователь базы данных для приложения.
- **`twc_s3_bucket`** — S3-совместимый bucket для артефактов и файлов.

**Как связаны ресурсы**

- Сначала Terraform обращается к **Timeweb Cloud API** и создаёт базовые ресурсы.
- **SSH key** используется сервером **devtools**.
- **VPC** объединяет **devtools**, **Kubernetes** и **PostgreSQL** в одной сети.
- **Kubernetes cluster** использует **node group** для вычислительных ресурсов.
- **Database cluster** содержит **database instance** и **database user**.
- **S3 bucket** создаётся отдельно, но тоже управляется той же Terraform-конфигурацией.

**Что такое Terraform Outputs**

- **`kubeconfig`** — данные для подключения к кластеру **Kubernetes**.
- **`db_host`** и **`db_port`** — адрес и порт базы данных.
- **`s3_access_key`** — ключ доступа к S3-хранилищу.
- **`devtools_public_ip`** — публичный IP сервера **devtools**.


### Пояснение по Terraform

**Что делает `terraform apply`**

- Команда **`terraform apply`** берёт описанные в `.tf` файлах ресурсы и приводит инфраструктуру к этому состоянию. См. [Terraform в Timeweb Cloud](https://timeweb.cloud/docs/terraform)
- Проще говоря, это запуск создания или обновления всей облачной инфраструктуры из кода.

> "Terraform позволяет автоматизированно управлять ресурсами в Timeweb Cloud с помощью удобных файлов конфигурации формата HCL (HashiCorp Configuration Language) и детальных планов вносимых изменений."

> «Terraform позволяет автоматизированно управлять ресурсами в Timeweb Cloud с помощью удобных файлов конфигурации формата HCL (HashiCorp Configuration Language) и детальных планов вносимых изменений.»

**Почему `kubeconfig` и `s3_access_key` помечены как sensitive**

- Terraform требует явно помечать чувствительные output-значения как **sensitive**, чтобы снизить риск случайной утечки. См. [How-to output sensitive data with Terraform](https://support.hashicorp.com/hc/en-us/articles/5175257151891-How-to-output-sensitive-data-with-Terraform)
- Поэтому такие значения не должны свободно светиться в логах и выводе команд.

> "Terraform requires that any root module output containing sensitive data be explicitly marked as sensitive, to confirm your intent."

> «Terraform требует, чтобы любой output корневого модуля, содержащий чувствительные данные, был явно помечен как sensitive, чтобы подтвердить это намерение.»
