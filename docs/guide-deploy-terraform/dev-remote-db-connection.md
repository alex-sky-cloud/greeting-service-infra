# Подключение к удалённой PostgreSQL из среды разработки



Инструкция для локального ПК (IntelliJ IDEA, DBeaver, psql) к managed PostgreSQL в Timeweb Cloud.



Скрипты автоматизации лежат в каталоге **`scripts/dev-db-connection/`**.



## 1. Архитектура доступа



База данных **не имеет публичного IP**. Она доступна только внутри VPC `10.10.0.0/24`.



| Ресурс | Пример значения | Доступ из интернета |

|--------|-----------------|---------------------|

| PostgreSQL (приватный) | `10.10.0.5:5432` | Нет |

| База | `greeting_db` | — |

| Пользователь приложения | `greeting_user` | — |

| Jump-хost devtools (SSH) | `72.56.249.137:22` | Да |

| Devtools (приватный) | `10.10.0.6` | Нет (только VPC) |

| Локальный порт туннеля | `15432` | localhost на вашем ПК |

| Схемы приложения | `iso_demo`, `shop_demo` | — |



> **Важно:** IP `10.10.0.5` и `72.56.249.137` — значения **на момент последнего `terraform apply`**.  

> После пересоздания инфраструктуры они могут измениться. Всегда сверяйте через скрипт `01-show-terraform-ips.sh`.



Приложение в Kubernetes подключается напрямую по `DB_URL` из Secret.  

Разработчик с ноутбука подключается **через SSH-туннель** на devtools.



Пароль БД — тот же, что задан в `TF_VAR_db_password` (Terraform) и в Secret `greeting-service-secret`.



## 1.1. Откуда берутся IP-адреса



Все адреса создаются **Terraform** в каталоге `infra/terraform/` и сохраняются в state.  

После `terraform apply` их можно прочитать через **outputs**.



| IP / сеть | Terraform-ресурс | Output / файл | Назначение |

|-----------|------------------|---------------|------------|

| `10.10.0.0/24` | `twc_vpc.main` | `variables.tf` → `vpc_subnet` | Приватная сеть VPC. K8s, PostgreSQL и devtools в одной сети. |

| `10.10.0.5` | `twc_database_cluster.postgres` | `db_host`, `db_jdbc_url` | **Приватный** IP managed PostgreSQL. JDBC для приложения в K8s. Снаружи VPC недоступен. |

| `5432` | `twc_database_cluster.postgres` | `db_port` | Порт PostgreSQL. |

| `72.56.249.137` | `twc_floating_ip.devtools` + `twc_server.devtools` | `devtools_public_ip` | **Публичный** floating IP VPS devtools. SSH, Docker Registry, jump-хост для туннеля. |

| `10.10.0.6` | `twc_server.devtools` → `local_network` | `devtools_private_ip` | **Приватный** IP devtools в VPC. Внутренний доступ из K8s/worker-нод. |

| `15432` | — | — | **Не из Terraform.** Локальный порт на ПК для `ssh -L`; выбираете свободный порт сами. |



### Как получить актуальные значения



**Через скрипт (Git Bash, из корня репозитория):**



```bash



bash scripts/dev-db-connection/01-show-terraform-ips.sh

```



Скрипт вызывает `terraform output` **через WSL Ubuntu** (как в основном гайде проекта) и печатает расшифровку адресов.



**Вручную через WSL:**



```bash



wsl -d Ubuntu

source ~/.bashrc

cd '/mnt/d/Project_infra/greeting-service-infra/infra/terraform'

terraform output -raw db_host

terraform output -raw db_port

terraform output -raw db_jdbc_url

terraform output -raw devtools_public_ip

terraform output -raw devtools_private_ip

```



**Откуда в коде:**



- PostgreSQL: `infra/terraform/database.tf` → `output "db_host"`, `outputs.tf` → `db_jdbc_url`

- Devtools: `infra/terraform/registry_server.tf` → `output "devtools_public_ip"`, `output "devtools_private_ip"`

- VPC: `infra/terraform/vpc.tf`



## 2. Предварительные требования



- Git Bash (или другой терминал с `ssh`)

