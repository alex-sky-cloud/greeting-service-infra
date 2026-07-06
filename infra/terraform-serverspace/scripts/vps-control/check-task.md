# check-task.sh

Показывает статус задачи Serverspace (`lt...` из лога Terraform).

## Запуск

```bash

cd /d/Project_infra/greeting-service-infra/infra/terraform-serverspace

./scripts/check-task.sh lt6334455
```

## Что смотреть

- `is_completed`: `Completed` / `Failed` / в процессе
- `server_id` — id созданной VM (если есть)

Детальный текст ошибки API часто **не отдаёт** — только `Failed`.
