# install-k3s-workers.sh — фаза 2: join k3s agent (§8.2)

Ставит **k3s agent** на `k8s-worker-1` и `k8s-worker-2`. Токен и IP master берёт из `infra-servers.env` — вручную вставлять `TOKEN_FROM_MASTER` не нужно.

## Среда запуска

| Среда | Подходит? |
|-------|-----------|
| **Git Bash** на Windows | **Да** |
| WSL | **Нет** — скрипт остановится |
| PowerShell / CMD | **Нет** |

Путь репозитория в Git Bash: `/d/Project_infra/greeting-service-infra`.

SSH-ключ: `~/.ssh/id_ed25519`.

Перед **ручными** ssh в этой же сессии всегда:

```bash

cd /d/Project_infra/greeting-service-infra
source <(tr -d '\r' < ./infra-servers.env)
```

`tr -d '\r'` нужен: файл env часто с Windows-переносами, обычный `source ./infra-servers.env` ломает переменные. Сам скрипт воркеров делает source сам.

## Файлы

| Файл | Где выполняется |
|------|-----------------|
| [`install-k3s-workers.sh`](install-k3s-workers.sh) | Git Bash на ПК → SSH |
| [`install-k3s-workers-remote.sh`](install-k3s-workers-remote.sh) | На каждом worker (root) |

## Быстрый старт (Git Bash)

Сначала master (пишет `K3S_TOKEN=` в env), затем воркеры:

```bash

cd /d/Project_infra/greeting-service-infra
source <(tr -d '\r' < ./infra-servers.env)
bash scripts/manual-deploy/k3s-master/install-k3s-master.sh --yes
bash scripts/manual-deploy/k3s-workers/install-k3s-workers.sh
```

Один воркер: `--only worker-1` или `--only worker-2`. Без установки: `--dry-run`. `--yes` убирает Enter на remote, но **не** убирает паузу «VPS ещё нет».

## Откуда берутся переменные

| Переменная | Зачем |
|------------|--------|
| `K8S_MASTER_IP` / `K3S_SERVER_IP` | `K3S_URL=https://<этот_IP>:6443` |
| `K3S_TOKEN` | строка из `/var/lib/rancher/k3s/server/node-token` на master |
| `K3S_WORKER_1_IP` | SSH + join worker-1 |
| `K3S_WORKER_2_IP` | SSH + join worker-2 |

`K3S_TOKEN` в env пишет `install-k3s-master.sh`. Если строки ещё нет — скрипт воркеров попробует файл `k3s-node-token` или скачает `/root/k3s-node-token` с master и допишет env.

## Что значит K3S_URL

Установщик `get.k3s.io` **без** `K3S_URL` ставит **server** (второй control-plane).  
**С** `K3S_URL` — ставит **agent** и регистрирует ноду на уже существующем master. Нам нужен agent.

`K3S_TOKEN` — секрет кластера. Без него master не примет worker.

## Если воркера ещё нет

Скрипт не падает. Если IP = `REPLACE_ME` или SSH не отвечает:

1. пишет, что VPS, возможно, не запущен / env не заполнен;
2. ждёт любую клавишу;
3. заново читает `infra-servers.env` и продолжает.

## Что делает remote на сервере

1. Поясняет, зачем `K3S_URL` и `K3S_TOKEN` (токен на экран не печатает).
2. `curl -sfL https://get.k3s.io | K3S_URL=... K3S_TOKEN=... K3S_NODE_NAME=k8s-worker-N sh -`
3. Проверка `systemctl is-active k3s-agent`.
4. `ufw allow 8472/udp` и `10250/tcp`, если ufw есть.

После обоих join — на master: `kubectl get nodes -o wide`. Ожидание: три ноды **Ready**.
