# delete-network.sh

Удаляет изолированную сеть по id.

## Запуск

```bash

cd /d/Project_infra/greeting-service-infra/infra/terraform-serverspace
./scripts/delete-network.sh l44n752
```

## Ошибка «Servers are connected to the network»

1. `./scripts/list-resources.sh` — поле `servers=` у сети
2. Удалить VM: `delete-server.sh` или панель
3. Снова `delete-network.sh`

Если VM не видна в API — **`delete-stuck-server.md`**.
