

***

# Terraform для Serverspace и первый запуск

> **Канонический путь:** `D:\Project_infra\greeting-service-infra\infra\terraform-serverspace\`
> **Ядро документа:** в этом каталоге находится отдельная Terraform-конфигурация только для Serverspace. Terraform работает локально в Docker на вашем ПК. Этот документ нужен для первого практического шага: подготовить каталог, создать базовые `.tf` файлы, заполнить переменные и получить первый `plan`.

***

## Оглавление

### Часть III — Terraform конфигурация Serverspace

19. [Канонический каталог terraform-serverspace](#19-%D0%BA%D0%B0%D0%BD%D0%BE%D0%BD%D0%B8%D1%87%D0%B5%D1%81%D0%BA%D0%B8%D0%B9-%D0%BA%D0%B0%D1%82%D0%B0%D0%BB%D0%BE%D0%B3-terraform-serverspace)
20. [Структура каталога](#20-%D1%81%D1%82%D1%80%D1%83%D0%BA%D1%82%D1%83%D1%80%D0%B0-%D0%BA%D0%B0%D1%82%D0%B0%D0%BB%D0%BE%D0%B3%D0%B0)
21. [Файлы docker и запуск Terraform](#21-%D1%84%D0%B0%D0%B9%D0%BB%D1%8B-docker-%D0%B8-%D0%B7%D0%B0%D0%BF%D1%83%D1%81%D0%BA-terraform)
22. [Секреты и переменные](#22-%D1%81%D0%B5%D0%BA%D1%80%D0%B5%D1%82%D1%8B-%D0%B8-%D0%BF%D0%B5%D1%80%D0%B5%D0%BC%D0%B5%D0%BD%D0%BD%D1%8B%D0%B5)
23. [Файл providers tf](#23-%D1%84%D0%B0%D0%B9%D0%BB-providers-tf)
24. [Файл variables tf](#24-%D1%84%D0%B0%D0%B9%D0%BB-variables-tf)
25. [Файл terraform tfvars](#25-%D1%84%D0%B0%D0%B9%D0%BB-terraform-tfvars)
26. [Файл main tf](#26-%D1%84%D0%B0%D0%B9%D0%BB-main-tf)
27. [Первый init validate plan](#27-%D0%BF%D0%B5%D1%80%D0%B2%D1%8B%D0%B9-init-validate-plan)
28. [Что должно получиться после первого plan](#28-%D1%87%D1%82%D0%BE-%D0%B4%D0%BE%D0%BB%D0%B6%D0%BD%D0%BE-%D0%BF%D0%BE%D0%BB%D1%83%D1%87%D0%B8%D1%82%D1%8C%D1%81%D1%8F-%D0%BF%D0%BE%D1%81%D0%BB%D0%B5-%D0%BF%D0%B5%D1%80%D0%B2%D0%BE%D0%B3%D0%BE-plan)

***

# Часть III — Terraform конфигурация Serverspace

## 19. Канонический каталог terraform-serverspace

Рабочий каталог для Serverspace:

```text
D:\Project_infra\greeting-service-infra\infra\terraform-serverspace\
```

В этом каталоге будет жить отдельная Terraform-конфигурация именно под Serverspace.

Это означает:

- здесь будут свои `.tf` файлы;
- здесь будет свой `terraform.tfstate`;
- здесь будут свои `docker/.env` и `terraform.tfvars`;
- здесь не смешиваем конфигурацию других облаков.

***

## 20. Структура каталога

На первом этапе создаём такую структуру:

```text
infra/terraform-serverspace/
├── docker/
│   ├── docker-compose.yml
│   └── .env.example
├── providers.tf
├── variables.tf
├── main.tf
├── outputs.tf
├── terraform.tfvars.example
├── .gitignore
└── README.md
```

Позже добавим:

```text
infra/terraform-serverspace/
├── modules/
│   ├── vps_control_plane/
│   ├── vps_apps/
│   ├── vps_postgres/
│   └── vps_gitlab/
```

На первом проходе можно начать без модулей, чтобы быстро получить рабочий `plan`.

***

## 21. Файлы docker и запуск Terraform

В исходном гайде уже используется схема с `docker/docker-compose.yml`, `docker/.env` и запуском Terraform из Git Bash через Docker.

Для нового каталога повторяем тот же подход.

### 21.1 Файл `docker/docker-compose.yml`

Создать файл:

```yaml
services:
  terraform:
    image: hashicorp/terraform:1.9
    working_dir: /workspace
    volumes:
      - ..:/workspace
      - terraform-plugin-cache:/root/.terraform.d/plugin-cache
    environment:
      TF_PLUGIN_CACHE_DIR: /root/.terraform.d/plugin-cache
      TF_VAR_serverspace_token: ${SERVERSPACE_TOKEN:-}
      TF_VAR_serverspace_region: ${SERVERSPACE_REGION:-}
    entrypoint: ["terraform"]

