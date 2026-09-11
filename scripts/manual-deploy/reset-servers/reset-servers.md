# reset-servers.sh — откат prepare-server + k3s

Возвращает VPS в состояние «как при первой установке ОС»: снимает всё, что ставили скрипты `prepare-server` и k3s. **SSH-ключи не удаляются.**

## Среда запуска

| Среда | Подходит? |
|-------|-----------|
| **Git Bash** на Windows | **Да** |
| WSL | **Нет** |
| PowerShell / CMD | **Нет** |

## Что снимается на сервере

| Компонент | Откуда ставился |
|-----------|-----------------|
| k3s server / agent | install-k3s-master / install-k3s-workers |
| ufw | prepare-server |
| git | prepare-server |
| docker.io | prepare-server (только devtools / traefik / storage) |
| hostname k8s-* / devtools / … | prepare-server → обратно через reverse DNS |

**Не трогаем:** `/root/.ssh/authorized_keys`, базовые пакеты, которые были до prepare (curl, wget и т.д.).

## Локально после сброса master

- удаляет `k3s-node-token` (если есть);
- очищает `K3S_TOKEN` в `infra-servers.env`.

## Быстрый старт

Все роли с IP в env:

```bash

cd /d/Project_infra/greeting-service-infra
bash scripts/manual-deploy/reset-servers/reset-servers.sh
```

Одна роль:

```bash

bash scripts/manual-deploy/reset-servers/reset-servers.sh k8s-master
bash scripts/manual-deploy/reset-servers/reset-servers.sh k8s-worker-1 --yes
```

Без изменений на сервере:

```bash

bash scripts/manual-deploy/reset-servers/reset-servers.sh --dry-run
```

## Выбор серверов

Тот же список ролей и переменных env, что у `prepare-server.sh`:

| Роль | Переменная |
|------|------------|
| devtools | `DEVTOOLS_IP` |
| k8s-master | `K3S_SERVER_IP` / `K8S_MASTER_IP` |
| k8s-worker-1 | `K3S_WORKER_1_IP` |
| k8s-worker-2 | `K3S_WORKER_2_IP` |
| traefik-1 | `TRAEFIK_1_IP` |
| traefik-2 | `TRAEFIK_2_IP` |
| storage-1/2 | `STORAGE_1_IP`, `STORAGE_2_IP` |

Если IP = `REPLACE_ME` — пауза: **Enter** (повтор) или **s** (пропустить роль).

## Файлы

| Файл | Где выполняется |
|------|-----------------|
| [`reset-servers.sh`](reset-servers.sh) | Git Bash на ПК → SSH |
| [`reset-servers-remote.sh`](reset-servers-remote.sh) | На VPS (root) |
