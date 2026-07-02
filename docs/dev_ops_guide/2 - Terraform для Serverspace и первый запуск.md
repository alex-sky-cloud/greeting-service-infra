

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

Нужен именно готовый конфигурационный файл `terraform.tfvars.example`

**Источник:** https://developer.hashicorp.com/terraform/language/values/variables

> "You can add variable blocks to your configuration to define input interface for your module. This lets users pass custom values to your module at runtime."

Перевод:
> "Вы можете добавлять блоки переменных в конфигурацию, чтобы определить входной интерфейс модуля. Это позволяет передавать собственные значения в модуль во время выполнения."

**Утверждение:**
В `.tfvars` должны быть именно присваивания значений уже объявленным переменным, а не новые объявления переменных.

**Источник:** https://developer.hashicorp.com/terraform/language/values/variables

> "You can assign values directly to variable names in files with a `.tfvars` or `.auto.tfvars` extension."

Перевод:
> "Вы можете напрямую присваивать значения именам переменных в файлах с расширением `.tfvars` или `.auto.tfvars`."

**Утверждение:**
Файл `terraform.tfvars` Terraform загружает автоматически, если он лежит в текущем каталоге и называется именно так.

**Источник:** https://developer.hashicorp.com/terraform/tutorials/configuration-language/variables

> "Terraform automatically loads all files in the current directory with the exact name terraform.tfvars or matching *.auto.tfvars."

Перевод:
> "Terraform автоматически загружает все файлы в текущем каталоге с точным именем `terraform.tfvars` или подходящие под шаблон `*.auto.tfvars`."

**Утверждение:**
Так как в ваших текущих файлах используются `api_key` и `location`, а не `serverspace_region`, конфигурационный пример нужно привести именно к этим именам.

**Источник:** https://developer.hashicorp.com/terraform/language/values/variables

> "To reference a `variable` in other parts of your configuration, use `var.<NAME>` syntax."

Перевод:
> "Чтобы ссылаться на переменную в других частях конфигурации, используйте синтаксис `var.<ИМЯ>`."

Готовый файл:

```hcl
api_key = "your_serverspace_api_key"

ssh_public_key = "ssh-ed25519 AAAA... ваш_публичный_ключ"

location     = "am2"
image_family = "Ubuntu-20.04-X64"

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
```

**Утверждение:**
Если нужен шаблонный файл, его правильно назвать `terraform.tfvars.example`, а рабочую копию затем создать отдельно.

**Источник:** https://developer.hashicorp.com/terraform/tutorials/configuration-language/variables

> "Terraform automatically loads all files in the current directory with the exact name terraform.tfvars or matching *.auto.tfvars."

Перевод:
> "Terraform автоматически загружает все файлы в текущем каталоге с точным именем `terraform.tfvars` или подходящие под шаблон `*.auto.tfvars`."

Рабочая команда:

```bash

cp terraform.tfvars.example terraform.tfvars
```

**Утверждение:**
RAM в вашем примере действительно задаётся в мегабайтах, поэтому `4096 = 4 GB`, а `8192 = 8 GB`.

**Источник:** https://serverspace.ru/support/help/automation-terraform/

В официальном примере **Serverspace** память сервера задаётся числовыми значениями вроде `2048` и `8192`, что соответствует передаче RAM в мегабайтах.

### Пояснение по RAM

Здесь RAM задаётся в мегабайтах:

- 4096 = 4 GB
- 8192 = 8 GB

***

## 26. Файл main tf

На первом шаге делаем минимальную конфигурацию.

Создать файл `main.tf`:

```hcl
resource "serverspace_isolated_network" "reactive_net" {
  location       = var.location
  name           = "reactive_net"
  description    = "Example for Terraform"
  network_prefix = "192.168.0.0"
  mask           = 24
}

resource "serverspace_server" "control_plane" {
  image            = var.image_family
  name             = var.control_plane_name
  location         = var.location
  cpu              = var.control_plane_cpu
  ram              = var.control_plane_ram
  boot_volume_size = var.control_plane_disk * 1024

  nic {
    network      = ""
    network_type = "PublicShared"
    bandwidth    = 50
  }

  nic {
    network      = serverspace_isolated_network.reactive_net.id
    network_type = "Isolated"
    bandwidth    = 0
  }

  ssh_keys = [
    serverspace_ssh.terraform.id,
  ]
}

resource "serverspace_server" "apps" {
  image            = var.image_family
  name             = var.apps_name
  location         = var.location
  cpu              = var.apps_cpu
  ram              = var.apps_ram
  boot_volume_size = var.apps_disk * 1024

  nic {
    network      = ""
    network_type = "PublicShared"
    bandwidth    = 50
  }

  nic {
    network      = serverspace_isolated_network.reactive_net.id
    network_type = "Isolated"
    bandwidth    = 0
  }

  ssh_keys = [
    serverspace_ssh.terraform.id,
  ]
}

resource "serverspace_server" "postgres" {
  image            = var.image_family
  name             = var.postgres_name
  location         = var.location
  cpu              = var.postgres_cpu
  ram              = var.postgres_ram
  boot_volume_size = var.postgres_disk * 1024

  nic {
    network      = ""
    network_type = "PublicShared"
    bandwidth    = 70
  }

  nic {
    network      = serverspace_isolated_network.reactive_net.id
    network_type = "Isolated"
    bandwidth    = 0
  }
}

resource "serverspace_server" "gitlab" {
  image            = var.image_family
  name             = var.gitlab_name
  location         = var.location
  cpu              = var.gitlab_cpu
  ram              = var.gitlab_ram
  boot_volume_size = var.gitlab_disk * 1024

  nic {
    network      = ""
    network_type = "PublicShared"
    bandwidth    = 50
  }

  nic {
    network      = serverspace_isolated_network.reactive_net.id
    network_type = "Isolated"
    bandwidth    = 0
  }
}

```

