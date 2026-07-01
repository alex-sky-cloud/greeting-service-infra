# Комментарии к `main.tf`

## Для чего нужен файл `main.tf`

Файл `main.tf` — это основной Terraform-файл, в котором описывается сама инфраструктура: какие ресурсы нужно создать, как они связаны между собой и с какими параметрами они должны быть развернуты. 

 - В инструкции **Serverspace** прямо сказано, что `main.tf` должен содержать описание инфраструктуры. Источник: https://serverspace.ru/support/help/automation-terraform/

 - В **Terraform**, ресурс описывается через блок `resource`, а конкретный набор аргументов зависит от провайдера. Источник: https://developer.hashicorp.com/terraform/language/block/resource

> "The `resource` block defines a piece of infrastructure and specifies the settings for Terraform to create it with."

Перевод:
> "Блок `resource` определяет элемент инфраструктуры и задаёт настройки, с помощью которых Terraform его создаёт."

## Текущий `main.tf`

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

## Ресурс `serverspace_isolated_network.reactive_net`

Этот блок создаёт изолированную сеть, к которой потом подключаются серверы через второй `nic`.
 - В официальной экосистеме провайдера **Serverspace** для этого используется ресурс `serverspace_isolated_network`. Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs/resources/isolated_network

***
Это значит **“к этой приватной сети серверы подключаются через второй сетевой интерфейс”**. Источник: https://serverspace.io/support/help/automation-terraform/

В инструкции _Serverspace_ показан пример инфраструктуры с серверами, "**connected via isolated network**", то есть соединёнными через изолированную сеть. Источник: https://serverspace.io/support/help/automation-terraform/

`NIC` — это **network interface card**, по-простому: 
 - сетевой интерфейс или **сетевой адаптер** сервера. Через него сервер подключается к сети. Источник: https://www.codecademy.com/resources/blog/network-interface-card


Проще можно написать так:

- **первый `nic`** подключает сервер к публичной сети;
- **второй `nic`** подключает тот же сервер к изолированной приватной сети.

Источник: https://serverspace.io/support/help/automation-terraform/

Если совсем простыми словами, то у сервера как будто **две сетевые “карты”**:

- одна — для внешней сети;
- вторая — для внутренней сети между серверами.
- 
***

### `resource "serverspace_isolated_network" "reactive_net"`

 - `serverspace_isolated_network` — тип ресурса провайдера **Serverspace** для приватной сети.
 - `reactive_net` — локальное имя ресурса внутри Terraform-конфигурации.
 - Такое имя нужно, чтобы потом ссылаться на сеть как `serverspace_isolated_network.reactive_net.id`. Источник: https://developer.hashicorp.com/terraform/language/block/resource

> "To reference the resource in your configuration, you must refer to it using `<TYPE>.<LABEL>` syntax."

Перевод:
> "Чтобы ссылаться на ресурс в конфигурации, нужно использовать синтаксис `<ТИП>.<МЕТКА>`."

### Свойства сети

- `location = var.location` — задаёт локацию сети; используется та же переменная, что и для серверов, чтобы все ресурсы создавались в одной локации.
- `name = "reactive_net"` — имя сети в **Serverspace**.
- `description = "Example for Terraform"` — текстовое описание сети.
- `network_prefix = "192.168.0.0"` — базовый адрес подсети.
- `mask = 24` — маска подсети.

## Ресурс `serverspace_server.control_plane`

Этот блок описывает сервер `control_plane`. 
 - Он создаётся через официальный ресурс `serverspace_server`, который в документации **Serverspace** используется для _создания, изменения и удаления_ серверов. 
    - Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs/resources/server

### Заголовок ресурса

- `serverspace_server` — тип ресурса сервера.
- `control_plane` — **локальное имя** сервера в Terraform.
- Такое имя выбрано по роли сервера: это **управляющий узел инфраструктуры**(будем использовать **Kubernetes**).

