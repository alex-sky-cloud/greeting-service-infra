# prepare-server.sh — подготовка VPS (§7 гайда)

Автоматизирует шаги из раздела **«7. Подготовка серверов»**: `apt update/upgrade`, базовые пакеты, `hostname`, `ufw`, Docker (на devtools / traefik / storage).

Без аргумента роли скрипт сам обходит **все** серверы из `infra-servers.env`. IP вручную в команду вставлять не нужно.

## Среда запуска (важно)

| Среда | Подходит? |
|-------|-----------|
| **Git Bash** на Windows | **Да** — основной способ |
| WSL | **Нет** — скрипт завершится с подсказкой |
| PowerShell / CMD | **Нет** — откройте Git Bash |

Путь к репозиторию в Git Bash: `/d/Project_infra/greeting-service-infra`.

SSH-ключ: `~/.ssh/id_ed25519` (это `/c/Users/<ваш_user>/.ssh/id_ed25519`, **не** `/c/Users/$USER/...`).

Перед **ручными** ssh в этой же сессии:

```bash

cd /d/Project_infra/greeting-service-infra
source <(tr -d '\r' < ./infra-servers.env)
```

Скрипт сам читает env. Команда `source` нужна вам, если дальше набираете `ssh` руками. Обычный `source ./infra-servers.env` на Windows часто ломается из‑за `\r`.

## Файлы

| Файл | Где выполняется |
|------|-----------------|
| [`prepare-server.sh`](prepare-server.sh) | Git Bash на ПК → SSH на VPS |
| [`prepare-server-remote.sh`](prepare-server-remote.sh) | На сервере (передаётся по SSH автоматически) |

## Быстрый старт (Git Bash)

Все роли, у которых в env уже есть IP:

```bash

cd /d/Project_infra/greeting-service-infra
bash scripts/manual-deploy/prepare-server/prepare-server.sh
```

Одна роль:

```bash

bash scripts/manual-deploy/prepare-server/prepare-server.sh devtools
```

Явный IP (редко нужно):

```bash

bash scripts/manual-deploy/prepare-server/prepare-server.sh traefik-1 --host 203.0.113.10
```

## Как скрипт выбирает серверы

Порядок ролей:

| Роль | Переменная в infra-servers.env |
|------|--------------------------------|
| devtools | `DEVTOOLS_IP` |
| k8s-master | `K3S_SERVER_IP` или `K8S_MASTER_IP` |
| k8s-worker-1 | `K3S_WORKER_1_IP` |
| k8s-worker-2 | `K3S_WORKER_2_IP` |
| traefik-1 | `TRAEFIK_1_IP` или `TRAEFIK_ENTRY_IP` |
| traefik-2 | `TRAEFIK_2_IP` |
| storage-1 | `STORAGE_1_IP` |
| storage-2 | `STORAGE_2_IP` |

Готовность проверяет **SSH**, не ping. У многих VPS ICMP закрыт, а вход по ключу уже работает.

Если IP = `REPLACE_ME` или SSH не отвечает:

1. пишет, что VPS, возможно, не запущен / env не заполнен;
2. ждёт **Enter** (заново читает env) или **s + Enter** (пропустить роль);
3. идёт дальше по списку.

Если у двух ролей один и тот же IP (сейчас так у `DEVTOOLS_IP` и `K3S_SERVER_IP`), вторую роль **пропускает**. На одной машине нельзя задать два hostname.

## Что будет на экране

1. План: роль → IP или «ещё нет IP».
2. Для каждой роли: SSH `Команда` → вывод → OK.
3. На сервере: разделы 0–8, как раньше (паузы 3–25 с, Enter — раньше).
4. `--yes` убирает Enter **на remote**. Пауза «нет VPS» остаётся.

## Полезные опции

```bash

# Все заполненные серверы, без долгого upgrade
bash scripts/manual-deploy/prepare-server/prepare-server.sh --skip-upgrade

# Показать шаги без изменений
bash scripts/manual-deploy/prepare-server/prepare-server.sh --dry-run

# Одна k8s-нода с принудительным Docker (обычно не нужно)
bash scripts/manual-deploy/prepare-server/prepare-server.sh k8s-master --with-docker
```

## Удалили VPS и создали заново

**На вашем ПК ключ не пропадает** (`~/.ssh/id_ed25519` остаётся).

**На сервере — пропадает:** при удалении VPS стирается и `authorized_keys`. Ключ в аккаунте Timeweb остаётся, но **на новый сервер его нужно снова привязать**:

1. При **создании** VPS — выбрать тот же SSH-ключ в панели, **или**
2. После создания — добавить ключ к серверу в панели, **или**
3. Через веб-консоль Timeweb вставить `cat ~/.ssh/id_ed25519.pub` в `/root/.ssh/authorized_keys`.

Если SSH просит **пароль** — ключ на **этом** VPS не прописан. Новый `ssh-keygen` на ПК не нужен.

`ssh-keygen -R IP` — только сброс старого отпечатка **хоста** в `known_hosts`.

## После скрипта

- **devtools** → §10 Registry, §11 GitLab, §12 Runner.
- **k8s-master/worker** → §8 k3s.
- **traefik-*** → §9 Traefik в Docker.
- **storage-*** → §13 MinIO.
