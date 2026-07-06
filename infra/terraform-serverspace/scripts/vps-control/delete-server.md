# delete-server.sh

Удаляет **один** сервер по id.

## Запуск

```bash

cd /d/Project_infra/greeting-service-infra/infra/terraform-serverspace
./scripts/delete-server.sh l44s1304957
```

Id — в `./scripts/list-resources.sh` или URL карточки в панели.

## Зависший «Создание 30%»

Сервер может не быть в `GET /servers`, но блокировать сеть. См. **`delete-stuck-server.md`**.
