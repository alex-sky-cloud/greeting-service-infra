## Общая логика `variables.tf`

```hcl
variable "name" {
  description = "..."
  type        = string | number | bool | ...
  default     = "..." # необязательно
  sensitive   = true   # необязательно
}
```

Источник: https://developer.hashicorp.com/terraform/language/block/variable

> "Use the `variable` block to parameterize your configuration so that module consumers can pass custom values into the configuration at runtime."

Перевод:
> "Используйте блок `variable` для параметризации конфигурации, чтобы пользователи модуля могли передавать собственные значения во время выполнения."

Источник: https://developer.hashicorp.com/terraform/language/block/variable

> "The `description` argument in a `variable` block documents the purpose of a variable and the value the block expects."

Перевод:
> "Аргумент `description` в блоке `variable` документирует назначение переменной и то значение, которое она ожидает."

- `variable "..."` задаёт имя входной переменной Terraform; это имя затем используется в конфигурации как `var.<имя>`.
- `description` нужен не для работы Terraform, а для читаемости и документации.
- `type` ограничивает тип значения: `string` для текста, `number` для чисел.
- `default` делает переменную необязательной при запуске, если значение по умолчанию допустимо.
- `sensitive = true` скрывает значение в выводе Terraform и нужен для секретов.

## Переменная `api_key`

```hcl
variable "api_key" {
  description = "API key проекта Serverspace для аутентификации провайдера"
  type        = string
  sensitive   = true
}
```

Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs

> "Schema. Optional. key (String) API Key; you can obtain it from settings of your project in the control panel."

Перевод:
> "Схема. Необязательно. key (String) — API-ключ; вы можете получить его в настройках проекта в панели управления."

Источник: https://developer.hashicorp.com/terraform/language/block/variable

> "The label after the `variable` keyword is a name for the variable, which must be unique... The name of a variable can be any valid identifier."

Перевод:
> "Метка после ключевого слова `variable` — это имя переменной, которое должно быть уникальным... Имя переменной может быть любым корректным идентификатором."

- Название `api_key` выбрано потому, что в `providers.tf` провайдер получает значение через `key = var.api_key`; значит имя должно совпадать с тем, как оно вызывается в конфигурации.
- Используется именно `api_key`, а не `serverspace_token` или `serverspace_key`, потому что задача файла переменных — быть согласованным с уже существующим `providers.tf`.
- `description` поясняет, что это ключ проекта Serverspace и он нужен именно для аутентификации провайдера.
- `type = string` выбран потому, что API-ключ передаётся как строка.
- `sensitive = true` обязателен по смыслу, потому что API-ключ — секрет.
- `default` не задан специально: секреты не стоит зашивать в код по умолчанию.

## Переменная `ssh_public_key`

```hcl
variable "ssh_public_key" {
  description = "Публичный SSH-ключ для ресурса serverspace_ssh"
  type        = string
}
```

Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs/resources/ssh

> "Serverspace SSH key resource allows you to manage SSH keys for Server access. Keys created with this resource can be referenced in your Server configuration..."

Перевод:
> "Ресурс Serverspace SSH key позволяет управлять SSH-ключами для доступа к серверу. Ключи, созданные этим ресурсом, могут использоваться в конфигурации сервера..."

- Название `ssh_public_key` выбрано буквально по содержанию переменной: сюда передаётся именно публичный SSH-ключ.
- В имени есть `public`, чтобы не перепутать этот параметр с приватным ключом, который сюда передавать нельзя.
- Суффикс `_key` показывает, что значение — это сам ключ, а не имя ресурса, не id и не путь к файлу.
- `description` прямо связывает переменную с ресурсом `serverspace_ssh`, чтобы было понятно, где она применяется.
- `type = string` нужен потому, что публичный ключ передаётся одной строкой (`ssh-rsa ...` или `ssh-ed25519 ...`).
- `sensitive = true` не задан, потому что публичный ключ не является секретом.
- `default` не задан, потому что SSH-ключ обычно должен подставляться из конкретного окружения пользователя.

## Переменная `location`

```hcl
variable "location" {
  description = "Локация Serverspace для создаваемых серверов"
  type        = string
  default     = "am2"
}
```

Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs/resources/server

> "... location ..."

Перевод:
> "... location ..."

- Название `location` выбрано потому, что официальный ресурс `serverspace_server` использует именно аргумент `location`, а не `region`.
- Такое имя уменьшает путаницу между именем переменной Terraform и именем аргумента ресурса.
- `description` поясняет, что речь идёт именно о локации создаваемых серверов, а не о регионе провайдера как отдельной сущности.
- `type = string` выбран, потому что код локации передаётся строкой.
- `default = "am2"` задан для удобства, чтобы не вводить одно и то же значение вручную каждый раз; при этом его можно переопределить в `terraform.tfvars`.
- `sensitive` не нужен, потому что локация не является секретной информацией.

## Переменная `image_family`

```hcl
variable "image_family" {
  description = "Образ ОС для serverspace_server"
  type        = string
  default     = "Ubuntu-20.04-X64"
}
```

Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs/resources/server

> "... image ..."

Перевод:
> "... image ..."