### Основные свойства сервера

 - `image = var.image_family` — образ **ОС**. Имя переменной `image_family` пользовательское, но в сам ресурс оно **сопоставляется** (mapping) в официальный аргумент `image`.
 - `name = var.control_plane_name` — имя сервера в панели **Serverspace**.
 - `location = var.location` — локация сервера.
 - `cpu = var.control_plane_cpu` — число **vCPU**.
 - `ram = var.control_plane_ram` — объём оперативной памяти.
 - `boot_volume_size = var.control_plane_disk * 1024` — размер загрузочного диска; 
   - умножение на `1024` означает перевод из ГБ в МБ, как в официальном примере **Serverspace** с `40*1024`. Источник: https://serverspace.ru/support/help/automation-terraform/

### Первый блок `nic`

- `network = ""` — пустое значение сети для публичного интерфейса, как в примере Serverspace.
- `network_type = "PublicShared"` — тип публичной общей сети.
- `bandwidth = 50` — пропускная способность интерфейса.

В официальном примере **Serverspace** блок `nic` действительно содержит `network`, `network_type` и `bandwidth`. Источник: https://serverspace.ru/support/help/automation-terraform/

### Второй блок `nic`

- `network = serverspace_isolated_network.reactive_net.id` — ссылка на id созданной изолированной сети.
- `network_type = "Isolated"` — тип приватной сети.
- `bandwidth = 0` — для изолированной сети в примере используется нулевое значение.

Ссылка записана правильно в синтаксисе **Terraform**: `<тип ресурса>.<локальное имя>.<атрибут>`. Источник: https://developer.hashicorp.com/terraform/language/block/resource

### `ssh_keys`

- `ssh_keys = [ serverspace_ssh.terraform.id ]` — передаёт серверу id SSH-ключа, созданного в `ssh_key.tf`.
- `serverspace_ssh` — тип ресурса SSH-ключа.
- `terraform` — локальное имя этого SSH-ключа.
- `id` — атрибут созданного ключа.

В ресурсе `serverspace_server` параметр `ssh_keys` описан как **список ID** SSH-ключей. Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs/resources/server

## Ресурсы `apps`, `postgres`, `gitlab`

Эти блоки построены по той же схеме, что и `control_plane`: тот же тип ресурса, та же локация, тот же принцип подключения публичной и изолированной сети, но другие переменные под конкретную роль сервера. Это сделано для единообразия конфигурации.

### `apps`

- `name = var.apps_name` — имя сервера приложений.
- `cpu = var.apps_cpu` — CPU для сервера приложений.
- `ram = var.apps_ram` — RAM для сервера приложений.
- `boot_volume_size = var.apps_disk * 1024` — диск сервера приложений.
- `ssh_keys = [ serverspace_ssh.terraform.id ]` — привязка SSH-ключа.

### `postgres`

- `name = var.postgres_name` — имя сервера PostgreSQL.
- `cpu = var.postgres_cpu` — CPU для PostgreSQL.
- `ram = var.postgres_ram` — RAM для PostgreSQL.
- `boot_volume_size = var.postgres_disk * 1024` — диск PostgreSQL.
- `bandwidth = 70` в публичном `nic` — отдельно заданная пропускная способность для этого сервера.

### `gitlab`

- `name = var.gitlab_name` — имя сервера GitLab.
- `cpu = var.gitlab_cpu` — CPU для GitLab.
- `ram = var.gitlab_ram` — RAM для GitLab.
- `boot_volume_size = var.gitlab_disk * 1024` — диск GitLab.

## Что важно помнить

- `main.tf` описывает именно ресурсы инфраструктуры, а не переменные; 
  - переменные объявляются отдельно в `variables.tf`. Источник: https://serverspace.ru/support/help/automation-terraform/
- Все ссылки между ресурсами должны писаться без префикса `resource.`, в форме `serverspace_ssh.terraform.id` или `serverspace_isolated_network.reactive_net.id`. Источник: https://developer.hashicorp.com/terraform/language/block/resource
- Если в `main.tf` используется `serverspace_ssh.terraform.id`, то ресурс `serverspace_ssh "terraform"` должен существовать в `ssh_key.tf`.
- Если в `main.tf` используется `var.location`, `var.image_family`, `var.control_plane_cpu` и другие `var.*`, то эти переменные должны быть объявлены в `variables.tf`.
