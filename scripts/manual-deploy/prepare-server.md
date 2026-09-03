# prepare-server.sh — подготовка VPS (§7 гайда)

Автоматизирует шаги из раздела **«7. Подготовка серверов»**: `apt update/upgrade`, базовые пакеты, `hostname`, `ufw`, Docker (на devtools / traefik / storage).

## Среда запуска (важно)

| Среда | Подходит? |
|-------|-----------|
| **Git Bash** на Windows | **Да** — основной способ |
| WSL | **Нет** — скрипт завершится с подсказкой |
| PowerShell / CMD | **Нет** — откройте Git Bash |

Путь к репозиторию в Git Bash: `/d/Project_infra/greeting-service-infra`.

SSH-ключ: `~/.ssh/id_ed25519` (это `/c/Users/<ваш_user>/.ssh/id_ed25519`, **не** `/c/Users/$USER/...`).

## Файлы

| Файл | Где выполняется |
|------|-----------------|
| [`prepare-server.sh`](prepare-server.sh) | Git Bash на ПК → SSH на VPS |
| [`prepare-server-remote.sh`](prepare-server-remote.sh) | На сервере (передаётся по SSH автоматически) |

## Быстрый старт (Git Bash)

```bash

cd /d/Project_infra/greeting-service-infra
bash scripts/manual-deploy/prepare-server.sh devtools
```

Без аргумента роли скрипт сразу выведет **ОШИБКА: не указана роль сервера** и пример команды.

`infra-servers.env` скрипт подхватывает сам (в том числе если файл сохранён с CRLF из Windows).

Перед первым SSH на новый хост:

```bash

source ./infra-servers.env
ssh-keygen -R "${DEVTOOLS_IP}"
ssh-keyscan -H "${DEVTOOLS_IP}" >> ~/.ssh/known_hosts
ssh -i ~/.ssh/id_ed25519 root@${DEVTOOLS_IP} "echo connected"
```

## Удалили VPS и создали заново

**На вашем ПК ключ не пропадает** (`~/.ssh/id_ed25519` остаётся).

**На сервере — пропадает:** при удалении VPS стирается и `authorized_keys`. Ключ в аккаунте Timeweb остаётся, но **на новый сервер его нужно снова привязать**:

1. При **создании** VPS — выбрать тот же SSH-ключ в панели, **или**
2. После создания — добавить ключ к серверу в панели, **или**
3. Через веб-консоль Timeweb вставить `cat ~/.ssh/id_ed25519.pub` в `/root/.ssh/authorized_keys`.

Если SSH просит **пароль** — ключ на **этом** VPS не прописан. Новый `ssh-keygen` на ПК не нужен.

`ssh-keygen -R IP` — только сброс старого отпечатка **хоста** в `known_hosts` (тот же IP, другой сервер). Ваш ключ не удаляет.

## Что будет на экране

1. **Git Bash** — SSH: `Команда:` → `вывод` → `Результат: OK`.
2. **На сервере** — для каждой проверки:
   - `--- Проверка:` — что проверяем;
   - `Команда:` — точная команда;
   - `----- вывод -----` — результат на экране;
   - `--- Результат: OK` или `FAIL`.
3. **Раздел 0** — исходная проверка **до** изменений.
4. **Разделы 1–7** — установка (сеть/DNS, apt, hostname, ufw, Docker).
5. **Раздел 8** — **итоговая проверка всех компонентов** (как в вашем списке команд).
6. **Паузы** — 3–25 с; **Enter** — продолжить раньше.

## Сеть и DNS (любой провайдер)

Скрипт сам ждёт интернет, прописывает DNS, отключает мёртвые зеркала apt, повторяет apt при сбое.

## Роли и IP

| Роль | Переменная в infra-servers.env |
|------|--------------------------------|
| devtools | `DEVTOOLS_IP` |
| k8s-master | `K3S_SERVER_IP` или `K8S_MASTER_IP` |
| k8s-worker-1 | `K3S_WORKER_1_IP` |
| k8s-worker-2 | `K3S_WORKER_2_IP` |
| traefik-1 | `TRAEFIK_1_IP` или `TRAEFIK_ENTRY_IP` |
| storage-1 | `STORAGE_1_IP` |

Явный IP: `--host 203.0.113.10`.

## Полезные опции

```bash

# Повторный прогон без долгого upgrade
bash scripts/manual-deploy/prepare-server.sh devtools --skip-upgrade --yes

# Показать шаги без изменений на сервере
bash scripts/manual-deploy/prepare-server.sh devtools --dry-run

# k8s-нода с принудительным Docker (обычно не нужно)
bash scripts/manual-deploy/prepare-server.sh k8s-master --with-docker
```

Git Bash, заполненный `infra-servers.env`, ключ `~/.ssh/id_ed25519`.

## После скрипта

- **devtools** → §10 Registry, §11 GitLab, §12 Runner.
- **k8s-master/worker** → §8 k3s.
- **traefik-*** → §9 Traefik в Docker.
- **storage-*** → §13 MinIO.
