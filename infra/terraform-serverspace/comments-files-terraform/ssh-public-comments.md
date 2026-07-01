# Комментарии к `ssh_key.tf`

## Исходный файл

```hcl
resource "serverspace_ssh" "terraform" {
  name       = "terraform-key"
  public_key = var.ssh_public_key
}
```

Источник: https://serverspace.ru/support/help/automation-terraform/

> "resource "serverspace_ssh" "terraform" {
name = "terraform-key"
public_key = "ssh-rsa AAAAB3Nza...JUDjlM= root@CentOS.local"
}"

Перевод:
> "Ресурс `serverspace_ssh` с локальным именем `terraform` создаёт SSH-ключ с именем `terraform-key`, а его публичная часть задаётся через `public_key`."

- Этот файл создаёт в Serverspace SSH-ключ, который затем можно использовать в конфигурации серверов.
- Синтаксис блока соответствует официальному примеру из документации Serverspace.

## Строка `resource "serverspace_ssh" "terraform"`

Источник: https://developer.hashicorp.com/terraform/language/block/resource

> "A `resource` block declares a resource of a given type with a given local name."

Перевод:
> "Блок `resource` объявляет ресурс заданного типа с заданным локальным именем."

- `resource` — это ключевое слово Terraform для описания управляемого объекта инфраструктуры.
- Первый идентификатор `serverspace_ssh` — это тип ресурса провайдера, то есть SSH-ключ в Serverspace.
- Второй идентификатор `terraform` — это локальное имя ресурса внутри Terraform-конфигурации.
- Локальное имя выбрано для ссылок из других файлов, например `serverspace_ssh.terraform.id`.
- Именно поэтому в блоке `resource` два имени: первое отвечает на вопрос **что создаётся**, второе — **как к этому ресурсу обращаться в коде**.

## Свойство `name = "terraform-key"`

Источник: https://serverspace.ru/support/help/automation-terraform/

> "resource "serverspace_ssh" "terraform" {
name = "terraform-key"
public_key = "ssh-rsa AAAAB3Nza...JUDjlM= root@CentOS.local"
}"

Перевод:
> "Внутри ресурса указывается `name = "terraform-key"`, то есть имя SSH-ключа в панели Serverspace."

- Свойство `name` задаёт имя объекта SSH-ключа в самом Serverspace.
- Значение `terraform-key` выбрано как человекочитаемое имя, чтобы ключ было легко узнать в панели управления.
- Это имя не обязано совпадать с локальным именем ресурса `terraform`, потому что они используются в разных контекстах.
- `name` — это имя объекта в облаке, а `terraform` — только внутренняя метка в Terraform-коде.

## Свойство `public_key = var.ssh_public_key`

Источник: https://serverspace.ru/support/help/automation-terraform/

> "public_key = "ssh-rsa AAAAB3Nza...JUDjlM= root@CentOS.local""

Перевод:
> "Свойство `public_key` должно содержать публичную часть SSH-ключа."

Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs/resources/ssh

> "Serverspace SSH key resource allows you to manage SSH keys for Server access. Keys created with this resource can be referenced in your Server configuration..."

Перевод:
> "Ресурс Serverspace SSH key позволяет управлять SSH-ключами для доступа к серверам. Ключи, созданные этим ресурсом, могут использоваться в конфигурации сервера..."

- Свойство `public_key` передаёт в ресурс публичную часть SSH-ключа.
- Использование `var.ssh_public_key` вместо жёстко прописанной строки — это правильнее, чем хранить сам ключ прямо в `ssh_key.tf`.
- Название переменной `ssh_public_key` объясняет содержимое максимально прямо: это SSH-ключ, причём именно публичный.
- Такое решение упрощает повторное использование конфигурации в разных окружениях.

## Как ресурс используется дальше

Источник: https://serverspace.ru/support/help/automation-terraform/

> "ssh_keys = [
resource.serverspace_ssh.terraform.id,
]"

Перевод:
> "В конфигурации сервера список `ssh_keys` может ссылаться на `resource.serverspace_ssh.terraform.id`."

- После создания ресурс получает `id`, и этот `id` затем передаётся в поле `ssh_keys` у ресурса `serverspace_server`.
- Поэтому локальное имя `terraform` важно: именно по нему строится ссылка `serverspace_ssh.terraform.id`.
- Если бы локальное имя было другим, например `main`, то и ссылка изменилась бы на `serverspace_ssh.main.id`.

## Почему файл называется `ssh_key.tf`

Источник: https://serverspace.ru/support/help/automation-terraform/

> "Создайте и откройте файл ssh_key.tf, в котором будет находиться публичная часть ssh-ключа для создания сервера."

Перевод:
> "Создайте и откройте файл `ssh_key.tf`, в котором будет находиться публичная часть SSH-ключа для создания сервера."

- Имя файла `ssh_key.tf` выбрано по его назначению: в нём хранится описание ресурса SSH-ключа.
- Это не обязательное имя с точки зрения Terraform, но оно удобно и соответствует официальному примеру Serverspace.
- Terraform читает все файлы `*.tf` в каталоге как одну конфигурацию, поэтому имя файла влияет в основном на читаемость проекта.

***

### Путь до публичного SSH-ключа