- Название `image_family` выбрано как более человеческое и привычное для чтения имя переменной, описывающее семейство или тип образа ОС.
- При этом в самом ресурсе Serverspace используется аргумент `image`, поэтому в `main.tf` эта переменная должна подставляться как `image = var.image_family`.
- `description` объясняет, что переменная определяет образ ОС для ресурса `serverspace_server`.
- `type = string` нужен, потому что имя образа передаётся текстом.
- `default = "Ubuntu-20.04-X64"` добавлен как рабочее стартовое значение, чтобы конфигурацию было проще запускать без постоянного ручного ввода.
- `sensitive` не нужен, потому что имя образа не является секретом.

## Группа `control_plane_*`

```hcl
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
```

Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs/resources/server

> "cpu (Number) Count of the CPU Cores. ram (Number) Size of RAM in MB. boot_volume_size (Number) Size of the volume from which the server will be booted..."

Перевод:
> "cpu (Number) — количество ядер CPU. ram (Number) — объём RAM в МБ. boot_volume_size (Number) — размер тома, с которого загружается сервер..."

- Префикс `control_plane_` выбран, чтобы сразу отделить параметры управляющего узла от остальных серверов инфраструктуры.
- Суффикс `_name` означает имя ресурса сервера; используется `string`, потому что имя — это текст.
- Суффикс `_cpu` выбран по аналогии с официальным аргументом `cpu`; тип `number` нужен для числового значения количества vCPU.
- Суффикс `_ram` выбран по аналогии с официальным аргументом `ram`; тип `number` нужен, потому что размер памяти задаётся числом.
- Суффикс `_disk` описывает размер системного диска в удобном для человека виде; в `main.tf` он затем обычно переводится в официальный аргумент `boot_volume_size`.
- `default` не задан ни для одной из этих переменных, потому что параметры control plane обычно подбираются под конкретную инфраструктуру.
- `sensitive` не используется, потому что имя сервера, CPU, RAM и диск не являются секретами.

## Группа `apps_*`

```hcl
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
```

Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs/resources/server

> "cpu (Number) Count of the CPU Cores. ram (Number) Size of RAM in MB. boot_volume_size (Number) Size of the volume from which the server will be booted..."

Перевод:
> "cpu (Number) — количество ядер CPU. ram (Number) — объём RAM в МБ. boot_volume_size (Number) — размер загрузочного тома..."

- Префикс `apps_` выбран как короткое имя роли сервера приложений.
- Внутри группы сохранён тот же шаблон имён (`name`, `cpu`, `ram`, `disk`), что и у `control_plane_*`, чтобы вся конфигурация читалась единообразно.
- Такое повторение схемы названий удобно: по одному имени сразу понятно и назначение сервера, и тип параметра.
- `type` у свойств выбран по смыслу: `string` для имени, `number` для ресурсов.
- `default` и `sensitive` не нужны по тем же причинам, что и для control plane.

## Группа `postgres_*`

```hcl
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
```

Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs/resources/server

> "cpu (Number) Count of the CPU Cores. ram (Number) Size of RAM in MB. boot_volume_size (Number) Size of the volume from which the server will be booted..."

Перевод:
> "cpu (Number) — количество ядер CPU.
> 
> ram (Number) — объём RAM в МБ.
> 
> boot_volume_size (Number) — размер загрузочного тома..."

- Префикс `postgres_` выбран по имени роли сервера: это узел под PostgreSQL.
- Названия свойств внутри группы повторяют ту же модель, что и у остальных серверов, чтобы не приходилось запоминать отдельную схему имён для каждой роли.
- `postgres_disk` назван так для краткости, но фактически смысл переменной — размер загрузочного диска сервера базы данных.
- `type = number` для CPU, RAM и диска выбран логично, потому что все эти параметры передаются как числовые значения.

## Группа `gitlab_*`

```hcl
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

Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs/resources/server

> "cpu (Number) 
> 
> ram (Number) Size of RAM in MB.
> 
> boot_volume_size (Number) Size of the volume from which the server will be booted..."

Перевод:
> "cpu (Number) — количество ядер CPU.
> 
> ram (Number) — объём RAM в МБ.
> 
> boot_volume_size (Number) — размер загрузочного тома..."

 - Префикс `gitlab_` выбран по сервису, который будет размещён на сервере.
 - Повторяющаяся схема `name/cpu/ram/disk` сохранена специально, чтобы по всем ролям инфраструктуры использовалась одна логика именования.
 - Это делает `variables.tf` предсказуемым: если существует `apps_cpu`, то естественно ожидать и `gitlab_cpu`, а не какое-то другое имя.
 - `description` у каждой переменной объясняет назначение свойства человеческим языком.
 - `type` и отсутствие `sensitive/default` здесь обоснованы так же, как и в остальных серверных группах.

## Почему свойства названы именно так

Источник: https://developer.hashicorp.com/terraform/language/values/variables

> "You can add variable blocks to your configuration to define input interface for your module. This lets users pass custom values to your module at runtime."

Перевод:
> "Вы можете добавлять блоки variable в конфигурацию, чтобы определить входной интерфейс модуля. Это позволяет пользователям передавать собственные значения во время выполнения."

- Имена переменных выбраны так, чтобы они одновременно отражали роль сервера (`control_plane`, `apps`, `postgres`, `gitlab`) и тип параметра (`name`, `cpu`, `ram`, `disk`).
- Такой стиль именования делает входной интерфейс Terraform-модуля предсказуемым и читаемым без заглядывания в `main.tf`.
- Там, где у провайдера есть официальные имена аргументов (`key`, `location`, `cpu`, `ram`, `boot_volume_size`), названия переменных либо совпадают с ними, либо осознанно остаются более удобными для чтения, но потом должны явно маппиться в `main.tf`.
