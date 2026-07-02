# find-stuck-server.sh

Диагностика после ошибки Terraform: `task 'lt6336740' failed`.

## Запуск (достаточно одного аргумента)

```bash

cd /d/Project_infra/greeting-service-infra/infra/terraform-serverspace
./scripts/find-stuck-server.sh lt6336740
```

**Id сети указывать не нужно** — скрипт сам выводит **все** isolated-сети проекта.

Справка:

```bash

./scripts/find-stuck-server.sh --help
```

## Аргументы

| Аргумент | Обязателен | Откуда взять |
|----------|------------|--------------|
| `TASK_ID` | да | Ошибка Terraform: `task 'lt6336740' failed` → `lt6336740` |
| `NETWORK_ID` | **нет** | Только если нужна одна конкретная сеть (редко) |

Пример с сетью (опционально):

```bash

./scripts/find-stuck-server.sh lt6336740 l44n754
```

Id сети — из блока `isolated networks (all in project)` или из `terraform.tfstate` (`reactive_net.id`).

## Что выводит скрипт

| Блок | Смысл |
|------|--------|
| `task` | Completed / Failed, `server_id` (если есть) |
| `isolated networks (all in project)` | Все сети и привязанные VM |
| `servers list` | Все VM в проекте |
| `probe` | Id рядом с текущими VM (поиск «призрака») |

## Удаление (только Failed + есть server_id)

```bash

./scripts/find-stuck-server.sh --delete lt6336740
```

## Связанные файлы

| Файл | Назначение |
|------|------------|
| `find-stuck-server.sh` | Скрипт |
| `find-stuck-server.md` | Эта памятка |
| `find-stuck-server-result.md` | Разбор примера вывода |
| `delete-stuck-server.md` | Завис «Создание 30%» |