- SSH-ключ `~/.ssh/id_ed25519` (добавлен в Terraform / Timeweb)

- Доступ по SSH к devtools (публичный IP из `devtools_public_ip`)

- Пароль БД (`TF_VAR_db_password`) — в `~/.bashrc` или в переменной окружения

- Клиент **`psql`** (см. раздел 2.1) — для проверки из консоли

- **WSL Ubuntu** — для чтения `terraform output` (скрипт `01-show-terraform-ips.sh`)



Проверка SSH (скрипт):



```bash



bash scripts/dev-db-connection/02-check-ssh.sh

```



## 2.1. Установка `psql` для Git Bash (Windows)



Git Bash **не содержит** `psql` по умолчанию. Нужны бинарники PostgreSQL для Windows.



### Вариант A — установщик EDB (рекомендуется)



1. Скачайте установщик: [PostgreSQL for Windows](https://www.postgresql.org/download/windows/) → **Download the installer**.

2. Запустите установщик. Можно снять галочку **PostgreSQL Server**, если локальный сервер не нужен — достаточно компонента **Command Line Tools**.

3. Запомните каталог установки, обычно:



```text



C:\Program Files\PostgreSQL\16\bin

```



4. Добавьте `psql` в PATH для Git Bash — в файл `~/.bashrc`:



```bash



export PATH="/c/Program Files/PostgreSQL/16/bin:$PATH"

```



5. Перезапустите Git Bash и проверьте:



```bash



psql --version

```



Ожидаемый вывод (версия может отличаться):



```text



psql (PostgreSQL) 16.x

```



> Если `psql: command not found`, проверьте путь к каталогу `bin` и номер версии (`15`, `16`, `17`).



### Вариант B — winget (PowerShell или cmd от администратора)



```powershell



winget install PostgreSQL.PostgreSQL.16 --accept-package-agreements --accept-source-agreements

```



После установки добавьте PATH в `~/.bashrc`, как в варианте A.



### Вариант C — Chocolatey (cmd от администратора)



```cmd



choco install postgresql16 -y

```



После установки добавьте PATH в `~/.bashrc`, как в варианте A.



### Проверка, что Git Bash видит `psql`



```bash



source ~/.bashrc

command -v psql

psql --version

```



## 3. Скрипты в `scripts/dev-db-connection/`



Все скрипты запускаются **из корня репозитория** в **Git Bash**.  

Перед запуском при необходимости: `source ~/.bashrc` (пароль БД).



| Скрипт | Назначение |

|--------|------------|

| `01-show-terraform-ips.sh` | Показать IP из `terraform output` и расшифровку |

| `02-check-ssh.sh` | Проверить SSH на devtools |

| `03-start-tunnel.sh` | Поднять SSH-туннель в фоне |

| `04-stop-tunnel.sh` | Остановить туннель |

| `05-check-tunnel-port.sh` | Проверить TCP на `localhost:15432` |

| `06-psql-test.sh` | SQL-запрос через `psql` (нужен туннель) |

| `07-verify-all.sh` | Полный цикл: IP → SSH → туннель → psql → stop |

| `08-clean-remote-database.sh` | **Очистка удалённой БД** (DROP iso_demo, shop_demo) |

| `lib.sh` | Общие функции (не запускать напрямую) |



### Типовой порядок (ручной)



```bash



bash scripts/dev-db-connection/01-show-terraform-ips.sh

bash scripts/dev-db-connection/02-check-ssh.sh

bash scripts/dev-db-connection/03-start-tunnel.sh

bash scripts/dev-db-connection/05-check-tunnel-port.sh

source ~/.bashrc

bash scripts/dev-db-connection/06-psql-test.sh

bash scripts/dev-db-connection/04-stop-tunnel.sh

```



### Полная автоматическая проверка



```bash



bash scripts/dev-db-connection/07-verify-all.sh

```



Старый путь (обратная совместимость):



```bash



bash scripts/verify-db-tunnel-gitbash.sh

```



## 4. Способ A — SSH-туннель в Git Bash + клиент на localhost



Подходит для IDEA, DBeaver, pgAdmin, `psql`.



### 4.1. Поднять туннель



Рекомендуется скрипт:



```bash



bash scripts/dev-db-connection/03-start-tunnel.sh

```



Или вручную (подставьте актуальные IP из `01-show-terraform-ips.sh`):



```bash



ssh -i ~/.ssh/id_ed25519 \

  -L 15432:10.10.0.5:5432 \

  root@72.56.249.137

```



Окно терминала **не закрывайте**, если туннель поднят в foreground.



Остановить фоновый туннель:



```bash



bash scripts/dev-db-connection/04-stop-tunnel.sh

```



### 4.2. Настройки в IntelliJ IDEA



**Database → Data Source → PostgreSQL**, вкладка **General**:



| Поле | Значение |

|------|----------|

| Host | `localhost` |

| Port | `15432` |

| Database | **`greeting_db`** ← не `postgres` |

| User | `greeting_user` |

| Password | значение `TF_VAR_db_password` |



Вкладка **Advanced**:

| Поле | Значение |
|------|----------|
| **Maintenance database** | **`greeting_db`** |

> IDEA может подключаться к служебной БД `postgres` по умолчанию.  
> У `greeting_user` нет `CONNECT` на `postgres` — только на `greeting_db`.  
> Ошибка: `FATAL: permission denied for database "postgres"`.

JDBC URL:

```text

jdbc:postgresql://localhost:15432/greeting_db
```



Вкладку **SSH/SSL** оставьте выключенной — туннель уже поднят в терминале.



**Test Connection** → **OK**.



Полезные схемы в обозревателе: `iso_demo`, `shop_demo`.  

Таблица истории миграций Flyway: `iso_demo.flyway_schema_history`.



### 4.3. Проверка через `psql` (Git Bash)



**Порядок:** туннель (`03-start-tunnel.sh`) → `06-psql-test.sh`.



```bash



source ~/.bashrc

bash scripts/dev-db-connection/03-start-tunnel.sh

bash scripts/dev-db-connection/06-psql-test.sh

```



Ожидаемый результат запроса:



```text



    db     |      usr

-----------+---------------

 greeting_db | greeting_user

(1 row)

```



### 4.4. Проверка порта туннеля (без psql)



```bash



bash scripts/dev-db-connection/05-check-tunnel-port.sh

```



## 5. Способ B — встроенный SSH-туннель в IDEA (без отдельного терминала)



**Database → Data Source → PostgreSQL**.



Вкладка **SSH/SSL** → **Use SSH tunnel** (IP из `devtools_public_ip`):



| Поле | Значение |

|------|----------|

| Host | `72.56.249.137` |

| Port | `22` |

| User name | `root` |

| Auth type | Key pair |

| Private key | `C:\Users\<user>\.ssh\id_ed25519` |



Вкладка **General** (IP из `db_host`):



| Поле | Значение |

|------|----------|

| Host | `10.10.0.5` |

| Port | `5432` |

| Database | `greeting_db` |

| User | `greeting_user` |

| Password | `TF_VAR_db_password` |



**Test Connection** → **OK**.



## 6. Частые ошибки



| Симптом | Причина | Решение |

|---------|---------|---------|

| Connection timed out на `10.10.0.5` | Прямое подключение без туннеля | Способ A или B |

| `schema "greeting_user" does not exist` | Путаница login и schema | Схемы: `iso_demo`, `shop_demo`; login: `greeting_user` |

| SSH: Permission denied | Неверный ключ или devtools недоступен | `02-check-ssh.sh` |

| Tunnel ok, но auth failed | Неверный пароль | `TF_VAR_db_password` / K8s Secret |

| `permission denied for database "postgres"` | IDEA подключается к `postgres`, не к `greeting_db` | Database и Maintenance database = `greeting_db` (§4.2) |

| `psql: command not found` | Клиент не установлен | Раздел 2.1 |

| Устаревший IP в командах | Terraform пересоздал ресурсы | `01-show-terraform-ips.sh` |



Просмотр пароля из Secret (если настроен `kubectl`):



```bash



kubectl get secret greeting-service-secret -n dev \

  -o jsonpath='{.data.DB_PASSWORD}' | base64 -d

echo

```


