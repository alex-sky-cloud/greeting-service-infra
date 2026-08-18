# Часть VIII: Два VPS (devtools + Kubernetes), namespace dev/prod

*GitLab · Runner · k3s Kubernetes · Helm · Self-hosted PostgreSQL*

Версия: 4.0 | 2026-08 | Целевая аудитория: backend developer middle+

## Оглавление

1. [Итоговая архитектура и список серверов](#раздел-1)
2. [Плавающий IP: что это и меняется ли он](#раздел-2)
3. [Заказ виртуальных серверов — точные параметры](#раздел-3)
4. [Подготовка серверов: базовые пакеты](#раздел-4)
5. [Установка k3s (single-node)](#раздел-5)
6. [Установка NGINX Ingress Controller](#раздел-6)
7. [Получение kubeconfig](#раздел-7)
8. [Docker Registry на devtools-сервере](#раздел-8)
9. [GitLab CE на devtools-сервере](#раздел-9)
10. [GitLab Runner (self-hosted)](#раздел-10)
11. [Namespace dev и prod](#раздел-11)
12. [PostgreSQL — StatefulSet, отдельно для dev и prod](#раздел-12)
13. [Kubernetes Secrets](#раздел-13)
14. [Сборка и push Docker-образа](#раздел-14)
15. [Деплой через Helm в dev и prod](#раздел-15)
16. [DNS и проверка через Ingress](#раздел-16)
17. [Типичные ошибки](#раздел-17)
18. [Итоговый порядок первого запуска](#раздел-18)
19. [Перенос на другого провайдера](#раздел-19)

---

<a id="раздел-1"></a>
## Раздел 1. Итоговая архитектура и список серверов

- **devtools** — GitLab CE, Docker Registry, GitLab Runner.
- **k8s-node** — k3s **single-node** кластер (**control-plane** и **worker** в одном лице), внутри которого через два namespace (`dev` и `prod`) разворачиваются приложение и PostgreSQL.

Итого — **два** сервера. 
 - Отдельных **worker-нод** не заводится: 
   - _разделение окружений_ делается **namespace** внутри **одного кластера**, а не количеством серверов.

---

<a id="раздел-2"></a>
## Раздел 2. Плавающий IP: что это и меняется ли он

**Плавающий IP** (floating IP) — статичный публичный адрес, который сам по себе **не меняется**.

- Его можно **вручную** перепривязать от одного сервера к другому, не меняя сам адрес:

> «Плавающий (выделенный) IP — это IPv4-адрес, который можно привязать к любому устройству в пределах одной приватной подсети.»

- Источник: [https://reg.cloud/support/cloud/oblachnyye-servery/seti/plavayushchiye-publichnyye-ip-adresa](https://reg.cloud/support/cloud/oblachnyye-servery/seti/plavayushchiye-publichnyye-ip-adresa)

> "A Floating IP is a public, static IP address that can be dynamically reassigned to multiple devices on your network."

**RU**:

 - «Плавающий IP — это публичный статический IP-адрес, который можно динамически переназначать между разными устройствами в сети.»

- Источник: [https://us.ovhcloud.com/public-cloud/floating-ip/](https://us.ovhcloud.com/public-cloud/floating-ip/)

  - Адрес закреплён за вашим аккаунтом, а не за конкретным сервером.
  - Не меняется автоматически при перезапуске или пере-сборке сервера.
  - При замене сервера DNS-запись менять не нужно — достаточно пере-привязать floating IP к новому серверу.

---

<a id="раздел-3"></a>
## Раздел 3. Заказ виртуальных серверов — точные параметры

| Сервер | vCPU | RAM | Диск | Роль |
|---|---|---|---|---|
| `devtools` | 4 | 8 ГБ | 100 ГБ SSD | GitLab CE + Docker Registry + GitLab Runner |
| `k8s-node` | 2 | 4 ГБ | 50 ГБ SSD | k3s single-node (namespace dev + prod) |

1. Авторизуйтесь в панели управления провайдера.
2. **Новый ресурс → Сервер**.
3. ОС: **Ubuntu 22.04 LTS**.
4. Конфигурация — по таблице выше.
5. Добавьте публичный SSH-ключ (`~/.ssh/id_ed25519.pub`). (https://cloud.reg.ru/panel/settings)(делается один раз для обоих серверов)
6. Повторите для обоих серверов.

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
K8S_NODE_IP=<k8s_node_public_ip>
EOF
```

---

<a id="раздел-4"></a>
## Раздел 4. Подготовка серверов: базовые пакеты

На каждом из двух серверов:

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

На `devtools` также установите утилиту для htpasswd (понадобится для Registry):

```bash

apt-get install -y apache2-utils
```

---

<a id="раздел-5"></a>
## Раздел 5. Установка k3s (single-node)

```bash

ssh root@<K8S_NODE_IP>
```

```bash

curl -sfL https://get.k3s.io | sh -
```

```

[INFO]  Using v1.30.5+k3s1 as release
[INFO]  Installing k3s to /usr/local/bin/k3s
[INFO]  systemd: Starting k3s
```

Проверка:

```bash

sudo k3s kubectl get nodes
```

```

NAME       STATUS   ROLES                  AGE   VERSION
k8s-node   Ready    control-plane,master   45s   v1.30.5+k3s1
```

Одна нода несёт одновременно control-plane и все рабочие поды обоих namespace — отдельных worker-нод не требуется.

---

<a id="раздел-6"></a>
## Раздел 6. Установка NGINX Ingress Controller

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

Так как нода одна, для DNS всегда используется публичный (или плавающий) IP сервера `k8s-node` из Раздела 3.

---

<a id="раздел-7"></a>
## Раздел 7. Получение kubeconfig

```bash

sudo cat /etc/rancher/k3s/k3s.yaml
```

- Источник: [https://habr.com/ru/companies/slurm/articles/729480/](https://habr.com/ru/companies/slurm/articles/729480/)

```bash

mkdir -p ~/.kube
scp root@<K8S_NODE_IP>:/etc/rancher/k3s/k3s.yaml ~/.kube/selfhosted-greeting.yaml
sed -i 's/127.0.0.1/<K8S_NODE_IP>/' ~/.kube/selfhosted-greeting.yaml
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

<a id="раздел-8"></a>
## Раздел 8. Docker Registry на devtools-сервере

```bash

ssh root@<DEVTOOLS_IP>
```

```bash

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

Так как Registry находится на `devtools`, а k3s — на отдельном сервере `k8s-node`, нужно разрешить k3s подключаться к Registry по HTTP (Registry работает без TLS):

```bash

ssh root@<K8S_NODE_IP>
```

```bash

mkdir -p /etc/rancher/k3s
cat > /etc/rancher/k3s/registries.yaml << EOF
mirrors:
  "<DEVTOOLS_IP>:5000":
    endpoint:
      - "http://<DEVTOOLS_IP>:5000"
configs:
  "<DEVTOOLS_IP>:5000":
    tls:
      insecure_skip_verify: true
EOF
systemctl restart k3s
```

---

<a id="раздел-9"></a>
## Раздел 9. GitLab CE на devtools-сервере

```bash

ssh root@<DEVTOOLS_IP>
```

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

<a id="раздел-10"></a>
## Раздел 10. GitLab Runner (self-hosted)

Runner устанавливается на `devtools` — там же, где GitLab и Registry, а сборка Docker-образов идёт локально на этом сервере перед push в Registry.

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
  --tag-list "self-hosted" \
  --run-untagged="false" \
  --locked="false"
```

```bash

sudo gitlab-runner status
sudo gitlab-runner verify
```

Для того чтобы Runner мог выполнять `helm upgrade`/`kubectl apply` на кластере `k8s-node`, на `devtools` также нужно установить `kubectl` и `helm` и скопировать туда kubeconfig из Раздела 7:

```bash

curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
chmod +x kubectl
mv kubectl /usr/local/bin/
```

```bash

curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

```bash

mkdir -p /root/.kube
scp ~/.kube/selfhosted-greeting.yaml root@<DEVTOOLS_IP>:/root/.kube/config
```

---

<a id="раздел-11"></a>
## Раздел 11. Namespace dev и prod

Выполняется с локального ПК или прямо на `devtools` (там, где настроен `kubectl` с доступом к `k8s-node`):

```bash

kubectl create namespace dev
kubectl create namespace prod
```

```bash

kubectl get namespaces
```

```

NAME              STATUS   AGE
default           Active   10m
dev               Active   3s
ingress-nginx     Active   8m
kube-system       Active   10m
prod              Active   2s
```

---

<a id="раздел-12"></a>
## Раздел 12. PostgreSQL — StatefulSet, отдельно для dev и prod

Каждое окружение получает собственный экземпляр PostgreSQL в своём namespace на сервере `k8s-node` — это изолирует данные dev от prod внутри одного кластера.

`postgres-pvc.yaml` (для dev):

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
      storage: 10Gi
```

Для `prod` — тот же манифест с `namespace: prod` и, при необходимости, большим объёмом (например `storage: 20Gi`).

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
            storage: 10Gi
```

Для `prod` замените `namespace: dev` на `namespace: prod` в этом же файле (либо держите два отдельных файла `postgres-statefulset-dev.yaml` / `postgres-statefulset-prod.yaml`).

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

Применение для dev:

```bash

kubectl create secret generic postgres-secret \
  --namespace=dev \
  --from-literal=DB_PASSWORD="пароль-для-dev" \
  --dry-run=client -o yaml | kubectl apply -f -
```

```bash

kubectl apply -f postgres-pvc.yaml
kubectl apply -f postgres-statefulset.yaml
kubectl apply -f postgres-service.yaml
```

Применение для prod (те же файлы с `-n prod` / изменённым `namespace: prod` внутри YAML):

```bash

kubectl create secret generic postgres-secret \
  --namespace=prod \
  --from-literal=DB_PASSWORD="пароль-для-prod" \
  --dry-run=client -o yaml | kubectl apply -f -
```

Проверка:

```bash

kubectl get pods -n dev -l app=postgres
kubectl get pods -n prod -l app=postgres
```

```

NAME         READY   STATUS    RESTARTS   AGE
postgres-0   1/1     Running   0          32s
```

Права на CREATE SCHEMA (выполнить отдельно в каждом namespace):

```bash

kubectl exec -it postgres-0 -n dev -- psql -U greeting_user -d greeting_db -c "GRANT CREATE ON DATABASE greeting_db TO greeting_user;"
kubectl exec -it postgres-0 -n prod -- psql -U greeting_user -d greeting_db -c "GRANT CREATE ON DATABASE greeting_db TO greeting_user;"
```

Строки подключения:

```

dev:  jdbc:postgresql://postgres.dev.svc.cluster.local:5432/greeting_db
prod: jdbc:postgresql://postgres.prod.svc.cluster.local:5432/greeting_db
```

---

<a id="раздел-13"></a>
## Раздел 13. Kubernetes Secrets

Для dev:

```bash

kubectl create secret generic greeting-service-secret \
  --namespace=dev \
  --from-literal=DB_URL="jdbc:postgresql://postgres.dev.svc.cluster.local:5432/greeting_db" \
  --from-literal=DB_USERNAME="greeting_user" \
  --from-literal=DB_PASSWORD="пароль-для-dev" \
  --dry-run=client -o yaml | kubectl apply -f -
```

Для prod:

```bash

kubectl create secret generic greeting-service-secret \
  --namespace=prod \
  --from-literal=DB_URL="jdbc:postgresql://postgres.prod.svc.cluster.local:5432/greeting_db" \
  --from-literal=DB_USERNAME="greeting_user" \
  --from-literal=DB_PASSWORD="пароль-для-prod" \
  --dry-run=client -o yaml | kubectl apply -f -
```

Registry-credentials в обоих namespace (Registry находится на `devtools`, поэтому `docker-server` указывает на `DEVTOOLS_IP`):

```bash

for NS in dev prod; do
kubectl create secret docker-registry registry-credentials \
  --namespace="${NS}" \
  --docker-server="<DEVTOOLS_IP>:5000" \
  --docker-username="docker" \
  --docker-password="docker" \
  --dry-run=client -o yaml | kubectl apply -f -
done
```

Проверка:

```bash

kubectl get secrets -n dev
kubectl get secrets -n prod
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

Выполняется на сервере `devtools` (там установлен Docker) либо на локальном ПК, если он также настроен на push в Registry `devtools`.

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

Один и тот же образ используется и для dev, и для prod — окружения различаются только namespace, Secret и values-файлом Helm.

---

<a id="раздел-15"></a>
## Раздел 15. Деплой через Helm в dev и prod

Выполняется с `devtools` (kubectl/helm настроены на кластер `k8s-node`, см. Раздел 10) либо с локального ПК.

Dev:

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

Prod:

```bash

helm upgrade --install greeting-service infra/helm/greeting-service \
  --namespace prod \
  --create-namespace \
  -f infra/helm/greeting-service/values.yaml \
  -f infra/helm/greeting-service/values-prod.yaml \
  --set image.repository="${DEVTOOLS_IP}:5000/greeting-service" \
  --set image.tag="${IMAGE_TAG}" \
  --timeout 5m
```

```

Release "greeting-service" has been upgraded. Happy Helming!
STATUS: deployed
```

Проверка:

```bash

kubectl get pods -n dev
kubectl get pods -n prod
```

```

NAME                               READY   STATUS    RESTARTS   AGE
greeting-service-5678bf87cc-plrp2  1/1     Running   0          45s
postgres-0                         1/1     Running   0          10m
```

---

<a id="раздел-16"></a>
## Раздел 16. DNS и проверка через Ingress

Ingress Controller работает на сервере `k8s-node`, поэтому для DNS используется публичный (или плавающий) IP именно этого сервера, а не `devtools`.

DNS A-записи:

```

Тип   : A
Имя   : greeting-dev
Значение : <K8S_NODE_IP>
TTL   : 300

Тип   : A
Имя   : greeting
Значение : <K8S_NODE_IP>
TTL   : 300
```

Ingress разводит трафик по namespace через `host`-правило: `greeting-dev.<домен>` → namespace `dev`, `greeting.<домен>` → namespace `prod` (задаётся в `values-dev.yaml` / `values-prod.yaml` Helm chart).

Проверка dev:

```bash

curl -H "Host: greeting-dev.<ваш-домен>" http://<K8S_NODE_IP>/api/greeting
```

```

{"message":"Hello, World! Environment: dev, Version: manual-v1", ...}
```

Проверка prod:

```bash

curl -H "Host: greeting.<ваш-домен>" http://<K8S_NODE_IP>/api/greeting
```

```

{"message":"Hello, World! Environment: prod, Version: manual-v1", ...}
```

Проверка health для каждого окружения:

```bash

curl -H "Host: greeting-dev.<ваш-домен>" http://<K8S_NODE_IP>/actuator/health
curl -H "Host: greeting.<ваш-домен>" http://<K8S_NODE_IP>/actuator/health
```

```

{"status":"UP","components":{"db":{"status":"UP", ...}}}
```

---

<a id="раздел-17"></a>
## Раздел 17. Типичные ошибки

**1.** `docker push` → `http: server gave HTTP response to HTTPS client`.

- Причина: Docker ожидает HTTPS.
- Исправление: на сервере/ПК, откуда идёт push, в Docker добавить:

```json

{
  "insecure-registries": ["<DEVTOOLS_IP>:5000"]
}
```

**2.** Pod в `ImagePullBackOff` на `k8s-node`.

- Причина: k3s не настроен на insecure registry, указывающий на `DEVTOOLS_IP` (Раздел 8).
- Исправление: повторить создание `/etc/rancher/k3s/registries.yaml` на `k8s-node` и `systemctl restart k3s`.

**3.** GitLab Runner не может выполнить `kubectl apply`/`helm upgrade`.

- Причина: на `devtools` не настроен kubeconfig, либо он не скопирован из Раздела 7.
- Исправление: повторить `scp` kubeconfig в `/root/.kube/config` на `devtools` (Раздел 10).

**4.** `permission denied for database greeting_db` при Flyway-миграциях с `CREATE SCHEMA`.

- Причина: `greeting_user` не имеет прав `CREATE` в соответствующем namespace.
- Исправление: выполнить `GRANT CREATE ON DATABASE greeting_db TO greeting_user;` через `kubectl exec` именно в том namespace (`dev` или `prod`), где упала миграция.

**5.** Приложение в dev видит данные prod или наоборот.

- Причина: Secret `greeting-service-secret` в одном из namespace указывает на `postgres.<другой-namespace>.svc.cluster.local`.
- Исправление: проверить `DB_URL` через `kubectl get secret greeting-service-secret -n <namespace> -o jsonpath='{.data.DB_URL}' | base64 -d` и убедиться, что домен Service соответствует своему namespace.

**6.** `EXTERNAL-IP` у `ingress-nginx` — `<pending>` бесконечно.

- Причина: в самостоятельном k3s нет Cloud Controller Manager.
- Исправление: использовать публичный IP сервера `k8s-node` напрямую — эта нода единственная, поэтому Ingress Controller всегда физически на ней.

---

<a id="раздел-18"></a>
## Раздел 18. Итоговый порядок первого запуска

1. Заказать два сервера с параметрами из Раздела 3: `devtools` и `k8s-node`.
2. Проверить SSH-доступ к обоим.
3. Базовая подготовка пакетов на обоих серверах (Раздел 4).
4. Установить k3s single-node на `k8s-node` (Раздел 5).
5. Установить NGINX Ingress Controller (Раздел 6).
6. Скопировать kubeconfig на локальный ПК (Раздел 7).
7. Установить Docker Registry на `devtools`, настроить insecure registry на `k8s-node` (Раздел 8).
8. Установить GitLab CE на `devtools` (Раздел 9).
9. Зарегистрировать GitLab Runner на `devtools`, настроить kubectl/helm с доступом к `k8s-node` (Раздел 10).
10. Создать namespace `dev` и `prod` (Раздел 11).
11. Развернуть PostgreSQL StatefulSet в каждом namespace (Раздел 12).
12. Выдать `greeting_user` права `CREATE SCHEMA` в каждом namespace.
13. Создать Kubernetes Secrets для обоих namespace (Раздел 13).
14. Собрать и запушить Docker-образ приложения на `devtools` (Раздел 14).
15. Выполнить `helm upgrade --install` в `dev`, затем в `prod` (Раздел 15).
16. Настроить две DNS A-записи на IP сервера `k8s-node` (Раздел 16).
17. Финальная проверка обоих окружений: `curl` по `greeting-dev.<домен>` и `greeting.<домен>` → HTTP 200, `/actuator/health` → `"db":{"status":"UP"}` в каждом.

---

<a id="раздел-19"></a>
## Раздел 19. Перенос на другого провайдера

1. Заказать те же два сервера с Ubuntu 22.04 у нового провайдера, с теми же параметрами (Раздел 3).
2. Повторить установку k3s (Раздел 5), Ingress (Раздел 6), Docker Registry (Раздел 8), GitLab CE (Раздел 9), GitLab Runner (Раздел 10), PostgreSQL в обоих namespace (Раздел 12).
3. Обновить обе DNS A-записи на новый IP сервера `k8s-node`.
