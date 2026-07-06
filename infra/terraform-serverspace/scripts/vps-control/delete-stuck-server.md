# Зависший сервер «Создание 30%»

## Почему

Terraform запустил несколько VM; task `Failed`, карточка в панели осталась. Сервер:
- блокирует удаление сети;
- может не быть в `list-resources.sh`.

## Что делать

### 1. Панель

«…» → **Удалить**. Не получается — шаг 2.

### 2. API

```bash

./scripts/find-stuck-server.sh lt6334455
./scripts/probe-server-ids.sh l44s1304940 l44s1304959
./scripts/delete-server.sh l44sXXXXXXXX
./scripts/delete-network.sh l44n752
```

### 3. Поддержка Serverspace

| Где | Что видно |
|-----|-----------|
| Панель | «Создание 30%», удалить нельзя |
| `list-resources.sh` | servers: 0 |
| `delete-network.sh` | Servers are connected |

Текст обращения — в **`find-stuck-server.md`**.

### 4. После очистки

```bash

rm -f terraform.tfstate terraform.tfstate.backup
docker compose -f docker/docker-compose.yml --env-file docker/.env run --rm terraform init
```

Затем `plan` / `apply`.
