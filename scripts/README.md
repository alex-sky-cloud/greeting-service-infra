# Скрипты greeting-service-infra

Краткий указатель: **локальные** скрипты работают на вашем ПК (Docker, kubectl с kubeconfig). **Удалённые** — через SSH-туннель или SSH на devtools; по умолчанию **не удаляют данные** на managed PostgreSQL.

## Локальная разработка

| Скрипт | Назначение |
|--------|------------|
| [`create-reactive-demo-db.sh`](create-reactive-demo-db.sh) / [`.cmd`](create-reactive-demo-db.cmd) | Создать БД `reactive_demo` в Docker `local-postgres` (идемпотентно). При необходимости поднимает `docker compose`. |
| [`local-reactive-demo-db.md`](local-reactive-demo-db.md) | Пошаговая инструкция для reactive-demo локально. |

**Запуск reactive-demo локально:**

```bash
# из корня репозитория
bash scripts/create-reactive-demo-db.sh
cd reactive-demo && ./gradlew bootRun --args='--spring.profiles.active=local'
```

Windows CMD: `scripts\create-reactive-demo-db.cmd`

---

## Kubernetes и registry (локальный kubectl → удалённый кластер)

| Скрипт | Назначение | Безопасность |
|--------|------------|--------------|
| [`get-kubeconfig.sh`](get-kubeconfig.sh) / [`.cmd`](get-kubeconfig.cmd) | Сохранить kubeconfig из terraform output | Только локальный файл |
| [`create-secrets.sh`](create-secrets.sh) | Secret'ы registry + DB в namespace dev/stage/prod | БД не трогает; обновляет Secret в K8s |
| [`apply-k8s-insecure-registry.sh`](apply-k8s-insecure-registry.sh) | Insecure pull с devtools:5000 на worker-ноде | Перезапуск k0sworker на ноде |
| [`k8s-worker-insecure-registry.sh`](k8s-worker-insecure-registry.sh) | Payload для worker (вызывается из apply-*) | То же |
| [`setup-registry.sh`](setup-registry.sh) | Установить Docker Registry на devtools (SSH pipe) | Не трогает PostgreSQL |
| [`tune-registry-upload.sh`](tune-registry-upload.sh) | TCP keepalive + докачка upload для медленного push | Перезапуск registry |

---

## Удалённая PostgreSQL (`dev-db-connection/`)

Типичный порядок: `01` → `02` → `03` → `06` → `04`.

| Скрипт | Назначение | Меняет данные? |
|--------|------------|----------------|
| [`01-show-terraform-ips.sh`](dev-db-connection/01-show-terraform-ips.sh) | IP из terraform | Нет |
| [`02-check-ssh.sh`](dev-db-connection/02-check-ssh.sh) | SSH на devtools | Нет |
| [`03-start-tunnel.sh`](dev-db-connection/03-start-tunnel.sh) | Туннель localhost:15432 → PG | Нет |
| [`04-stop-tunnel.sh`](dev-db-connection/04-stop-tunnel.sh) | Остановить туннель | Нет |
| [`05-check-tunnel-port.sh`](dev-db-connection/05-check-tunnel-port.sh) | TCP-проверка порта | Нет |
| [`06-psql-test.sh`](dev-db-connection/06-psql-test.sh) | Тестовый SELECT | Нет |
| [`07-verify-all.sh`](dev-db-connection/07-verify-all.sh) | Полная диагностика | Нет |
| [`08-clean-remote-database.sh`](dev-db-connection/08-clean-remote-database.sh) | DROP учебных схем в `greeting_db` | **ДА** — нужен ввод `yes` |
| [`09-check-db-user-privileges.sh`](dev-db-connection/09-check-db-user-privileges.sh) | Права greeting_user (туннель) | Нет |
| [`09-check-db-user-privileges-wsl.sh`](dev-db-connection/09-check-db-user-privileges-wsl.sh) | То же через SSH+psql на devtools | Нет (может apt install client) |
| [`10-create-reactive-demo-db.sh`](dev-db-connection/10-create-reactive-demo-db.sh) | `CREATE DATABASE reactive_demo` если нет | Только новая БД, существующие данные не трогает |
| [`lib.sh`](dev-db-connection/lib.sh) | Общие функции | — |

Обёртка: [`verify-db-tunnel-gitbash.sh`](verify-db-tunnel-gitbash.sh) → `07-verify-all.sh`.

**Перед удалёнными скриптами:** `source ~/.bashrc` (переменная `TF_VAR_db_password`).

---

## Только на сервере GitLab

| Скрипт | Назначение |
|--------|------------|
| [`check-gitlab-pat.sh`](check-gitlab-pat.sh) | Проверить PAT IntelliJ (только чтение) |

```bash
ssh root@<DEVTOOLS_IP> 'bash -s' < scripts/check-gitlab-pat.sh
```

---

## Опасные операции

- **`08-clean-remote-database.sh`** — удаляет схемы `iso_demo`, `shop_demo` и flyway history в **greeting_db**. Не затрагивает `reactive_demo`.
- **`apply-k8s-insecure-registry.sh`** — кратковременно перезапускает worker.

Все остальные скрипты либо только читают, либо создают ресурсы идемпотентно (CREATE DATABASE IF NOT EXISTS, kubectl apply).
