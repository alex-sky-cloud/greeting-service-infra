# Часть VI: Полностью самостоятельная инфраструктура — детальная версия

*GitLab · Runner · k3s Kubernetes · Helm · Self-hosted PostgreSQL*

Версия: 2.1 | 2026-08 | Целевая аудитория: backend developer middle+

## Оглавление

1. [Итоговая архитектура и список серверов](#раздел-1)
2. [Плавающий IP: что это и меняется ли он](#раздел-2)
3. [Заказ виртуальных серверов — точные параметры](#раздел-3)
4. [Подготовка серверов: базовые пакеты](#раздел-4)
5. [Установка k3s — master-нода](#раздел-5)
6. [Установка k3s — worker-ноды](#раздел-6)
7. [Установка NGINX Ingress Controller](#раздел-7)
8. [Получение kubeconfig](#раздел-8)
9. [Docker Registry на devtools-сервере](#раздел-9)
10. [GitLab CE на devtools-сервере](#раздел-10)
11. [GitLab Runner (self-hosted)](#раздел-11)
12. [PostgreSQL — StatefulSet в Kubernetes](#раздел-12)
13. [Kubernetes Secrets для приложения](#раздел-13)
14. [Сборка и push Docker-образа](#раздел-14)
15. [Первый деплой через Helm](#раздел-15)
16. [DNS и проверка через Ingress](#раздел-16)
17. [Типичные ошибки](#раздел-17)
18. [Итоговый порядок первого запуска](#раздел-18)
19. [Перенос на другого провайдера](#раздел-19)

---

<a id="раздел-1"></a>
## Раздел 1. Итоговая архитектура и список серверов

- **devtools** — 4 CPU / 8 ГБ RAM / 100 ГБ SSD, Ubuntu 22.04 — GitLab CE, Docker Registry, GitLab Runner.
- **k8s-master** — 2 CPU / 4 ГБ RAM / 50 ГБ SSD, Ubuntu 22.04 — control-plane k3s.
- **k8s-worker-1** — 2 CPU / 4 ГБ RAM / 50 ГБ SSD, Ubuntu 22.04 — worker-нода k3s.
- **k8s-worker-2** — 2 CPU / 4 ГБ RAM / 50 ГБ SSD, Ubuntu 22.04 — worker-нода k3s.

Итого: 10 vCPU / 20 ГБ RAM / 250 ГБ SSD на четырёх серверах.

---

<a id="раздел-2"></a>
## Раздел 2. Плавающий IP: что это и меняется ли он

**Плавающий IP** (floating IP) — статичный публичный адрес, который сам по себе **не меняется**.

- Его можно **вручную** перепривязать от одного сервера к другому, не меняя сам адрес:

> «Плавающий (выделенный) IP — это IPv4-адрес, который можно привязать к любому устройству в пределах одной приватной подсети.»

- Источник: [https://reg.cloud/support/cloud/oblachnyye-servery/seti/plavayushchiye-publichnyye-ip-adresa](https://reg.cloud/support/cloud/oblachnyye-servery/seti/plavayushchiye-publichnyye-ip-adresa)

> "A Floating IP is a public, static IP address that can be dynamically reassigned to multiple devices on your network."

**RU**: «Плавающий IP — это публичный статический IP-адрес, который можно динамически переназначать между разными устройствами в сети.»

- Источник: [https://us.ovhcloud.com/public-cloud/floating-ip/](https://us.ovhcloud.com/public-cloud/floating-ip/)

- Адрес закреплён за вашим аккаунтом, а не за конкретным сервером.
- Не меняется автоматически при перезапуске или пересборке сервера.
- При замене сервера DNS-запись менять не нужно — достаточно перепривязать floating IP к новому серверу.

---

<a id="раздел-3"></a>
## Раздел 3. Заказ виртуальных серверов — точные параметры

| Сервер | vCPU | RAM | Диск | Роль |
|---|---|---|---|---|
| `devtools` | 4 | 8 ГБ | 100 ГБ SSD | GitLab CE + Docker Registry + GitLab Runner |
| `k8s-master` | 2 | 4 ГБ | 50 ГБ SSD | Control-plane k3s |
| `k8s-worker-1` | 2 | 4 ГБ | 50 ГБ SSD | Worker-нода k3s |
| `k8s-worker-2` | 2 | 4 ГБ | 50 ГБ SSD | Worker-нода k3s |

1. Авторизуйтесь в панели управления провайдера.
2. **Новый ресурс → Сервер**.
3. ОС: **Ubuntu 22.04 LTS**.
4. Конфигурация — по таблице выше.
5. Добавьте публичный SSH-ключ (`~/.ssh/id_ed25519.pub`).
6. Повторите для всех четырёх серверов.

Проверка SSH:

```bash

ssh root@<SERVER_PUBLIC_IP> "echo connected"
```

```

connected
```

Сохранить IP-адреса:

```bash

cat > infra-servers.env << 'EOF'
DEVTOOLS_IP=<devtools_public_ip>
K8S_MASTER_IP=<k8s_master_public_ip>
K8S_WORKER_1_IP=<k8s_worker_1_public_ip>
K8S_WORKER_2_IP=<k8s_worker_2_public_ip>
EOF
```

---

<a id="раздел-4"></a>
## Раздел 4. Подготовка серверов: базовые пакеты

На каждом сервере:

```bash

ssh root@<SERVER_IP>
```

```bash

apt-get update
apt-get upgrade -y
apt-get install -y curl wget gnupg git ufw
```

На `devtools` дополнительно:

```bash

curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
docker --version
```

```

Docker version 27.x.x, build xxxxxxx
```

---

<a id="раздел-5"></a>
## Раздел 5. Установка k3s — master-нода

```bash

ssh root@<K8S_MASTER_IP>
```

```bash

curl -sfL https://get.k3s.io | sh -
```

```

[INFO]  Using v1.30.5+k3s1 as release
[INFO]  Downloading binary https://github.com/k3s-io/k3s/releases/download/v1.30.5+k3s1/k3s
[INFO]  Installing k3s to /usr/local/bin/k3s
[INFO]  systemd: Starting k3s
```

Проверка control-plane:

```bash

sudo k3s kubectl get nodes
```

```

NAME          STATUS   ROLES                  AGE   VERSION
k8s-master    Ready    control-plane,master   45s   v1.30.5+k3s1
```

Получение токена для worker-нод:

```bash

sudo cat /var/lib/rancher/k3s/server/node-token
```

```

K10a1b2c3d4e5f6...::server:1a2b3c4d5e6f7g8h9i0j
```

- Источник: [https://arenda-server.cloud/blog/kak-nastroit-klaster-k3s-kubernetes-na-ubuntu/](https://arenda-server.cloud/blog/kak-nastroit-klaster-k3s-kubernetes-na-ubuntu/)

---

<a id="раздел-6"></a>
## Раздел 6. Установка k3s — worker-ноды

На `k8s-worker-1`:

```bash

ssh root@<K8S_WORKER_1_IP>
```

```bash

curl -sfL https://get.k3s.io | K3S_URL=https://<K8S_MASTER_IP>:6443 K3S_TOKEN=<NODE_TOKEN> sh -
```

На `k8s-worker-2`:

```bash

ssh root@<K8S_WORKER_2_IP>
```

```bash

curl -sfL https://get.k3s.io | K3S_URL=https://<K8S_MASTER_IP>:6443 K3S_TOKEN=<NODE_TOKEN> sh -
```

- Источник: [https://arenda-server.cloud/blog/kak-nastroit-klaster-k3s-kubernetes-na-ubuntu/](https://arenda-server.cloud/blog/kak-nastroit-klaster-k3s-kubernetes-na-ubuntu/)

Проверка кластера на master-ноде:

```bash

sudo k3s kubectl get nodes -o wide
```

```

NAME             STATUS   ROLES                  AGE     VERSION         INTERNAL-IP
k8s-master       Ready    control-plane,master   5m12s   v1.30.5+k3s1    10.0.0.10
k8s-worker-1     Ready    <none>                 1m30s   v1.30.5+k3s1    10.0.0.11
k8s-worker-2     Ready    <none>                 1m5s    v1.30.5+k3s1    10.0.0.12
```

---

<a id="раздел-7"></a>
## Раздел 7. Установка NGINX Ingress Controller

```bash

curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

```bash

helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace
```

```bash

kubectl get pods -n ingress-nginx
```

```

NAME                                        READY   STATUS    RESTARTS   AGE
ingress-nginx-controller-7d6f8b9c4d-x8k2p   1/1     Running   0          45s
```

```bash

kubectl get svc -n ingress-nginx
```

Если `EXTERNAL-IP` — `<pending>`:

```bash

kubectl get pods -n ingress-nginx -o wide
```

```

NAME                                        READY   STATUS    NODE
ingress-nginx-controller-7d6f8b9c4d-x8k2p   1/1     Running   k8s-worker-1
```

Используйте публичный IP этой worker-ноды.

---

<a id="раздел-8"></a>
## Раздел 8. Получение kubeconfig

```bash

sudo cat /etc/rancher/k3s/k3s.yaml
```

- Источник: [https://habr.com/ru/companies/slurm/articles/729480/](https://habr.com/ru/companies/slurm/articles/729480/)

```bash

mkdir -p ~/.kube
scp root@<K8S_MASTER_IP>:/etc/rancher/k3s/k3s.yaml ~/.kube/selfhosted-greeting.yaml
sed -i 's/127.0.0.1/<K8S_MASTER_IP>/' ~/.kube/selfhosted-greeting.yaml
chmod 600 ~/.kube/selfhosted-greeting.yaml
```

```bash

export KUBECONFIG=~/.kube/selfhosted-greeting.yaml
kubectl get nodes
```

```bash

echo 'export KUBECONFIG="$HOME/.kube/selfhosted-greeting.yaml"' >> ~/.bashrc
source ~/.bashrc
```

---

<a id="раздел-9"></a>
## Раздел 9. Docker Registry на devtools-сервере

```bash

ssh root@<DEVTOOLS_IP>
```

```bash

apt-get install -y apache2-utils
mkdir -p /opt/registry/data /opt/registry/config /opt/registry/auth
htpasswd -Bbn docker docker > /opt/registry/auth/htpasswd
```

```bash

docker run -d \
  --name registry \
  --restart=always \
  -p 5000:5000 \
  -v /opt/registry/data:/var/lib/registry \
  -v /opt/registry/auth:/auth \
  -e "REGISTRY_AUTH=htpasswd" \
  -e "REGISTRY_AUTH_HTPASSWD_REALM=Registry Realm" \
  -e "REGISTRY_AUTH_HTPASSWD_PATH=/auth/htpasswd" \
  registry:2
```

```bash

curl -u docker:docker http://localhost:5000/v2/
```

```

{}
```

---

<a id="раздел-10"></a>
## Раздел 10. GitLab CE на devtools-сервере

```bash

curl -fsSL https://packages.gitlab.com/install/repositories/gitlab/gitlab-ce/script.deb.sh | sudo bash
sudo EXTERNAL_URL="http://<DEVTOOLS_IP>" apt-get install -y gitlab-ce
```

```bash

sudo gitlab-ctl status
```

```bash

sudo cat /etc/gitlab/initial_root_password
```

Войдите в `http://<DEVTOOLS_IP>` под `root`, смените пароль: **Avatar → Edit profile → Password**.

---

<a id="раздел-11"></a>
## Раздел 11. GitLab Runner (self-hosted)

```bash

curl -L "https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh" | sudo bash
sudo apt-get install -y gitlab-runner
```

```bash

sudo gitlab-runner register \
  --non-interactive \
  --url "http://<DEVTOOLS_IP>/" \
  --token "<runner-authentication-token>" \
  --executor "shell" \
  --description "devtools-runner" \
  --tag-list "self-hosted,devtools" \
  --run-untagged="false" \
  --locked="false"
```

```bash

sudo gitlab-runner status
sudo gitlab-runner verify
```

---

<a id="раздел-12"></a>
## Раздел 12. PostgreSQL — StatefulSet в Kubernetes

`postgres-pvc.yaml`:

```yaml

apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
  namespace: dev
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 20Gi
```

`postgres-statefulset.yaml`:

```yaml

apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: dev
spec:
  serviceName: postgres
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:16-alpine
          env:
            - name: POSTGRES_DB
              value: "greeting_db"
            - name: POSTGRES_USER
              value: "greeting_user"
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgres-secret
                  key: DB_PASSWORD
          ports:
            - containerPort: 5432
          volumeMounts:
            - name: postgres-storage
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
    - metadata:
        name: postgres-storage
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 20Gi
```

`postgres-service.yaml`:

```yaml

apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: dev
spec:
  selector:
    app: postgres
  ports:
    - port: 5432
      targetPort: 5432
  clusterIP: None
```

```bash

kubectl create namespace dev
```

```bash

kubectl create secret generic postgres-secret \
  --namespace=dev \
  --from-literal=DB_PASSWORD="ваш-надёжный-пароль" \
  --dry-run=client -o yaml | kubectl apply -f -
```

```bash

kubectl apply -f postgres-pvc.yaml
kubectl apply -f postgres-statefulset.yaml
kubectl apply -f postgres-service.yaml
```

```bash

kubectl get pods -n dev -l app=postgres
```

```

NAME         READY   STATUS    RESTARTS   AGE
postgres-0   1/1     Running   0          32s
```

Права на CREATE SCHEMA:

```bash

kubectl exec -it postgres-0 -n dev -- psql -U greeting_user -d greeting_db -c "GRANT CREATE ON DATABASE greeting_db TO greeting_user;"
```

Строка подключения:

```

jdbc:postgresql://postgres.dev.svc.cluster.local:5432/greeting_db
```

---

<a id="раздел-13"></a>
## Раздел 13. Kubernetes Secrets для приложения

```bash

DB_HOST="postgres.dev.svc.cluster.local"
DB_PORT="5432"
```

```bash

kubectl create secret generic greeting-service-secret \
  --namespace=dev \
  --from-literal=DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/greeting_db" \
  --from-literal=DB_USERNAME="greeting_user" \
  --from-literal=DB_PASSWORD="ваш-надёжный-пароль" \
  --dry-run=client -o yaml | kubectl apply -f -
```

```bash

kubectl create secret docker-registry registry-credentials \
  --namespace=dev \
  --docker-server="<DEVTOOLS_IP>:5000" \
  --docker-username="docker" \
  --docker-password="docker" \
  --dry-run=client -o yaml | kubectl apply -f -
```

```bash

kubectl get secrets -n dev
```

```

NAME                       TYPE                             DATA   AGE
greeting-service-secret    Opaque                           3      10s
postgres-secret            Opaque                           1      2m
registry-credentials       kubernetes.io/dockerconfigjson   1      5s
```

---

<a id="раздел-14"></a>
## Раздел 14. Сборка и push Docker-образа

```bash

DEVTOOLS_IP=<ваш IP devtools-сервера>
IMAGE_TAG=manual-v1
```

```bash

echo docker | docker login ${DEVTOOLS_IP}:5000 -u docker --password-stdin
```

```bash

cd app
docker build -t ${DEVTOOLS_IP}:5000/greeting-service:${IMAGE_TAG} .
docker push ${DEVTOOLS_IP}:5000/greeting-service:${IMAGE_TAG}
```

```bash

curl -u docker:docker http://${DEVTOOLS_IP}:5000/v2/greeting-service/tags/list
```

```

{"name":"greeting-service","tags":["manual-v1"]}
```

Insecure registry на worker-нодах:

```bash

ssh root@<K8S_WORKER_1_IP>
```

```bash

mkdir -p /etc/rancher/k3s
cat > /etc/rancher/k3s/registries.yaml << EOF
mirrors:
  "${DEVTOOLS_IP}:5000":
    endpoint:
      - "http://${DEVTOOLS_IP}:5000"
configs:
  "${DEVTOOLS_IP}:5000":
    tls:
      insecure_skip_verify: true
EOF
systemctl restart k3s-agent
```

Повторить на `k8s-worker-2`.

---

<a id="раздел-15"></a>
## Раздел 15. Первый деплой через Helm

```bash

helm lint infra/helm/greeting-service
```

```bash

helm upgrade --install greeting-service infra/helm/greeting-service \
  --namespace dev \
  --create-namespace \
  -f infra/helm/greeting-service/values.yaml \
  -f infra/helm/greeting-service/values-dev.yaml \
  --set image.repository="${DEVTOOLS_IP}:5000/greeting-service" \
  --set image.tag="${IMAGE_TAG}" \
  --timeout 5m
```

```

Release "greeting-service" has been upgraded. Happy Helming!
NAME: greeting-service
STATUS: deployed
```

```bash

kubectl get pods -n dev
```

```

NAME                               READY   STATUS    RESTARTS   AGE
greeting-service-5678bf87cc-plrp2  1/1     Running   0          45s
postgres-0                         1/1     Running   0          10m
```

---

<a id="раздел-16"></a>
## Раздел 16. DNS и проверка через Ingress

```bash

kubectl get pods -n ingress-nginx -o wide
```

DNS A-запись:

```

Тип   : A
Имя   : greeting-dev
Значение : <IP worker-ноды с Ingress Controller>
TTL   : 300
```

```bash

curl -H "Host: greeting-dev.<ваш-домен>" http://<IP worker-ноды>/api/greeting
```

```

{"message":"Hello, World! Environment: dev, Version: manual-v1", ...}
```

```bash

curl -H "Host: greeting-dev.<ваш-домен>" http://<IP worker-ноды>/actuator/health
```

```

{"status":"UP","components":{"db":{"status":"UP", ...}}}
```

---

<a id="раздел-17"></a>
## Раздел 17. Типичные ошибки

**1.** Worker-нода не появляется в `kubectl get nodes`.

- Причина: неверный токен или IP master-ноды.
- Исправление: заново `sudo cat /var/lib/rancher/k3s/server/node-token` на master и повторить установку agent.

**2.** `docker push` → `http: server gave HTTP response to HTTPS client`.

- Причина: Docker ожидает HTTPS.
- Исправление: Docker Desktop → Settings → Docker Engine:

```json

{
  "insecure-registries": ["<DEVTOOLS_IP>:5000"]
}
```

**3.** Pod в `ImagePullBackOff` на worker-ноде.

- Причина: не настроен `registries.yaml` на этой worker-ноде.
- Исправление: повторить настройку из Раздела 14 и перезапустить `k3s-agent`.

**4.** `permission denied for database greeting_db` при Flyway-миграциях с `CREATE SCHEMA`.

- Причина: `greeting_user` не имеет прав `CREATE`.
- Исправление:

```bash

kubectl exec -it postgres-0 -n dev -- psql -U greeting_user -d greeting_db -c "GRANT CREATE ON DATABASE greeting_db TO greeting_user;"
```

**5.** `EXTERNAL-IP` у `ingress-nginx` — `<pending>` бесконечно.

- Причина: в самостоятельном k3s нет Cloud Controller Manager.
- Исправление: использовать IP worker-ноды с подом `ingress-nginx-controller` (Раздел 7).

---

<a id="раздел-18"></a>
## Раздел 18. Итоговый порядок первого запуска

1. Заказать четыре сервера с параметрами из Раздела 3.
2. Проверить SSH-доступ.
3. Базовая подготовка пакетов (Раздел 4).
4. Установить k3s server на `k8s-master`, получить токен (Раздел 5).
5. Установить k3s agent на `k8s-worker-1` и `k8s-worker-2` (Раздел 6).
6. Установить NGINX Ingress Controller (Раздел 7).
7. Скопировать kubeconfig (Раздел 8).
8. Установить Docker Registry на `devtools` (Раздел 9).
9. Установить GitLab CE на `devtools` (Раздел 10).
10. Зарегистрировать GitLab Runner (Раздел 11).
11. Развернуть PostgreSQL StatefulSet (Раздел 12).
12. Выдать права `CREATE SCHEMA`.
13. Создать Kubernetes Secrets (Раздел 13).
14. Собрать и запушить Docker-образ, настроить insecure registry (Раздел 14).
15. Выполнить `helm upgrade --install` (Раздел 15).
16. Настроить DNS A-запись (Раздел 16).
17. Финальная проверка: `curl http://greeting-dev.<домен>/api/greeting` → HTTP 200, `/actuator/health` → `"db":{"status":"UP"}`.

---

<a id="раздел-19"></a>
## Раздел 19. Перенос на другого провайдера

1. Заказать те же четыре сервера с Ubuntu 22.04 у нового провайдера, с теми же параметрами (Раздел 3).
2. Повторить установку k3s (Разделы 5–6), Ingress (Раздел 7), Docker Registry, GitLab CE, GitLab Runner (Разделы 9–11), PostgreSQL (Раздел 12).
3. Обновить DNS A-запись на новый IP.
