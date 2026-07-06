# delete-all.sh

Полная очистка проекта Serverspace: **все VM + все изолированные сети** одним запуском.

## Когда использовать

- Снести тестовую инфраструктуру перед новым `terraform apply`
- Убрать дубликаты после неудачных apply
- Очистить проект, не заходя в панель по одной карточке

Не используйте на проде без проверки `list-resources.sh`.

---

## Пошагово

### 1. Перейти в каталог Terraform

```bash

cd /d/Project_infra/greeting-service-infra/infra/terraform-serverspace
```

### 2. Проверить токен

Токен в `docker/.env` или:

```bash

export SERVERSPACE_TOKEN=ваш_ключ
```

### 3. Посмотреть, что будет удалено

```bash

./scripts/list-resources.sh
```

Сверить **project id** с панелью Serverspace. Запомнить имена VM и id сетей.

### 4. Удалить всё

```bash

./scripts/delete-all.sh
```

Скрипт выведет:

| Блок | Смысл |
|------|--------|
| `servers (before)` | Список VM до удаления |
| `DELETE servers` | `DELETE server l44s...` для каждой VM |
| `networks (before)` | Список сетей до удаления |
| `DELETE networks` | `DELETE network l44n...` для каждой сети |
| `after (проверка)` | Повторный опрос API |

### 5. Убедиться в результате

Успех:

```
servers: 0
networks: 0
OK: проект пуст (серверов и сетей нет).
```

Неудача:

```
ВНИМАНИЕ: остались ресурсы — servers=0 networks=1
```

→ см. **`delete-stuck-server.md`**, **`probe-server-ids.sh`**.

### 6. Начать с нуля (локальный Terraform)

После `OK: проект пуст` удалить локальные артефакты Terraform в каталоге `infra/terraform-serverspace`:

```bash

rm -f terraform.tfstate terraform.tfstate.backup
rm -f .terraform.lock.hcl
rm -f tfplan
rm -rf .terraform
docker compose -f docker/docker-compose.yml --env-file docker/.env run --rm terraform init
```

| Файл / каталог | Зачем удалять |
|----------------|---------------|
| `terraform.tfstate` | Старый state — ссылки на уже удалённые VM/сети |
| `.terraform.lock.hcl` | Lock версий провайдера — пересоздастся при `init` |
| `tfplan` | Сохранённый plan от прошлого запуска — не применять старый |
| `.terraform/` | Кэш провайдеров и модулей — чистый `init` |

Затем новый цикл: `plan` → `apply`.

SSH-ключи (`terraform-key`) скрипт **не удаляет** — при необходимости вручную в панели или отдельным API.

---

## Как работает внутри

1. `GET /api/v1/servers` → для каждого `id` → `DELETE /api/v1/servers/{id}`
2. `GET /api/v1/networks/isolated` → для каждого `id` → `DELETE /api/v1/networks/isolated/{id}`
3. Повторный `GET` — счётчик должен быть 0

Порядок: **сначала серверы, потом сети** (иначе ошибка `Servers are connected to the network`).

Заголовок API: `X-API-KEY`, не `Authorization: Bearer`.

---

## Точечное удаление

| Задача | Скрипт |
|--------|--------|
| Одна VM | `./scripts/delete-server.sh l44s1304957` |
| Одна сеть | `./scripts/delete-network.sh l44n752` |
| Только посмотреть | `./scripts/list-resources.sh` |

---

## Связанные файлы

| Файл | Назначение |
|------|------------|
| `delete-all.sh` | Скрипт |
| `delete-all.md` | Эта памятка |
| `delete-stuck-server.md` | Если сеть/VM не удаляются |
