
## Комментарии к `terraform.tfvars.example`

### Для чего нужен файл

Файл `terraform.tfvars.example` — шаблон значений переменных. 
 - Из него делают рабочую копию `terraform.tfvars`, куда вносят реальные значения.

Источник: https://developer.hashicorp.com/terraform/tutorials/configuration-language/variables

> "Terraform automatically loads all files in the current directory with the exact name terraform.tfvars or matching *.auto.tfvars."

Перевод:
> "Terraform автоматически загружает все файлы в текущем каталоге с точным именем terraform.tfvars или подходящие под шаблон *.auto.tfvars."

### Актуальное содержимое файла

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


### `api_key`

- `api_key` — значение токена API Serverspace для аутентификации провайдера.
- Попадает в переменную `api_key` из `variables.tf`, оттуда — в `providers.tf` как `key = var.api_key`.
- В файле стоит заглушка `"your_serverspace_api_key"` — не рабочий токен; реальный токен берётся в панели управления Serverspace.


### `ssh_public_key`

- Значение публичного SSH-ключа, используется в `ssh_key.tf` как `public_key = var.ssh_public_key`.
- В файле указан пример `"ssh-ed25519 AAAA... ваш_публичный_ключ"` — сокращённая заглушка, не рабочий ключ.
- Настоящий ключ получают командами:

```bash

ssh-keygen -t ed25519 -C "your_email@example.com"
cat ~/.ssh/id_ed25519.pub
```

Источник: https://docs.github.com/en/authentication/connecting-to-github-with-ssh/generating-a-new-ssh-key-and-adding-it-to-the-ssh-agent

> "ssh-keygen -t ed25519 -C "your_email@example.com""

Перевод:
> "Команда создаёт новую пару SSH-ключей типа ed25519 с указанным комментарием."

- Вставлять нужно вывод `cat` целиком, одной строкой, без сокращений и без "...".


### `location`

- `location = "am2"` — код **локации** Serverspace для создаваемых серверов и сети.
- Используется в `main.tf` как `location = var.location` во всех ресурсах `serverspace_server` и `serverspace_isolated_network`.


### `image_family`

- `image_family = "Ubuntu-20.04-X64"` — название образа ОС.
- Используется в `main.tf` как `image = var.image_family`.


### Группа `control_plane_*`

- `control_plane_name = "k8s-control-plane-1"` — имя управляющего узла Kubernetes.
- `control_plane_cpu = 2` — количество vCPU.
- `control_plane_ram = 4096` — объём RAM в мегабайтах (4 ГБ).
- `control_plane_disk = 60` — размер диска в гигабайтах; в `main.tf` умножается на 1024 для `boot_volume_size`.


### Группа `apps_*`

- `apps_name = "k8s-apps-1"` — имя сервера приложений.
- `apps_cpu = 2` — количество vCPU.
- `apps_ram = 8192` — объём RAM в мегабайтах (8 ГБ).
- `apps_disk = 80` — размер диска в гигабайтах, умножается на 1024 в `main.tf`.


### Группа `postgres_*`

- `postgres_name = "postgres-1"` — имя сервера PostgreSQL.
- `postgres_cpu = 2` — количество vCPU.
- `postgres_ram = 8192` — объём RAM в мегабайтах (8 ГБ).
- `postgres_disk = 120` — размер диска в гигабайтах, умножается на 1024 в `main.tf`.


### Группа `gitlab_*`

- `gitlab_name = "gitlab-1"` — имя сервера GitLab.
- `gitlab_cpu = 4` — количество vCPU.
- `gitlab_ram = 8192` — объём RAM в мегабайтах (8 ГБ).
- `gitlab_disk = 120` — размер диска в гигабайтах, умножается на 1024 в `main.tf`.


### Почему структура именно такая

- В `.tfvars`-файле нельзя объявлять новые переменные — только присваивать значения уже объявленным в `variables.tf`.
- Имена слева должны точно совпадать с `variables.tf` и с использованием `var.<имя>` в `main.tf`, `providers.tf`, `ssh_key.tf`.

Источник: https://developer.hashicorp.com/terraform/language/values/variables

> "To reference a variable in other parts of your configuration, use var.<NAME> syntax."

Перевод:
> "Чтобы ссылаться на переменную в других частях конфигурации, используйте синтаксис var.<ИМЯ>."

### Почему RAM в мегабайтах

Источник: https://serverspace.ru/support/help/automation-terraform/

В официальном примере Serverspace память сервера задаётся числами вроде 2048 и 8192, что соответствует мегабайтам, а не гигабайтам.

### Что важно проверить перед запуском

- `api_key` и `ssh_public_key` обязательно заменить на реальные значения — без этого Terraform не сможет ни авторизоваться, ни создать SSH-ключ.
- Остальные значения (`location`, `image_family`, все `*_name`, `*_cpu`, `*_ram`, `*_disk`) уже рабочие и не требуют обязательной замены.
- Переход от шаблона к рабочему файлу:

```bash

cd /d/Project_infra/greeting-service-infra/infra/terraform-serverspace
cp terraform.tfvars.example terraform.tfvars
```