**Утверждение:**
Путь до публичного SSH-ключа обычно указывается позже — либо прямо в `ssh_key.tf` через функцию `file(...)`, либо через переменную в `terraform.tfvars`, а затем уже используется в `ssh_key.tf`.

**Источник:** https://developer.hashicorp.com/terraform/language/functions/file

> "The file function reads the contents of the file at the given path and returns them as a string."

Перевод:
> "Функция `file` читает содержимое файла по указанному пути и возвращает его как строку."

**Утверждение:**
Это значит, что если в ресурсе написать `public_key = file("...")`, Terraform возьмёт не путь как текст, а именно содержимое `.pub`-файла и передаст его в `public_key`.

**Источник:** https://developer.hashicorp.com/terraform/language/functions/file

> "The file function reads the contents of the file at the given path and returns them as a string."

Перевод:
> "Функция `file` читает содержимое файла по указанному пути и возвращает его как строку."

**Утверждение:**
Если путь начинается с `~`, лучше использовать `pathexpand(...)`, потому что эта функция разворачивает домашнюю директорию пользователя в полный путь.

**Источник:** https://developer.hashicorp.com/terraform/language/functions/pathexpand

> "pathexpand takes a filesystem path that might begin with a ~ segment, and if so it replaces that segment with the current user's home directory path."

Перевод:
> "`pathexpand` принимает путь файловой системы, который может начинаться с сегмента `~`, и в этом случае заменяет его на путь к домашней директории текущего пользователя."

**Утверждение:**
Поэтому технически корректный вариант прямо в `ssh_key.tf` выглядит так:

```hcl
resource "serverspace_ssh" "terraform" {
  name       = "terraform-key"
  public_key = file(pathexpand("~/.ssh/id_ed25519.pub"))
}
```

**Источник:** https://developer.hashicorp.com/terraform/language/functions/file

> "The file function reads the contents of the file at the given path and returns them as a string."

Перевод:
> "Функция `file` читает содержимое файла по указанному пути и возвращает его как строку."

**Источник:** https://developer.hashicorp.com/terraform/language/functions/pathexpand

> "pathexpand takes a filesystem path that might begin with a ~ segment, and if so it replaces that segment with the current user's home directory path."

Перевод:
> "`pathexpand` принимает путь файловой системы, который может начинаться с сегмента `~`, и в этом случае заменяет его на путь к домашней директории текущего пользователя."

**Утверждение:**
Если же оставить строку `public_key = var.ssh_public_key`, то путь до файла **нигде не указывается**, потому что Terraform в таком случае ждёт уже готовое содержимое публичного ключа, переданное через переменную.

**Источник:** https://developer.hashicorp.com/terraform/language/values/variables

> "You can add variable blocks to your configuration to define input interface for your module. This lets users pass custom values to your module at runtime."

Перевод:
> "Вы можете добавлять блоки `variable` в конфигурацию, чтобы определить входной интерфейс модуля. Это позволяет пользователям передавать собственные значения в модуль во время выполнения."

**Утверждение:**
В официальной инструкции Serverspace для `ssh_key.tf` тоже показан вариант, где в `public_key` вставляется сама публичная часть ключа, а не путь до файла.

**Источник:** https://serverspace.ru/support/help/automation-terraform/

В инструкции сказано, что нужно создать файл `ssh_key.tf`, где будет находиться публичная часть SSH-ключа для создания сервера, и вставить в переменную `public_key` своё значение.

**Утверждение:**
Если нужен именно путь до `.pub`-файла, удобнее сделать это через отдельную переменную и задать её позже, например в `terraform.tfvars`. Тогда схема будет такой:

`variables.tf`

```hcl
variable "ssh_public_key_path" {
  type = string
}
```

`terraform.tfvars`

```hcl
ssh_public_key_path = "~/.ssh/id_ed25519.pub"
```

`ssh_key.tf`

```hcl
resource "serverspace_ssh" "terraform" {
  name       = "terraform-key"
  public_key = file(pathexpand(var.ssh_public_key_path))
}
```

**Источник:** https://developer.hashicorp.com/terraform/language/values/variables

> "This lets users pass custom values to your module at runtime."

Перевод:
> "Это позволяет пользователям передавать собственные значения в модуль во время выполнения."

**Источник:** https://developer.hashicorp.com/terraform/language/functions/file

> "The file function reads the contents of the file at the given path and returns them as a string."

Перевод:
> "Функция `file` читает содержимое файла по указанному пути и возвращает его как строку."

**Источник:** https://developer.hashicorp.com/terraform/language/functions/pathexpand

> "pathexpand takes a filesystem path that might begin with a ~ segment..."

Перевод:
> "`pathexpand` принимает путь файловой системы, который может начинаться с сегмента `~`..."

**Утверждение:**
То есть ответ на ваш вопрос такой: путь можно указать либо сразу в `ssh_key.tf`, либо позже через `terraform.tfvars`, но при текущем варианте с `var.ssh_public_key` вы передаёте не путь, а уже сам текст публичного ключа.

**Источник:** https://developer.hashicorp.com/terraform/language/functions/file

> "The file function reads the contents of the file at the given path and returns them as a string."

Перевод:
> "Функция `file` читает содержимое файла по указанному пути и возвращает его как строку."

**Источник:** https://serverspace.ru/support/help/automation-terraform/

В официальном примере Serverspace в `public_key` вставляется сама публичная часть SSH-ключа.

