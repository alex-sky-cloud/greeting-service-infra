# list-resources.sh

Показывает ресурсы **текущего проекта** (тот, для которого выдан API-ключ).

## Запуск (Git Bash)

```bash

cd /d/Project_infra/greeting-service-infra/infra/terraform-serverspace
./scripts/list-resources.sh
```

Скрипт рассчитан на **Git Bash**. Нужен рабочий `python` или `jq` (не заглушка Microsoft Store). Без них выводится сырой JSON.

## Что выводит

См. разбор примера: **`list-resources-result.md`**.

- `PROJECT` — id проекта и баланс
- `SERVERS` — id, имя, state, локация
- `ISOLATED NETWORKS` — id, имя, привязанные server_ids
- `SSH KEYS` — id ключей

## Когда использовать

- Проверить, что Terraform создал в **том же проекте**, что открыт в панели
- Найти `id` сервера/сети перед удалением
- Увидеть «зависший» сервер: в API может не быть в списке, но сеть покажет `servers=...`

Токен: `export SERVERSPACE_TOKEN=...` или `docker/.env`.