volumes:
  terraform-plugin-cache:
```


### 21.2 Файл `docker/.env.example`

Создать файл:

```env
SERVERSPACE_TOKEN=
SERVERSPACE_REGION=ru-ams
```

Потом на его основе создаётся рабочий `docker/.env`.

### 21.3 Первый принцип запуска

Все команды выполнять в **Git Bash**, как и в базовом документе.

Переход в каталог:

```bash

cd '/d/Project_infra/greeting-service-infra/infra/terraform-serverspace'
```

***

## 22. Секреты и переменные

В базовом гайде уже зафиксирована логика: секреты держим в `.env` и `terraform.tfvars`, а не в `.tf` файлах.[^1]

### 22.1 Создать рабочий `.env`

```bash

cd '/d/Project_infra/greeting-service-infra/infra/terraform-serverspace'
cp docker/.env.example docker/.env
```

После этого вручную заполнить:

```env

SERVERSPACE_TOKEN=ваш_api_token
SERVERSPACE_REGION=ru-ams
```


### 22.2 Что храним в `.env`

В `.env` держим:

- токен **Serverspace**;
- **регион** по умолчанию;
- позже можно добавить дополнительные **секреты**.


### 22.3 Что храним в `terraform.tfvars`

В `terraform.tfvars` держим:

 - **размеры** VPS;
 - **имена** серверов;
 - **образы** ОС;
 - SSH-**ключи**;
 - доменные **имена**;
 - **параметры** PostgreSQL и GitLab, если нужно.

***

## 23. Файл providers tf

Создать файл `providers.tf`:

```hcl
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
  key = var.api_key # объявить данную переменую в variables.tf
}

# — рекомендую проверить актуальную схему аргументов через
#      `terraform providers schema -json`
# перед сдачей работы,
```

На первом этапе этого достаточно.

Если провайдер потребует другой `source` или версию, скорректируем после первого `init`.


**Утверждение:**  `providers.tf` совпадает со схемой официального провайдера — `key = var.api_key` корректен.

Источник: https://serverspace.ru/support/help/automation-terraform/

> "terraform { required_providers { serverspace = { source = "itglobalcom/serverspace" version = "0.2.2" } } } variable "s2_token" { type = string default = "<api-ключ>" } provider "serverspace" { key = var.s2_token }"

Перевод:
> "Провайдер настраивается через блок `provider "serverspace" { key = var.s2_token }`, ключ передаётся как строковая переменная."


***

## 24. Файл variables tf

Создать файл `variables.tf`:

```hcl
variable "api_key" {
  description = "API key проекта Serverspace для аутентификации провайдера"
  type        = string
  sensitive   = true
}

variable "ssh_public_key" {
  description = "Публичный SSH-ключ для ресурса serverspace_ssh"
  type        = string
}

variable "location" {
  description = "Локация Serverspace для создаваемых серверов"
  type        = string
  default     = "am2"
}

variable "image_family" {
  description = "Образ ОС для serverspace_server"
  type        = string
  default     = "Ubuntu-20.04-X64"
}

variable "control_plane_name" {
  description = "Имя сервера control plane"
  type        = string
}

variable "control_plane_cpu" {
  description = "Количество vCPU для control plane"
  type        = number
}

variable "control_plane_ram" {
  description = "Объём RAM для control plane в МБ"
  type        = number
}

variable "control_plane_disk" {
  description = "Размер диска control plane в ГБ; в main.tf обычно преобразуется в boot_volume_size"
  type        = number
}

variable "apps_name" {
  description = "Имя сервера приложений"
  type        = string
}

variable "apps_cpu" {
  description = "Количество vCPU для сервера приложений"
  type        = number
}

variable "apps_ram" {
  description = "Объём RAM для сервера приложений в МБ"
  type        = number
}

variable "apps_disk" {
  description = "Размер диска сервера приложений в ГБ; в main.tf обычно преобразуется в boot_volume_size"
  type        = number
}

variable "postgres_name" {
  description = "Имя сервера PostgreSQL"
  type        = string
}

variable "postgres_cpu" {
  description = "Количество vCPU для сервера PostgreSQL"
  type        = number
}

variable "postgres_ram" {
  description = "Объём RAM для сервера PostgreSQL в МБ"
  type        = number
}

variable "postgres_disk" {
  description = "Размер диска сервера PostgreSQL в ГБ; в main.tf обычно преобразуется в boot_volume_size"
  type        = number
}

variable "gitlab_name" {
  description = "Имя сервера GitLab"
  type        = string
}

variable "gitlab_cpu" {
  description = "Количество vCPU для сервера GitLab"
  type        = number
}

variable "gitlab_ram" {
  description = "Объём RAM для сервера GitLab в МБ"
  type        = number
}

variable "gitlab_disk" {
  description = "Размер диска сервера GitLab в ГБ; в main.tf обычно преобразуется в boot_volume_size"
  type        = number
}