Это черновик для первого `plan`.

Если конкретные имена аргументов у провайдера отличаются, мы увидим это на `terraform validate` или `terraform plan` и сразу поправим.

***

## 27. Первый init validate plan

В исходном документе уже используется рабочий порядок `init → validate → plan`, и его сохраняем здесь.

 - Запускаем Docker Desktop

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

```bash

$ docker compose -f docker/docker-compose.yml --env-file docker/.env run --rm terraform init
 Container docker-terraform-run-c5ad9a04146e Creating
 Container docker-terraform-run-c5ad9a04146e Created
Initializing the backend...
Initializing provider plugins...
- Finding itglobalcom/serverspace versions matching "~> 0.3.2"...
╷
│ Error: Invalid provider registry host
│
│ The host "registry.terraform.io" given in provider source address
│ "registry.terraform.io/itglobalcom/serverspace" does not offer a Terraform
│ provider registry.
```

- или же

```bash

docker compose -f docker/docker-compose.yml --env-file docker/.env run --rm terraform providers schema -json
 Container docker-terraform-run-a2e88b55e322 Creating
 Container docker-terraform-run-a2e88b55e322 Created
╷
│ Error: Inconsistent dependency lock file
│
│ The following dependency selections recorded in the lock file are
│ inconsistent with the current configuration:
│   - provider registry.terraform.io/itglobalcom/serverspace: required by this configuration but no version is selected
│
│ To make the initial dependency selections that will initialize the
│ dependency lock file, run:
│   terraform init
╵

```

**Причина найдена**:  нужно включить VPN или создать свое "зеркало."


## Что сделать дальше

1. Удалить старый lock-файл (если он есть), так как он уже "запомнил" неправильную версию:

```bash

rm .terraform.lock.hcl
rm -rf .terraform
```


### Создать локальное зеркало

Вот исправленный `docker-compose.yml` с сохранением твоего оригинального формата (комментарии на той же строке, тот же порядок ключей) — добавлена только строка монтирования `.terraformrc` для обхода блокировки реестра.

```yaml
services:
  terraform:
    image: hashicorp/terraform:1.9 # Образ с Terraform — Docker скачает сам при первом запуске
    working_dir: /workspace # Рабочий каталог внутри контейнера
    volumes:
      - ..:/workspace # Монтирует каталог terraform-serverspace с твоего диска в контейнер
      - terraform-plugin-cache:/root/.terraform.d/plugin-cache # Кэш провайдеров — чтобы не скачивать при каждом запуске
      - ./terraformrc:/root/.terraformrc # Конфиг CLI — зеркало реестра вместо заблокированного registry.terraform.io
    environment:
      TF_PLUGIN_CACHE_DIR: /root/.terraform.d/plugin-cache
      TF_VAR_api_key: ${SERVERSPACE_TOKEN:-} # Токен API Serverspace — читается из .env файла
      TF_VAR_location: ${SERVERSPACE_REGION:-} # Регион Serverspace — читается из .env файла
    entrypoint: ["terraform"] # При запуске контейнера сразу выполняет terraform

volumes:
  terraform-plugin-cache:
```


## Дополнительный файл — `docker/terraformrc`

Создай рядом с `.env` файл `terraformrc` (без точки в начале, монтируется как `.terraformrc` внутри контейнера):

```hcl
provider_installation {
  network_mirror {
    url     = "https://terraform-mirror.yandexcloud.net/"
    include = ["registry.terraform.io/*/*"]
  }
  direct {
    exclude = ["registry.terraform.io/*/*"]
  }
}
```

Источник: https://dev.to/kvendingoldo/kak-nastroit-zierkalo-rieiestrov-dlia-opentofu-i-terraform-277k

> "provider_installation { network_mirror { url = "https://terraform-mirror.yandexcloud.net/" include = ["registry.terraform.io/*/*"] } direct { exclude = ["registry.terraform.io/*/*"] } }"

## Запуск после изменений

```bash

docker compose -f docker/docker-compose.yml down -v

rm -rf .terraform .terraform.lock.hcl

docker compose -f docker/docker-compose.yml --env-file docker/.env run --rm terraform init
```

Получаем:

