# probe-server-ids.sh

Перебор id серверов в диапазоне — найти VM, не попавшую в `GET /servers`.

## Запуск

```bash

cd /d/Project_infra/greeting-service-infra/infra/terraform-serverspace
./scripts/probe-server-ids.sh l44s1304940 l44s1304959
```

Диапазон задайте вокруг id из лога Terraform или `check-task.sh` (`server_id`).

```bash

Диапазон: l44s1304954 .. l44s1304957
FOUND l44s1304954 postgres-1 Active nics:2 HTTP:200
FOUND l44s1304955 gitlab-1 Active nics:2 HTTP:200
FOUND l44s1304956 k8s-control-plane-1 Active nics:2 HTTP:200
FOUND l44s1304957 k8s-apps-1 Active nics:2 HTTP:200

```

## Если нашли id

```bash

./scripts/delete-server.sh l44sXXXXXXXX
```

См. также **`find-stuck-server.md`**, **`delete-stuck-server.md`**.