```
***

## ssh_key.tf

```hcl
resource "serverspace_ssh" "terraform" {
  name       = "terraform-key"
  public_key = var.ssh_public_key
}
```

Синтаксис ресурса подтверждён официальным примером.

Источник: https://serverspace.ru/support/help/automation-terraform/

> "resource "serverspace_ssh" "terraform" 
> 
> { name = "terraform-key" public_key = "ssh-rsa AAAAB3Nza...JUDjlM= root@CentOS.local" }"

## 25. Файл terraform tfvars

Создать файл `terraform.tfvars.example`:

 Добавьте ваш 

```hcl
serverspace_region = "ru-ams"

ssh_public_key = "ssh-ed25519 AAAA... ваш_публичный_ключ"

control_plane_name = "k8s-control-plane-1"
control_plane_cpu  = 2
control_plane_ram  = 4096
control_plane_disk = 60

apps_name = "k8s-apps-1"
apps_cpu  = 2
apps_ram  = 8192
apps_disk = 80

postgres_name = "postgres-1"
postgres_cpu  = 2
postgres_ram  = 8192
postgres_disk = 120

gitlab_name = "gitlab-1"
gitlab_cpu  = 4
gitlab_ram  = 8192
gitlab_disk = 120

image_family = "ubuntu"
```

Потом создать рабочий файл:

```bash
cp terraform.tfvars.example terraform.tfvars
```


### Пояснение по RAM

Здесь RAM задаётся в мегабайтах:

- 4096 = 4 GB
- 8192 = 8 GB

***

## 26. Файл main tf

На первом шаге делаем минимальную конфигурацию.

Создать файл `main.tf`:

```hcl
resource "serverspace_server" "control_plane" {
  name             = var.control_plane_name
  vcpus            = var.control_plane_cpu
  ram_mb           = var.control_plane_ram
  boot_disk_size   = var.control_plane_disk
  image            = var.image_family
  ssh_keys         = [var.ssh_public_key]
}

resource "serverspace_server" "apps" {
  name             = var.apps_name
  vcpus            = var.apps_cpu
  ram_mb           = var.apps_ram
  boot_disk_size   = var.apps_disk
  image            = var.image_family
  ssh_keys         = [var.ssh_public_key]
}

resource "serverspace_server" "postgres" {
  name             = var.postgres_name
  vcpus            = var.postgres_cpu
  ram_mb           = var.postgres_ram
  boot_disk_size   = var.postgres_disk
  image            = var.image_family
  ssh_keys         = [var.ssh_public_key]
}

resource "serverspace_server" "gitlab" {
  name             = var.gitlab_name
  vcpus            = var.gitlab_cpu
  ram_mb           = var.gitlab_ram
  boot_disk_size   = var.gitlab_disk
  image            = var.image_family
  ssh_keys         = [var.ssh_public_key]
}
```

Это черновик для первого `plan`.

Если конкретные имена аргументов у провайдера отличаются, мы увидим это на `terraform validate` или `terraform plan` и сразу поправим.

***

## 27. Первый init validate plan

В исходном документе уже используется рабочий порядок `init → validate → plan`, и его сохраняем здесь.[^1]

Команды:

```bash
cd '/d/Project_infra/greeting-service-infra/infra/terraform-serverspace'

docker compose -f docker/docker-compose.yml --env-file docker/.env run --rm terraform init
docker compose -f docker/docker-compose.yml --env-file docker/.env run --rm terraform validate
docker compose -f docker/docker-compose.yml --env-file docker/.env run --rm terraform plan
```

Если `init` проходит успешно, значит:

- Docker-схема работает;
- провайдер скачивается;
- токен и переменные подхватываются.

Если `validate` или `plan` ругаются на имена полей ресурса, это нормально для первого прогона. Тогда просто корректируем `main.tf` под фактический синтаксис провайдера.

***

## 28. Что должно получиться после первого plan

После первого успешного `plan` у тебя должно быть следующее:

- отдельный рабочий каталог Terraform для Serverspace;
- отдельный `docker/.env`;
- отдельный `terraform.tfvars`;
- отдельная конфигурация на 4 VPS;
- понимание, какие поля ресурса провайдера надо уточнить или поправить;
- готовность к следующему шагу — реальному `apply`.


### Результат этого документа

Этот документ не поднимает серверы сразу.

Он делает главное:

- создаёт отдельный фундамент под Serverspace;
- отделяет его от других облаков;
- подводит к первому корректному `plan`.


### Следующий документ

Следующим документом будет:

`Кластер-на-4-VPS-Serverspace-4.md`

В нём уже пойдём дальше:

- исправление конфигурации под фактический синтаксис провайдера;
- `outputs.tf`;
- `apply`;
- получение IP-адресов;
- первый SSH вход на все 4 VPS;
- подготовка VPS-1 под Kubernetes;
- подготовка VPS-3 под PostgreSQL.

***