```bash

 Warning: Incomplete lock file information for providers
│
│ Due to your customized provider installation methods, Terraform was forced
│ to calculate lock file checksums locally for the following providers:
│   - itglobalcom/serverspace
│
│ The current .terraform.lock.hcl file only includes checksums for
│ linux_amd64, so Terraform running on another platform will fail to install
│ these providers.
│
│ To calculate additional checksums for another platform, run:
│   terraform providers lock -platform=linux_amd64
│ (where linux_amd64 is the platform to generate)
╵
Terraform has been successfully initialized!

You may now begin working with Terraform. Try running "terraform plan" to see
any changes that are required for your infrastructure. All Terraform commands
should now work.

If you ever set or change modules or backend configuration for Terraform,
rerun this command to reinitialize your working directory. If you forget, other
commands will detect it and remind you to do so if necessary.


```
Это просто предупреждение (Warning), а не ошибка — `init` уже прошёл успешно, о чём говорит последняя строка "Terraform has been successfully initialized!".

**Что означает предупреждение:**

Поскольку ты используешь кастомное зеркало (`network_mirror` вместо прямого реестра), Terraform не смог скачать заранее готовые контрольные суммы (checksums) провайдера для всех платформ — он посчитал их локально только для одной платформы, на которой ты сейчас работаешь: `linux_amd64` (это платформа внутри Docker-контейнера).

Источник: https://developer.hashicorp.com/terraform/cli/config/config-file

> "The CLI configuration file configures per-user settings for CLI behaviors, which apply across all Terraform working directories."

Это значит: файл `.terraform.lock.hcl` теперь "знает" контрольные суммы провайдера только для Linux внутри контейнера. Если бы кто-то другой запускал этот же проект на Windows или Mac напрямую (без Docker), `init` бы не сработал — понадобилось досчитать чексуммы под ту платформу.

**В твоём случае это не проблема**, потому что:

- Terraform у тебя всегда запускается только в Docker-контейнере с `hashicorp/terraform:1.9` на базе Linux.
- Ты никогда не запускаешь этот `main.tf` напрямую на Windows.

Можно спокойно двигаться дальше:

```bash

cd '/d/Project_infra/greeting-service-infra/infra/terraform-serverspace'
docker compose -f docker/docker-compose.yml --env-file docker/.env run --rm terraform validate
```

```bash

cd '/d/Project_infra/greeting-service-infra/infra/terraform-serverspace'
docker compose -f docker/docker-compose.yml --env-file docker/.env run --rm terraform validate

 Container docker-terraform-run-a82abe90d900 Creating
 Container docker-terraform-run-a82abe90d900 Created
Success! The configuration is valid.
```

Если `validate` пройдёт без ошибок — переходи к `plan`.

***

```bash
cd '/d/Project_infra/greeting-service-infra/infra/terraform-serverspace'
docker compose -f docker/docker-compose.yml --env-file docker/.env run --rm terraform plan -out=tfplan

.....

Plan: 6 to add, 0 to change, 0 to destroy.
─────────────────────────────────────────────────────────────────────────────
Saved the plan to: tfplan

To perform exactly these actions, run the following command to apply:
    terraform apply "tfplan"

```

- Теперь выполняем план

```bash

docker compose -f docker/docker-compose.yml --env-file docker/.env run --rm terraform apply tfplan
```
 - если будут ошибки при выполнении:
  - может быть недоступна выбранная локация (тогда посмотри документ 1, там есть способ посмотреть доступные локации серверов)
  - может просто нет денег на сервере

```bash
.........
│   "status_code": 400,
│   "status": "400 Bad Request",
│   "response": {
│     "errors": [
│       {
│         "code": -19003,
│         "message": "Insufficient funds. Minimum balance: 2683.05"
│       }
│     ]
│   }
│ }

```

- Может быть ошибка на стороне провайдера

```bash


serverspace_server.control_plane: Creation complete after 51s [id=l44s1304964]
╷
│ Error: task 'lt6336740' failed
│
│   with serverspace_server.gitlab,
│   on main.tf line 80, in resource "serverspace_server" "gitlab":
│   80: resource "serverspace_server" "gitlab" {
│
╵


```

- На текущий момент они не знают что делать. Нужно писать в поддержку чтобы перезапустили задачу.

## 28. Что должно получиться после первого plan

После первого успешного `plan` у тебя должно быть следующее:

- отдельный рабочий каталог Terraform для Serverspace;
- отдельный `docker/.env`;
- отдельный `terraform.tfvars`;
- отдельная конфигурация на 4 VPS;
- понимание, какие поля ресурса провайдера надо уточнить или поправить;
- готовность к следующему шагу — реальному `apply`.


### Результат этого документа

 - должны получить развернутую инфрастуктуру

### Следующий документ

Следующим документом будет:


В нём уже пойдём дальше:

- исправление конфигурации под фактический синтаксис провайдера;
- `outputs.tf`;
- `apply`;
- получение IP-адресов;
- первый SSH вход на все 4 VPS;
- подготовка VPS-1 под Kubernetes;
- подготовка VPS-3 под PostgreSQL.

***

