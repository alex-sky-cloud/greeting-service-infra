# scripts — утилиты Serverspace API

Git Bash, каталог `infra/terraform-serverspace`:

```bash

cd /d/Project_infra/greeting-service-infra/infra/terraform-serverspace
chmod +x scripts/*.sh
./scripts/list-resources.sh
```

Токен: `export SERVERSPACE_TOKEN=...` или `docker/.env`.

| Скрипт | Памятка | Назначение |
|--------|---------|------------|
| `list-resources.sh` | `list-resources.md` | Список проекта, VM, сетей |
| | `list-resources-result.md` | Разбор примера вывода |
| `check-task.sh` | `check-task.md` | Статус задачи `lt...` |
| `find-stuck-server.sh` | `find-stuck-server.md` | Диагностика задачи после Failed apply (достаточно TASK_ID) |
| | `find-stuck-server-result.md` | Разбор вывода |
| `probe-server-ids.sh` | `probe-server-ids.md` | Перебор id серверов |
| `delete-all.sh` | `delete-all.md` | Удалить все VM и сети (пошаговая памятка) |
| `delete-server.sh` | `delete-server.md` | Удалить одну VM |
| `delete-network.sh` | `delete-network.md` | Удалить сеть |

**Завис «Создание 30%»** — `delete-stuck-server.md`.

Общий код: `lib/common.sh`.
