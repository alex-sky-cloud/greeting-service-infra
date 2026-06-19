# reactive-demo — полная инструкция

Учебный модуль **Spring Boot WebFlux + R2DBC**: реальные контроллеры, сервисы, репозитории.  
Конфигурация **как у соседнего `app/`** — один PostgreSQL, разные базы данных.

| | `app` | `reactive-demo` |
|---|-------|-----------------|
| Порт HTTP | 8080 | **8081** |
| База PostgreSQL | `app` (локально) / `greeting_db` (сервер) | **`reactive_demo`** |
| Доступ к БД | JDBC (JPA) | **JDBC (Flyway)** + **R2DBC (runtime)** |
| Profile локально | `local` | `local` |
| Profile на сервере | default + env | default + env |

---

## Оглавление

1. [Как устроено подключение к БД](#1-как-устроено-подключение-к-бд)
2. [Локальный запуск (Windows / Git Bash)](#2-локальный-запуск)
3. [Запуск с ноутбука на удалённую БД (SSH-туннель)](#3-запуск-с-ноутбука-на-удалённую-бд)
4. [Деплой на удалённый сервер (K8s) — пошагово](#4-деплой-на-удалённый-сервер-k8s)
5. [CI/CD (GitLab)](#5-cicd-gitlab)
6. [Docker-образ вручную](#6-docker-образ-вручную)
7. [API и проверка после старта](#7-api-и-проверка-после-старта)
8. [Конфигурация приложения](#8-конфигурация-приложения)
9. [Flyway и миграции](#9-flyway-и-миграции)
10. [Тесты](#10-тесты)
11. [Сброс данных](#11-сброс-данных)
12. [Типичные ошибки](#12-типичные-ошибки)
13. [Файлы в репозитории](#13-файлы-в-репозитории)
14. [Чек-листы](#14-чек-листы)

---

## 1. Как устроено подключение к БД

**Один PostgreSQL-сервер**, **отдельная база** `reactive_demo`. Порт PostgreSQL везде **5432** (на ноутбуке через туннель — **15432**).

```
┌─────────────────────────────────────────────────────────────┐
│  PostgreSQL (один инстанс)                                   │
│  ├── база app / greeting_db     → модуль app                 │
│  └── база reactive_demo         → модуль reactive-demo       │
└─────────────────────────────────────────────────────────────┘
```

При старте приложения:

1. **Flyway** (JDBC, `DB_URL`) — создаёт схему `reactive_demo`, таблицы, seed.
2. **R2DBC** (`R2DBC_URL`) — все запросы из WebFlux-репозиториев.

На сервере **не нужен** profile `local` и **не нужен** SSH-туннель — pod в VPC подключается к приватному IP PostgreSQL напрямую.

| Режим | Profile | Откуда настройки |
|-------|---------|------------------|
| Локально | `SPRING_PROFILES_ACTIVE=local` | `application-local.yml` |
| Ноутбук → удалённая БД | default | env: `DB_URL`, `R2DBC_URL`, … |
| K8s (dev/prod) | default | Secret `reactive-demo-secret` |

---

## 2. Локальный запуск

### Шаг 1. PostgreSQL (общий с `app`)

> **Подробно:** [`scripts/local-reactive-demo-db.md`](../scripts/local-reactive-demo-db.md) — создание БД, типичные ошибки, все скрипты.

Из **корня репозитория**:

```bash
cd app/src/main/resources/docker-greeting
docker compose up -d
```

Сервис `reactive-demo-db-init` в compose создаёт базу `reactive_demo`, если её ещё нет.

**Если Postgres уже работал до обновления** (volume старый) — один раз из корня репозитория:

```cmd
scripts\create-reactive-demo-db.cmd
```

```bash
bash scripts/create-reactive-demo-db.sh
```

**Проверка:**

```bash
docker exec local-postgres psql -U app -d reactive_demo -c "\dt reactive_demo.*"
```

Должны появиться таблицы после первого запуска приложения (Flyway). До первого запуска список может быть пуст — это нормально.

### Шаг 2. Запуск приложения

```bash
cd reactive-demo
```

**PowerShell:**

```powershell
$env:SPRING_PROFILES_ACTIVE="local"
$env:APP_ENV="local"
.\gradlew.bat bootRun
```

**Git Bash:**

```bash
SPRING_PROFILES_ACTIVE=local APP_ENV=local ./gradlew bootRun --no-daemon
```

Приложение: http://localhost:8081

Профиль `local` → `application-local.yml`:

- JDBC: `jdbc:postgresql://localhost:5432/reactive_demo`
- R2DBC: `r2dbc:postgresql://localhost:5432/reactive_demo`
- пользователь: `app` / `app`

Переменные `DB_URL` / `R2DBC_URL` **не нужны**.

### Шаг 3. Проверка

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8081/api/users/1/summary
curl "http://localhost:8081/api/demo/reactor/compare?ids=1,2"
```

---

## 3. Запуск с ноутбука на удалённую БД

Тот же managed PostgreSQL, что у `app` (Timeweb Cloud, VPC). Подробности туннеля: `docs/dev-remote-db-connection.md`.

### Шаг 1. SSH-туннель

```bash
bash scripts/dev-db-connection/03-start-tunnel.sh
```

Локальный порт по умолчанию: **15432**.

### Шаг 2. База `reactive_demo`

**Если уже делали `terraform apply`** после добавления `reactive_demo` в `database.tf` — база уже есть, этот шаг пропускайте.

**Иначе** (один раз):

```bash
bash scripts/dev-db-connection/10-create-reactive-demo-db.sh
```

### Шаг 3. Запуск без profile `local`

Profile **`local` не используем**.

**Git Bash:**

```bash
cd reactive-demo

DB_URL=jdbc:postgresql://localhost:15432/reactive_demo \
R2DBC_URL=r2dbc:postgresql://localhost:15432/reactive_demo \
DB_USERNAME=greeting_user \
DB_PASSWORD="$TF_VAR_db_password" \
APP_ENV=dev \
./gradlew bootRun --no-daemon
```

**PowerShell:**

```powershell
$env:DB_URL="jdbc:postgresql://localhost:15432/reactive_demo"
$env:R2DBC_URL="r2dbc:postgresql://localhost:15432/reactive_demo"
$env:DB_USERNAME="greeting_user"
$env:DB_PASSWORD=$env:TF_VAR_db_password
$env:APP_ENV="dev"
.\gradlew.bat bootRun
```

`R2DBC_URL` — тот же хост, порт и имя БД, что в `DB_URL`, но префикс `r2dbc:` вместо `jdbc:`.

---

## 4. Деплой на удалённый сервер (K8s)

Полная цепочка: **Terraform → Secret → образ в Registry → Helm → Ingress**.

### 4.1. Terraform — база и outputs

Файл: `infra/terraform/database.tf`

- ресурс `twc_database_instance.reactive_demo_db` — база `reactive_demo`;
- у `greeting_user` права на `greeting_db` **и** `reactive_demo`.

```bash
cd infra/terraform
terraform apply
```

Проверка outputs:

```bash
terraform output reactive_demo_jdbc_url
terraform output reactive_demo_r2dbc_url
terraform output db_host
```

Пример JDBC: `jdbc:postgresql://10.10.0.5:5432/reactive_demo`  
Пример R2DBC: `r2dbc:postgresql://10.10.0.5:5432/reactive_demo`

> IP `10.10.0.5` — пример; актуальный IP: `terraform output db_host` или `bash scripts/dev-db-connection/01-show-terraform-ips.sh`.

### 4.2. kubeconfig

```bash
terraform output -raw kubeconfig > ~/.kube/timeweb-greeting.yaml
export KUBECONFIG=~/.kube/timeweb-greeting.yaml
kubectl get nodes
```

### 4.3. Kubernetes Secrets

Скрипт создаёт **оба** Secret: `greeting-service-secret` и **`reactive-demo-secret`**.

```bash
# из корня репозитория
export KUBECONFIG=~/.kube/timeweb-greeting.yaml

DB_URL="$(cd infra/terraform && terraform output -raw db_jdbc_url)" \
DB_USERNAME=greeting_user \
DB_PASSWORD="$TF_VAR_db_password" \
REGISTRY_HOST="72.56.249.137:5000" \
REGISTRY_USER=docker \
REGISTRY_PASSWORD=docker \
bash scripts/create-secrets.sh
```

Secret **`reactive-demo-secret`** (в namespace `dev`, `stage`, `prod`):

| Ключ | Назначение |
|------|------------|
| `DB_URL` | JDBC → `.../reactive_demo` (Flyway) |
| `R2DBC_URL` | R2DBC → `.../reactive_demo` (WebFlux) |
| `DB_USERNAME` | `greeting_user` |
| `DB_PASSWORD` | пароль из Terraform |

Проверка:

```bash
kubectl get secret reactive-demo-secret -n dev
kubectl describe secret reactive-demo-secret -n dev
```

Шаблон: `infra/k8s/secret-template.yaml`.

### 4.4. Сборка и push образа

**Через CI/CD** (рекомендуется) — см. [раздел 5](#5-cicd-gitlab).

**Вручную** на devtools:

```bash
cd reactive-demo
./gradlew bootJar -x test
docker build -t 72.56.249.137:5000/reactive-demo:dev .
docker push 72.56.249.137:5000/reactive-demo:dev
```

### 4.5. Helm deploy

Chart: `infra/helm/reactive-demo/`

**Dev:**

```bash
helm upgrade --install reactive-demo infra/helm/reactive-demo \
  --namespace dev \
  --create-namespace \
  -f infra/helm/reactive-demo/values.yaml \
  -f infra/helm/reactive-demo/values-dev.yaml \
  --set image.repository=72.56.249.137:5000/reactive-demo \
  --set image.tag=dev \
  --atomic \
  --timeout 5m

kubectl rollout status deployment/reactive-demo-reactive-demo -n dev --timeout=300s
```

**Prod** (после проверки в dev):

```bash
helm upgrade --install reactive-demo infra/helm/reactive-demo \
  --namespace prod \
  --create-namespace \
  -f infra/helm/reactive-demo/values.yaml \
  -f infra/helm/reactive-demo/values-prod.yaml \
  --set image.repository=72.56.249.137:5000/reactive-demo \
  --set image.tag=TAG \
  --atomic \
  --timeout 10m
```

### 4.6. DNS и Ingress

| Окружение | Host (Ingress) |
|-----------|----------------|
| dev | `reactive-demo-dev.cloud-terra.online` |
| prod | `reactive-demo.cloud-terra.online` |

Настройте A-запись на тот же ingress/load balancer, что у `greeting-dev` / `greeting` (см. `infra/terraform/dns.tf`).

### 4.7. Проверка pod

```bash
kubectl get pods -n dev -l app.kubernetes.io/name=reactive-demo
kubectl logs -n dev deployment/reactive-demo-reactive-demo --tail=100
kubectl port-forward -n dev svc/reactive-demo-reactive-demo 8081:80
curl http://localhost:8081/actuator/health
curl http://localhost:8081/api/users/1/summary
```

Или через Ingress:

```bash
curl http://reactive-demo-dev.cloud-terra.online/actuator/health
```

### 4.8. Что происходит при старте pod

1. Kubernetes монтирует env из `reactive-demo-secret`.
2. Spring Boot читает `application.yml` (profile **не** `local`).
3. **Flyway** накатывает `db/migration/V0`…`V3` в схему `reactive_demo`.
4. **R2DBC** поднимает пул соединений.
5. Actuator `/actuator/health/readiness` → pod Ready.
6. Ingress отдаёт трафик на порт 8081 внутри контейнера.

---

## 5. CI/CD (GitLab)

Файл pipeline: `ci/.gitlab-ci.yml`  
Путь к конфигу в GitLab: **Settings → CI/CD → CI/CD configuration file** → `ci/.gitlab-ci.yml`.

| Job | Ветка | Действие |
|-----|-------|----------|
| `build-and-test-reactive-demo` | feature/*, develop, main, MR | `test` + `bootJar` |
| `build-and-push-docker-reactive-demo` | develop, main | push в Registry |
| `deploy-dev-reactive-demo` | develop | Helm → namespace `dev` |
| `deploy-prod-reactive-demo` | main | Helm → `prod` (**manual**) |

Переменные GitLab (как у `app`): `REGISTRY_HOST`, `REGISTRY_USER`, `REGISTRY_PASSWORD`, `KUBE_CONFIG_BASE64`.

Дополнительно (есть defaults в pipeline):

- `REACTIVE_DEMO_IMAGE_NAME=reactive-demo`
- `REACTIVE_DEMO_HELM_RELEASE=reactive-demo`

**Порядок первого деплоя через CI:**

1. `terraform apply`
2. `bash scripts/create-secrets.sh`
3. DNS на ingress host
4. Push / merge в `develop`
5. Дождаться `deploy-dev-reactive-demo`

---

## 6. Docker-образ вручную

```bash
cd reactive-demo
docker build -t reactive-demo:local .
```

Запуск (нужны env, profile `local` в образ **не** зашит):

```bash
docker run --rm -p 8081:8081 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/reactive_demo \
  -e R2DBC_URL=r2dbc:postgresql://host.docker.internal:5432/reactive_demo \
  -e DB_USERNAME=app \
  -e DB_PASSWORD=app \
  reactive-demo:local
```

Dockerfile: `reactive-demo/Dockerfile` → JAR `reactive-demo.jar`, порт **8081**.

---

## 7. API и проверка после старта

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/actuator/health` | health check |
| GET | `/api/users` | все пользователи |
| GET | `/api/users/{id}` | один пользователь |
| GET | `/api/users/{id}/summary` | user + заказы (`flatMap`) |
| GET | `/api/users/{id}/email-upper` | пример `map` |
| GET | `/api/demo/reactor/compare?ids=1,2` | demo: `map` vs `flatMap` |
| GET | `/api/demo/reactor/users?ids=1,2` | правильный `flatMap` |
| GET | `/api/demo/reactor/users-concat?ids=1,2,3` | `concatMap` |

---

## 8. Конфигурация приложения

| Файл | Когда используется |
|------|-------------------|
| `application.yml` | K8s, туннель — env **обязательны** |
| `application-local.yml` | profile `local` — localhost:5432/reactive_demo |
| `application-test.yml` | тесты + Testcontainers |

**Обязательные env (без profile `local`):**

| Переменная | Пример |
|------------|--------|
| `DB_URL` | `jdbc:postgresql://host:5432/reactive_demo` |
| `R2DBC_URL` | `r2dbc:postgresql://host:5432/reactive_demo` |
| `DB_USERNAME` | `greeting_user` / `app` |
| `DB_PASSWORD` | пароль |

Опционально: `APP_ENV`, `APP_VERSION`.

---

## 9. Flyway и миграции

Каталог: `src/main/resources/db/migration/`

| Файл | Содержание |
|------|------------|
| `V0__create_schema.sql` | схема `reactive_demo` |
| `V1__users_table.sql` | таблица users |
| `V2__orders_table.sql` | таблица orders |
| `V3__seed_data.sql` | тестовые данные |

Flyway: `schemas: reactive_demo`, `create-schemas: true` — см. `application.yml`.

Подробное пояснение, почему с R2DBC нужен отдельный JDBC URL для Flyway:
**[`src/main/resources/db/flyway-r2dbc-migrations.md`](src/main/resources/db/flyway-r2dbc-migrations.md)**

DDL **только** в миграциях, не в Terraform и не в Helm.

---

## 10. Тесты

```bash
cd reactive-demo
./gradlew test
# Windows:
gradlew.bat test
```

- Unit-тесты — Mockito + StepVerifier.
- Интеграционные — Testcontainers (PostgreSQL), profile `test`.
- Docker на машине **обязателен** для интеграционных тестов.

---

## 11. Сброс данных

**Локально:**

```bash
docker exec -i local-postgres psql -U app -d reactive_demo -v ON_ERROR_STOP=1 \
  < reactive-demo/src/main/resources/db/clean-database.sql
```

Перезапустите приложение — Flyway применит миграции снова.

**Удалённая БД** (через туннель):

```bash
psql -h 127.0.0.1 -p 15432 -U greeting_user -d reactive_demo \
  -v ON_ERROR_STOP=1 -f reactive-demo/src/main/resources/db/clean-database-remote.sql
```

---

## 12. Типичные ошибки

### `database "reactive_demo" does not exist`

База не создана на PostgreSQL.

**Локально:**

```cmd
scripts\create-reactive-demo-db.cmd
```

или `docker compose up -d` в `docker-greeting` (сервис `reactive-demo-db-init`).

**Удалённо:** `terraform apply` или `bash scripts/dev-db-connection/10-create-reactive-demo-db.sh`.

### `Could not resolve placeholder 'R2DBC_URL'`

Запуск без profile `local`, но env не заданы. Задайте `DB_URL`, `R2DBC_URL`, `DB_USERNAME`, `DB_PASSWORD` или используйте `SPRING_PROFILES_ACTIVE=local`.

### Health check failed / pod не Ready

1. `kubectl logs` — ошибка Flyway или подключения.
2. Secret: `kubectl get secret reactive-demo-secret -n dev`.
3. Увеличьте `initialDelaySeconds` в `values-dev.yaml` (первый Flyway может занять 1–2 мин).

### `mapWrong.resolvedUsers` пустой в `/compare`

Это **ожидаемое** поведение demo-endpoint: антипаттерн `map` + `findById`. См. `docs/project-reactor-interview-guide.md`, раздел 6.

---

## 13. Файлы в репозитории

| Путь | Назначение |
|------|------------|
| `reactive-demo/` | исходники, Dockerfile, Gradle |
| `reactive-demo/src/main/resources/application*.yml` | конфигурация |
| `reactive-demo/src/main/resources/db/migration/` | Flyway |
| `app/src/main/resources/docker-greeting/` | локальный PostgreSQL (+ init `reactive_demo`) |
| `scripts/local-reactive-demo-db.md` | **локальная** БД reactive_demo (эта инструкция) |
| `scripts/create-reactive-demo-db.sh` | создать БД в local-postgres |
| `scripts/create-secrets.sh` | Secret для K8s (в т.ч. `reactive-demo-secret`) |
| `scripts/dev-db-connection/10-create-reactive-demo-db.sh` | БД на managed PG |
| `infra/terraform/database.tf` | Terraform: база `reactive_demo` |
| `infra/helm/reactive-demo/` | Helm chart |
| `infra/k8s/secret-template.yaml` | шаблон Secret |
| `ci/.gitlab-ci.yml` | pipeline |
| `docs/dev-remote-db-connection.md` | SSH-туннель |
| `docs/project-reactor-interview-guide.md` | теория + demo `/compare` |

---

## 14. Чек-листы

### Локальная разработка

- [ ] Docker: `docker compose up -d` в `docker-greeting`
- [ ] База `reactive_demo` существует (`create-reactive-demo-db.cmd` при необходимости)
- [ ] `SPRING_PROFILES_ACTIVE=local`
- [ ] `./gradlew bootRun` → http://localhost:8081/actuator/health

### Первый деплой в K8s (dev)

- [ ] `terraform apply` — база `reactive_demo` в managed PG
- [ ] `terraform output reactive_demo_jdbc_url` / `reactive_demo_r2dbc_url`
- [ ] kubeconfig настроен
- [ ] `bash scripts/create-secrets.sh` — Secret `reactive-demo-secret` в `dev`
- [ ] DNS: `reactive-demo-dev.cloud-terra.online`
- [ ] Push в `develop` → pipeline green → pod Running
- [ ] `curl http://reactive-demo-dev.cloud-terra.online/actuator/health`

### Prod

- [ ] Проверено в dev
- [ ] `deploy-prod-reactive-demo` (manual) на ветке `main`
- [ ] DNS: `reactive-demo.cloud-terra.online`

---

## Структура модуля

```
reactive-demo/
  Dockerfile
  build.gradle
  README.md                    ← эта инструкция
  src/main/java/.../
    controller/                UserController, ReactorDemoController
    service/                   UserService, ReactorDemoService
    repository/                R2DBC repositories
  src/main/resources/
    application.yml
    application-local.yml
    db/migration/
  src/test/...
```

Порт **8081** — не конфликтует с `app` (8080).
