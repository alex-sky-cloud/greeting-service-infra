## Блок `terraform { ... }`

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
  key = var.api_key
}
```

Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs

> "terraform { required_providers { serverspace = { source = \"itglobalcom/serverspace\" version = \"~> 0.2.2\" } } } 
> 
> Set the variable value in *.tfvars file \# 
> 
> or using -var=\"api_key=...\" CLI option variable \"api_key\" {} 
> 
> Configure the Serverspace Provider provider \"serverspace\" { key = var.api_key }"

Перевод:
> "terraform { required_providers { serverspace = { source = \"itglobalcom/serverspace\" version = \"~> 0.2.2\" } } } 
> 
> Задайте значение переменной в файле *.tfvars  или через CLI-опцию -var=\"api_key=...\" variable \"api_key\" {} 
> 
> Настройка провайдера Serverspace provider \"serverspace\" { key = var.api_key }"

### Блок `terraform { ... }`

**`required_version = ">= 1.9.0"`**

Источник: https://developer.hashicorp.com/terraform/language/block/terraform

> "Specifies which version of the Terraform CLI is allowed to run the configuration."

Перевод:
> "Задаёт, какая версия **Terraform CLI** допускается для запуска конфигурации."

 - Значение `>= 1.9.0` разрешает выполнение конфигурации на Terraform 1.9.0 и выше. 
 - Этот параметр **не зависит** от провайдера **Serverspace** и настраивается разработчиком самостоятельно, исходя из используемых возможностей языка.

**`source = "itglobalcom/serverspace"` и `version = "~> 0.2.2"`**

Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs

> "The **Serverspace** _provider_ is used to interact with the resources supported by Serverspace. Before use configure the provider with the proper credentials."

Перевод:
> "Провайдер **Serverspace** используется для взаимодействия с ресурсами, поддерживаемыми **Serverspace**. Перед использованием настройте провайдера с соответствующими учётными данными."

 - Оба значения взяты дословно из официального блока "Example Usage" на странице документации провайдера. 
 - актуальная версия 0.3.2, опубликована 31 января 2025 года, а в официальном примере используется ограничение `~> 0.2.2`.

### Переменная `api_key`

Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs

> "\# Set the variable value in *.tfvars file \# or using -var=\"api_key=...\" CLI option variable \"api_key\" {}"

Перевод:
> " Задайте значение переменной в файле *.tfvars  или через CLI-опцию -var=\"api_key=...\" variable \"api_key\" {}"

- Провайдер требует объявления переменной `api_key`, значение которой передаётся либо через файл `.tfvars`, либо через флаг `-var` при запуске `terraform apply`/`terraform plan`. 
- Это **официально** задокументированный _способ_ **передачи** _ключа_, а не произвольное решение.

### Блок `provider "serverspace" { ... }`

Источник: https://registry.terraform.io/providers/itglobalcom/serverspace/latest/docs

> "Optional: key (String) API Key; you can obtain it from settings of your project in the control panel."

Перевод:
> "Опционально: key (String) — API-ключ; вы можете получить его в настройках вашего проекта в панели управления."

- Раздел **Schema** страницы документации подтверждает: 
  - единственный аргумент провайдера — `key`. 
  - Атрибутов `token` и `region`, которые были в исходном файле, в схеме провайдера не существует — их использование привело бы к ошибке при выполнении команд `terraform init` или `terraform plan`.
