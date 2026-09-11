# install-k3s-master.sh — фаза 2: k3s control-plane (§8.1)

Ставит **k3s server** на master: `--disable traefik`, проверка `kubectl get nodes`, пишет **K3S_TOKEN** в `infra-servers.env`, скачивает **kubeconfig** в `~/.kube/selfhosted-greeting.yaml`.

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

Обычный `source ./infra-servers.env` на Windows часто ломается из-за `\r`. Скрипт сам делает source — эта команда нужна вам, если дальше набираете `ssh ... root@${K8S_MASTER_IP}` руками.

## Файлы

| Файл | Где выполняется |
|------|-----------------|
| [`install-k3s-master.sh`](install-k3s-master.sh) | Git Bash на ПК → SSH |
| [`install-k3s-master-remote.sh`](install-k3s-master-remote.sh) | На master (root), передаётся по SSH |

## Быстрый старт (Git Bash)

```bash

cd /d/Project_infra/greeting-service-infra
source <(tr -d '\r' < ./infra-servers.env)
bash scripts/manual-deploy/k3s-master/install-k3s-master.sh
```

IP: `K8S_MASTER_IP`, иначе `K3S_SERVER_IP`, иначе `DEVTOOLS_IP` (один адрес — нормально, если так задумано).

Явный IP:

```bash

bash scripts/manual-deploy/k3s-master/install-k3s-master.sh --host 203.0.113.10
```

Повтор без пауз Enter: `--yes`. Без установки: `--dry-run`.

## Что делает remote на сервере

1. Пояснение фазы 2 (паузы, как в §7).
2. `curl -sfL https://get.k3s.io | INSTALL_K3S_EXEC="--disable traefik" sh -`
3. `kubectl get nodes` (если нет `kubectl` — `k3s kubectl get nodes`). Статус **Ready**, пока одна нода.
4. `cat /var/lib/rancher/k3s/server/node-token` — полный вывод на экран.
5. Запись `/root/k3s-node-token` на сервере.
6. `ufw allow 6443/tcp` (и 8472/udp, 10250/tcp если ufw есть).

## Куда попадают токен и kubeconfig

После успеха локальный скрипт:

1. копирует `/root/k3s-node-token` на ПК;
2. записывает строку `K3S_TOKEN=...` в `infra-servers.env`;
3. скачивает `/etc/rancher/k3s/k3s.yaml` → `~/.kube/selfhosted-greeting.yaml`;
4. заменяет `127.0.0.1` на IP master; при наличии `kubectl` — `kubectl get nodes`.

### Если kubeconfig ещё недоступен

Скрипт **не падает сам**. Ждёт ваш выбор:

| Ввод | Действие |
|------|----------|
| **Enter** | снова проверить master и скачать |
| **q** | прервать скрипт |

Вручную копировать токен в команду join **не нужно**. Дальше:

```bash

source <(tr -d '\r' < ./infra-servers.env)
bash scripts/manual-deploy/k3s-workers/install-k3s-workers.sh
```

Файл `k3s-node-token`, kubeconfig и `*.env` в `.gitignore` — **не коммитьте**.

После установки:

```bash

export KUBECONFIG=~/.kube/selfhosted-greeting.yaml
kubectl get nodes
```
