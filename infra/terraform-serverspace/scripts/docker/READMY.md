
> "Скрипт сам переходит в `infra/terraform`, подхватывает `docker/.env` и запускает `docker compose run --rm terraform …`"

## Содержимое скрипта

Создайте файл `scripts/docker/terraform-docker.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Каталог, где лежит docker-compose.yml для Terraform
TF_DIR="$(cd "$(dirname "${BASH_SOURCE[^0]}")/.." && pwd)/infra/terraform"
COMPOSE_FILE="docker/docker-compose.yml"
ENV_FILE="docker/.env"

cd "$TF_DIR"

run_tf() {
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" run --rm terraform "$@"
}

case "${1:-}" in
  init)
    run_tf init
    ;;
  validate)
    run_tf validate
    ;;
  plan)
    run_tf plan -out=tfplan
    ;;
  apply)
    run_tf apply tfplan
    ;;
  destroy)
    run_tf destroy
    ;;
  *)
    echo "Использование: $0 {init|validate|plan|apply|destroy}"
    exit 1
    ;;
esac
```

После создания файла дайте ему права на выполнение:

```bash

cd '/d/Project_infra/greeting-service-infra/infra/terraform-serverspace'

chmod +x scripts/docker/terraform-docker.sh
```


## Пояснение к каждой команде скрипта

| Команда запуска | Что происходит внутри скрипта | Пояснение |
| :-- | :-- | :-- |
| `./scripts/terraform-docker.sh init` | вызывает `docker compose run --rm terraform init` | Скачивает provider-плагины в `.terraform/` на вашем ПК — обязательно первым шагом |
| `./scripts/terraform-docker.sh validate` | вызывает `terraform validate` | Проверяет синтаксис `.tf`-файлов, ничего не создаёт и не обращается к облаку |
| `./scripts/terraform-docker.sh plan` | вызывает `terraform plan -out=tfplan` | Строит план изменений и сохраняет его в файл `tfplan` внутри `infra/terraform`, а не просто выводит diff в консоль |
| `./scripts/terraform-docker.sh apply` | вызывает `terraform apply tfplan` | Применяет именно тот план, что был сохранён на шаге `plan`, а не пересчитывает план заново — это гарантирует, что применяется ровно то, что вы видели на экране |
| `./scripts/terraform-docker.sh destroy` | вызывает `terraform destroy` | Удаляет все ресурсы, описанные в state, — используйте осторожно, действие необратимо |

## Почему именно `plan -out=tfplan` + `apply tfplan`
 - это правильный подход по официальной документации.


> "`plan` | Показывает diff; ничего не создаёт"
> "`apply` | Вызывает API облаков; обновляет `terraform.tfstate`"

Если запускать `terraform apply` без сохранённого файла плана, Terraform заново пересчитает план прямо перед применением — а между вашим просмотром плана и нажатием **apply** состояние облака могло измениться. 
 - Сохранение в `tfplan` фиксирует ровно то, что вы проверили, и `apply tfplan` гарантированно применит именно эти изменения, без пересчёта.

## Полный порядок запуска (обновлённый)

```bash

cd '/d/Project_infra/greeting-service-infra/infra/terraform-serverspace'

chmod +x scripts/docker/terraform-docker.sh

./scripts/docker/terraform-docker.sh init
./scripts/docker/terraform-docker.sh validate
./scripts/docker/terraform-docker.sh plan
./scripts/docker/terraform-docker.sh apply

./scripts/docker/terraform-docker.sh destroy
```

Для удаления инфраструктуры отдельно:

```bash

./scripts/docker/terraform-docker.sh destroy
```

**Рекомендованное значение, а не жёсткое требование:** имя файла `tfplan` в скрипте — это просто удобное соглашение, можно назвать иначе (например, `plan.out`), Terraform не требует конкретного имени файла плана.


после `destroy` в папке `terraform-serverspace` остались служебные файлы, которые Terraform не удаляет автоматически: `.terraform/`, `.terraform.lock.hcl`, `.terraform.tfstate.lock.info`, `terraform.tfstate` и `tfplan`. Это нормальное поведение — `destroy` удаляет только ресурсы в облаке, а не свои же локальные служебные файлы.

## Почему это не баг, а норма Terraform

`terraform destroy` меняет содержимое `terraform.tfstate` на пустое (ресурсов там не остаётся), но сам файл state, кэш плагинов и lock-файлы Terraform **намеренно** не трогает — это часть его обычной работы, а не что-то специфичное для Serverspace.

## Новый скрипт: `terraform-clean.sh`

Добавьте рядом с `terraform-docker.sh` (в той же папке `scripts/docker/`) новый файл `terraform-clean.sh`:

```bash

#!/usr/bin/env bash
set -euo pipefail

# Каталог infra/terraform-serverspace — на два уровня выше самого скрипта
TF_DIR="$(cd "$(dirname "${BASH_SOURCE[^0]}")/../.." && pwd)"

cd "$TF_DIR"

echo "Очистка локальных служебных файлов Terraform в: $TF_DIR"

rm -rf .terraform
rm -f .terraform.lock.hcl
rm -f .terraform.tfstate.lock.info
rm -f terraform.tfstate
rm -f terraform.tfstate.backup
rm -f tfplan

echo "Готово. Каталог подготовлен для нового init."
```

Дайте права на выполнение:

```bash

chmod +x scripts/docker/terraform-clean.sh
```


## Пояснение к скрипту: что и зачем удаляется

| Файл/папка | Зачем удаляется | Что будет, если не удалить |
| :-- | :-- | :-- |
| `.terraform/` | Кэш скачанных provider-плагинов | Не мешает новой установке, но занимает место; безопасно удалить и скачать заново при `init` |
| `.terraform.lock.hcl` | Фиксирует версии провайдеров | Не мешает, но при смене версий провайдера лучше пересоздать |
| `.terraform.tfstate.lock.info` | Файл блокировки state | Если остался после сбойного запуска, может мешать новому `apply`, показывая ложную блокировку |
| `terraform.tfstate` | «Память» Terraform — какие ресурсы существуют | После `destroy` он уже пустой, но лучше удалить, чтобы не путать историю при новой установке |
| `terraform.tfstate.backup` | Автоматическая резервная копия предыдущего state | Не нужен, если начинаете установку с нуля |
| `tfplan` | Сохранённый план из прошлого запуска `plan` | Устаревший план нельзя применять повторно — новый `plan` создаст свежий файл |

## Как использовать

Находясь в `infra/terraform-serverspace`, после `destroy` выполните:

```bash

./scripts/docker/terraform-clean.sh
```

Затем начинайте установку заново:

```bash

./scripts/docker/terraform-docker.sh init
./scripts/docker/terraform-docker.sh validate
./scripts/docker/terraform-docker.sh plan
./scripts/docker/terraform-docker.sh apply
```

**Важно (рекомендация, а не жёсткое требование):** очистку `.terraform/` и lock-файла делать не обязательно каждый раз — если вы не меняли версию провайдера в `providers.tf`, можно оставить их и просто удалить `terraform.tfstate` и `tfplan`. Полная очистка нужна, только если хотите гарантированно чистый старт, например, при смене провайдера или диагностике проблем.
