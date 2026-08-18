# Часть V: Полностью самостоятельная инфраструктура, без привязки к провайдеру

*GitLab · Runner · k3s Kubernetes · Helm · Self-hosted PostgreSQL — всё вручную, переносимо на любой VPS-провайдер*

Версия: 1.0 | 2026-08 | Целевая аудитория: backend developer middle+

Все описания — на русском языке. Технические термины — на английском. Команды — на английском.

> **Что изменилось в этой версии.** Отвечаю сначала на прямой вопрос про плавающий IP (Раздел 0), а затем полностью убираю последнюю зависимость от managed-сервисов провайдера: **Kubernetes-кластер теперь тоже разворачивается вручную** (через `k3s`) на голых виртуальных серверах, а не заказывается как готовая услуга «Кластеры Kubernetes» у конкретного облака. В сочетании с самостоятельным PostgreSQL (уже сделано в предыдущей версии) итоговая архитектура состоит **только** из обычных виртуальных серверов (VPS) с Ubuntu — единственное, что вы заказываете у провайдера. Всё остальное — GitLab, Docker Registry, Kubernetes, PostgreSQL — вы разворачиваете сами, одинаковыми командами независимо от того, у кого арендованы серверы.

## Оглавление

1. [Что такое плавающий (floating) IP и меняется ли он](#раздел-0)
2. [Итоговая архитектура: список того, что вообще нужно у провайдера](#раздел-1)
3. [Заказ виртуальных серверов](#раздел-2)
4. [Плавающий IP: заказ и привязка](#раздел-3)
5. [Самостоятельный Kubernetes-кластер через k3s](#раздел-4)
6. [Получение kubeconfig без панели провайдера](#раздел-5)
7. [Самостоятельный PostgreSQL](#раздел-6)
8. [GitLab, Docker Registry, GitLab Runner](#раздел-7)
9. [Сеть между серверами: приватная сеть или публичные IP](#раздел-8)
10. [Что осталось от исходного документа без изменений](#раздел-9)
11. [Итоговый порядок первого запуска](#раздел-10)
12. [Как перенести всё на другого провайдера](#раздел-11)

---

<a id="раздел-0"></a>
## Раздел 0. Что такое плавающий (floating) IP и меняется ли он

Плавающий IP (floating IP) — это **статичный публичный адрес**, который сам по себе **не меняется**. Его особенность не в том, что он «плавает» во времени сам, а в том, что вы можете **вручную** перепривязать его от одного сервера к другому, не меняя сам IP-адрес:

> «Плавающий (выделенный) IP — это IPv4-адрес, который можно привязать к любому устройству в пределах одной приватной подсети.»

- Источник: [https://reg.cloud/support/cloud/oblachnyye-servery/seti/plavayushchiye-publichnyye-ip-adresa](https://reg.cloud/support/cloud/oblachnyye-servery/seti/plavayushchiye-publichnyye-ip-adresa)

То же самое подтверждается и в общей терминологии облачных провайдеров (OpenStack, на котором построено, например, Рег.облако):

> "A Floating IP is a public, static IP address that can be dynamically reassigned to multiple devices on your network, facilitating high availability, fault tolerance, and failover for your applications and services in the cloud... A floating IP is a static public IP that can be reassigned to a networked device without needing to change the device's location."

RU: «Плавающий IP — это публичный, статический IP-адрес, который можно динамически переназначать между разными устройствами в вашей сети, что обеспечивает высокую доступность, отказоустойчивость и failover для приложений и сервисов в облаке... Плавающий IP — это статический публичный адрес, который можно переназначить сетевому устройству без необходимости менять местоположение самого устройства.»

- Источник: [https://us.ovhcloud.com/public-cloud/floating-ip/](https://us.ovhcloud.com/public-cloud/floating-ip/)

### Практический смысл для вашего проекта

- Адрес **закреплён за вашим аккаунтом**, а не за конкретным сервером — пока вы сами не удалите ресурс «Плавающий IP», он остаётся вашим и не отдаётся другому клиенту.
- Он **не меняется автоматически** при перезапуске или пересборке сервера — в этом и есть его ценность по сравнению с обычным публичным IP, который у многих провайдеров может смениться после пересоздания VPS.
- Вы можете **вручную** отвязать его от одного сервера и привязать к другому — например, если старый devtools-сервер вышел из строя, вы поднимаете новый и мгновенно переключаете на него тот же самый внешний IP, без необходимости менять DNS-записи или конфигурацию клиентов.

Порядок действий в Рег.облаке:

> «Чтобы заказать новый IP: 1. Войдите в панель управления Рег.облака. 2. Перейдите в раздел Мои ресурсы → Плавающие IP. 3. Кликните Новый ресурс и выберите Плавающий IP. 4. Выберите город размещения IP и нажмите Добавить плавающий IP... Чтобы привязать IP-адрес: ...выберите Привязать к серверу.»

- Источник: [https://reg.cloud/support/cloud/oblachnyye-servery/seti/plavayushchiye-publichnyye-ip-adresa](https://reg.cloud/support/cloud/oblachnyye-servery/seti/plavayushchiye-publichnyye-ip-adresa)

Похожая логика (плавающий IP как ресурс, который явно берётся из пула и привязывается) действует в OpenStack-облаках в целом:

> «Плавающие IP-адреса не назначаются виртуальным машинам по умолчанию. Пользователи облака должны явным образом "взять" их из пула, настроенного администратором облака.»

- Источник: [https://habr.com/ru/companies/mirantis_openstack/articles/183264/](https://habr.com/ru/companies/mirantis_openstack/articles/183264/)

### Что это значит для DNS-записи вашего проекта

Если вы привязали плавающий IP к серверу с Ingress/NGINX и указали этот IP в DNS A-записи (`greeting-dev.<домен>` → `<floating_ip>`), то DNS-запись **не потребуется менять** при пересоздании сервера — достаточно перепривязать floating IP к новому серверу через панель, и DNS продолжит указывать на правильный сервер автоматически, без TTL-задержки распространения записи.

---

<a id="раздел-1"></a>
## Раздел 1. Итоговая архитектура: список того, что вообще нужно у провайдера

После удаления обеих managed-зависимостей (Managed Kubernetes и Managed PostgreSQL) у провайдера остаётся заказать только два типа примитивных ресурсов, которые есть у **любого** VPS-провайдера в мире без исключения:

| Ресурс | Назначение | Есть ли у произвольного VPS-провайдера |
|---|---|---|
| Виртуальный сервер (VPS) с Ubuntu | devtools-сервер (GitLab, Registry, Runner), master-нода Kubernetes, worker-ноды Kubernetes | Да, всегда |
| Публичный или плавающий IP-адрес | Доступ по SSH, вход HTTP/HTTPS-трафика | Да, всегда — либо включён в тариф VPS, либо заказывается отдельно |

Больше **ничего** заказывать у провайдера не требуется. VPC/приватная сеть используется только если провайдер предоставляет её бесплатно и автоматически (тогда это просто удобство, не обязательный шаг) — сервера прекрасно взаимодействуют и через публичные IP с ограничением доступа через firewall.

Именно это и делает алгоритм переносимым на любого провайдера — от него нужна только голая виртуальная машина с SSH-доступом, что есть буквально везде: DigitalOcean, Hetzner, Рег.облако, Timeweb Cloud, Selectel, любой другой хостер VPS.

---

<a id="раздел-2"></a>
## Раздел 2. Заказ виртуальных серверов

Понадобится минимум **три** виртуальных сервера (для учебного/тестового стенда можно обойтись двумя — совместив master-ноду Kubernetes с devtools-сервером, но раздельная схема нагляднее и ближе к production):

| Сервер | Роль | Рекомендуемая конфигурация |
|---|---|---|
| `devtools` | GitLab CE, Docker Registry, GitLab Runner | 4 vCPU / 8 ГБ RAM / 100 ГБ SSD |
| `k8s-master` | Control-plane Kubernetes (k3s server) | 2 vCPU / 4 ГБ RAM / 50 ГБ SSD |
| `k8s-worker-1`, `k8s-worker-2` | Worker-ноды Kubernetes (k3s agent), включая PostgreSQL StatefulSet | 2 vCPU / 4 ГБ RAM / 50 ГБ SSD каждый |

Порядок заказа одинаков у любого провайдера — покажу общий алгоритм, применимый к панели управления практически любого хостинга (терминология Рег.облака приведена как пример, но последовательность шагов универсальна):

1. Авторизуйтесь в панели управления провайдера.
2. Найдите раздел заказа виртуального сервера (обычно называется **Новый ресурс → Сервер**, **Создать сервер**, **Deploy new instance** и т.п.).
3. Выберите операционную систему **Ubuntu 22.04 LTS**.
4. Выберите конфигурацию согласно таблице выше.
5. Добавьте свой публичный SSH-ключ.
6. Повторите для каждого из четырёх серверов.

Проверка SSH-доступа к каждому серверу после создания:

```bash
ssh root@<SERVER_PUBLIC_IP> "echo connected"
```

---

<a id="раздел-3"></a>
## Раздел 3. Плавающий IP: заказ и привязка

Плавающий (или просто выделенный публичный) IP нужен **как минимум** для сервера `devtools` (доступ к GitLab и Registry) и желательно для одной из worker-нод Kubernetes, куда будет направляться внешний HTTP-трафик через Ingress.

Общая последовательность:

1. В панели провайдера найдите раздел **Плавающие IP** (или **Floating IP**, **Elastic IP** — название отличается у разных хостеров, суть одна).
2. Заказать новый плавающий IP в том же регионе, где размещены серверы.
3. Привязать его к нужному серверу через пункт меню **Привязать к серверу** (или аналогичный).

Если у выбранного провайдера floating IP как отдельная услуга не предусмотрена (у некоторых бюджетных VPS-провайдеров публичный IP просто выдаётся сразу вместе с сервером и остаётся неизменным пока сервер существует) — в таком случае этот шаг не нужен: обычный публичный IP сервера выполняет ту же функцию, только без возможности перепривязки без пересоздания сервера.

---

<a id="раздел-4"></a>
## Раздел 4. Самостоятельный Kubernetes-кластер через k3s

Вместо заказа услуги «Кластеры Kubernetes» у провайдера кластер разворачивается **вручную** через `k3s` — облегчённый дистрибутив Kubernetes, который устанавливается одной командой и требует минимум ресурсов, что отлично подходит для самостоятельного управления на нескольких VPS:

> "curl -sfL https://get.k3s.io | sh -"

- Источник: [https://habr.com/ru/companies/slurm/articles/729480/](https://habr.com/ru/companies/slurm/articles/729480/)

### Шаг 1. Установка master-ноды (control plane)

Подключитесь по SSH к серверу `k8s-master` и выполните:

```bash
ssh root@<K8S_MASTER_IP>
curl -sfL https://get.k3s.io | sh -
```

После завершения установки проверьте статус:

```bash
sudo k3s kubectl get nodes
```

### Шаг 2. Получение токена для подключения worker-нод

На той же master-ноде:

```bash
sudo cat /var/lib/rancher/k3s/server/node-token
```

Сохраните значение токена — оно понадобится для каждой worker-ноды.

- Источник: [https://arenda-server.cloud/blog/kak-nastroit-klaster-k3s-kubernetes-na-ubuntu/](https://arenda-server.cloud/blog/kak-nastroit-klaster-k3s-kubernetes-na-ubuntu/)

### Шаг 3. Подключение worker-нод

На каждом из серверов `k8s-worker-1` и `k8s-worker-2` выполните (подставив реальный IP master-ноды и токен из шага 2):

```bash
ssh root@<K8S_WORKER_IP>
curl -sfL https://get.k3s.io | K3S_URL=https://<K8S_MASTER_IP>:6443 K3S_TOKEN=<NODE_TOKEN> sh -
```

- Источник: [https://arenda-server.cloud/blog/kak-nastroit-klaster-k3s-kubernetes-na-ubuntu/](https://arenda-server.cloud/blog/kak-nastroit-klaster-k3s-kubernetes-na-ubuntu/)

### Шаг 4. Проверка кластера

На master-ноде:

```bash
sudo k3s kubectl get nodes
```

Ожидаемый результат — три строки (одна master и две worker), все в статусе `Ready`.

### Установка NGINX Ingress Controller

k3s поставляется со встроенным `Traefik` в качестве Ingress по умолчанию, но для точного соответствия оригинальному документу (который использует NGINX Ingress Controller) поставьте его отдельно через Helm — этот шаг ничем не отличается от аналогичного шага в managed-кластере:

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace
```

Проверьте, получил ли Ingress внешний адрес:

```bash
kubectl get svc -n ingress-nginx
```

Если поле `EXTERNAL-IP` осталось в статусе `<pending>` (типично для самостоятельно поднятого кластера без облачного LoadBalancer-интегратора), используйте вместо него публичный/плавающий IP той worker-ноды, на которой физически запущен под `ingress-nginx-controller` — тот же подход, что применялся для Timeweb Cloud в оригинальном документе, где `EXTERNAL-IP` не выдавался автоматически:

```bash
kubectl get pods -n ingress-nginx -o wide
```

---

<a id="раздел-5"></a>
## Раздел 5. Получение kubeconfig без панели провайдера

Так как кластер поднят самостоятельно, kubeconfig не скачивается из панели — он лежит непосредственно на master-ноде:

```bash
sudo cat /etc/rancher/k3s/k3s.yaml
```

- Источник: [https://habr.com/ru/companies/slurm/articles/729480/](https://habr.com/ru/companies/slurm/articles/729480/)

Скопируйте этот файл на локальный ПК и замените адрес `127.0.0.1` внутри файла на реальный публичный IP master-ноды:

```bash
scp root@<K8S_MASTER_IP>:/etc/rancher/k3s/k3s.yaml ~/.kube/selfhosted-greeting.yaml
sed -i 's/127.0.0.1/<K8S_MASTER_IP>/' ~/.kube/selfhosted-greeting.yaml
chmod 600 ~/.kube/selfhosted-greeting.yaml
export KUBECONFIG=~/.kube/selfhosted-greeting.yaml
kubectl get nodes
```

- Источник: [https://arenda-server.cloud/blog/kak-nastroit-klaster-k3s-kubernetes-na-ubuntu/](https://arenda-server.cloud/blog/kak-nastroit-klaster-k3s-kubernetes-na-ubuntu/)

Как только эта переменная `KUBECONFIG` указывает на локальный файл, все дальнейшие команды `kubectl`/`helm` из оригинального документа (Разделы 12–20) работают буквально без изменений — кластер k3s полностью совместим со стандартным API Kubernetes.

---

<a id="раздел-6"></a>
## Раздел 6. Самостоятельный PostgreSQL

Как и в предыдущей версии документа, PostgreSQL разворачивается вручную — теперь логично разместить его как `StatefulSet` внутри самостоятельно поднятого k3s-кластера (Вариант B из предыдущей версии), поскольку кластер уже под полным вашим контролем.

### PersistentVolumeClaim

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

### StatefulSet

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

### Service

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

### Secret с паролем

```bash
kubectl create secret generic postgres-secret \
  --namespace=dev \
  --from-literal=DB_PASSWORD="ваш-надёжный-пароль" \
  --dry-run=client -o yaml | kubectl apply -f -
```

### Применение манифестов

```bash
kubectl apply -f postgres-pvc.yaml
kubectl apply -f postgres-statefulset.yaml
kubectl apply -f postgres-service.yaml
```

### Строка подключения для приложения

```
jdbc:postgresql://postgres.dev.svc.cluster.local:5432/greeting_db
```

### Тарификация диска в самостоятельном кластере

В k3s диск `PersistentVolumeClaim` использует локальный диск той worker-ноды, на которой физически запланирован под — по умолчанию через `local-path-provisioner`, встроенный в k3s. Это означает, что стоимость диска для базы данных — это просто часть общего диска, за который вы уже платите в рамках тарифа VPS `k8s-worker-1`/`k8s-worker-2` (Раздел 2 этого документа), без какой-либо дополнительной поминутной тарификации от managed-сервиса — вы полностью контролируете этот диск, как обычное дисковое пространство арендованного VPS.

### Резервное копирование

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
  namespace: dev
spec:
  schedule: "0 3 * * *"
  jobTemplate:
    spec:
      template:
        spec:
          containers:
            - name: pg-dump
              image: postgres:16-alpine
              command:
                - /bin/sh
                - -c
                - "PGPASSWORD=$DB_PASSWORD pg_dump -h postgres -U greeting_user greeting_db | gzip > /backup/greeting_db_$(date +%F).sql.gz"
              env:
                - name: DB_PASSWORD
                  valueFrom:
                    secretKeyRef:
                      name: postgres-secret
                      key: DB_PASSWORD
              volumeMounts:
                - name: backup-storage
                  mountPath: /backup
          restartPolicy: OnFailure
          volumes:
            - name: backup-storage
              persistentVolumeClaim:
                claimName: postgres-backup-pvc
```

---

<a id="раздел-7"></a>
## Раздел 7. GitLab, Docker Registry, GitLab Runner

Эта часть уже была полностью независима от провайдера в исходном документе — она выполняется через SSH на сервере `devtools` и без изменений переносится в эту версию. Кратко повторю последовательность (полные команды — в оригинальном Разделе 10a/11a):

```bash
ssh root@<DEVTOOLS_IP>

# Docker Registry
bash -s < scripts/setup-registry.sh

# GitLab CE
curl -fsSL https://packages.gitlab.com/install/repositories/gitlab/gitlab-ce/script.deb.sh | sudo bash
sudo EXTERNAL_URL="http://<DEVTOOLS_IP>" apt-get install -y gitlab-ce

# GitLab Runner
curl -L "https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh" | sudo bash
sudo apt-get install -y gitlab-runner
sudo gitlab-runner register
```

---

<a id="раздел-8"></a>
## Раздел 8. Сеть между серверами: приватная сеть или публичные IP

Оригинальный документ (для Timeweb Cloud) и предыдущая версия (для Рег.облака) использовали VPC/приватную сеть провайдера для связи между devtools-сервером, Kubernetes-кластером и базой данных. Так как задача — полная независимость от конкретного провайдера, важно понимать, что VPC — это **опциональное удобство**, а не обязательное требование:

- Если у выбранного провайдера приватная сеть создаётся автоматически и бесплатно (как в Рег.облаке или Timeweb Cloud) — воспользуйтесь ей: трафик между серверами внутри VPC не идёт через интернет и обычно не тарифицируется.
- Если провайдер не предоставляет VPC вообще (многие бюджетные VPS-хостинги) — серверы взаимодействуют через **публичные IP**, а безопасность обеспечивается через firewall (`ufw` или `iptables`), ограничивающий доступ к портам 5432 (PostgreSQL), 6443 (k3s API), 5000 (Registry) только с IP-адресов других серверов вашей инфраструктуры:

```bash
sudo ufw allow from <K8S_WORKER_1_IP> to any port 6443
sudo ufw allow from <K8S_WORKER_2_IP> to any port 6443
sudo ufw deny 6443
```

Это делает архитектуру полностью переносимой: независимо от того, есть ли у нового провайдера VPC-аналог, сервис продолжит работать, просто через публичные IP с ограничением firewall вместо приватной сети.

---

<a id="раздел-9"></a>
## Раздел 9. Что осталось от исходного документа без изменений

- **Разделы 6–8** оригинала (инструменты на локальном ПК, минимальное Spring Boot приложение, локальная разработка) — без изменений.
- **Раздел 12** (namespace, Kubernetes Secrets, сборка и push Docker-образа, `helm install`, эксплуатация Ingress) — без изменений в командах; единственная разница — источник значений `DB_URL`/`devtools_public_ip` теперь не из панели провайдера, а из ваших собственных заметок о том, какие IP вы задали при заказе серверов.
- **Разделы 13–16** (kubectl-справочник, Helm chart, GitLab CI/CD pipeline, стратегия dev/stage/prod) — без изменений, потому что работают через стандартный Kubernetes API, который у k3s идентичен любому другому дистрибутиву Kubernetes.
- **Раздел 17а** (Flyway-миграции) — без изменений; права на `CREATE SCHEMA` для `greeting_user` теперь выдаются прямой SQL-командой `GRANT CREATE ON DATABASE greeting_db TO greeting_user;` внутри вашего собственного PostgreSQL, полностью под вашим контролем, без обращения к API какого-либо провайдера.
- **Разделы 18–22** (безопасность, эксплуатация rollback/scale/redeploy, диагностика типовых проблем) — без изменений.

---

<a id="раздел-10"></a>
## Раздел 10. Итоговый порядок первого запуска

1. Выбрать любого VPS-провайдера (проверить только наличие Ubuntu 22.04 и SSH-доступа — этого достаточно).
2. Заказать четыре виртуальных сервера: `devtools`, `k8s-master`, `k8s-worker-1`, `k8s-worker-2` (Раздел 2).
3. Заказать плавающий (или обычный публичный) IP для сервера `devtools` и одной из worker-нод (Раздел 3).
4. Установить k3s server на `k8s-master`, получить токен (Раздел 4, шаги 1–2).
5. Установить k3s agent на обеих worker-нодах с этим токеном (Раздел 4, шаг 3).
6. Проверить кластер: `kubectl get nodes` — три ноды в статусе `Ready` (Раздел 4, шаг 4).
7. Установить NGINX Ingress Controller через Helm (Раздел 4).
8. Скопировать kubeconfig с master-ноды на локальный ПК, заменить `127.0.0.1` на реальный IP (Раздел 5).
9. Развернуть PostgreSQL как StatefulSet в namespace `dev` (Раздел 6).
10. Установить Docker Registry, GitLab CE и GitLab Runner на сервере `devtools` (Раздел 7).
11. Настроить firewall-правила между серверами, если VPC у провайдера нет (Раздел 8).
12. Создать namespace и Kubernetes Secrets с параметрами подключения к самостоятельному PostgreSQL.
13. Выдать пользователю `greeting_user` права `CREATE SCHEMA` напрямую в PostgreSQL.
14. Создать репозиторий в GitLab, выполнить первый `git push`, настроить CI/CD Variables.
15. Выполнить ручной `helm upgrade --install` для первой проверки.
16. Настроить DNS A-запись на плавающий IP той worker-ноды, где физически запущен Ingress Controller.
17. Финальная проверка: `curl http://greeting-dev.<ваш-домен>/api/greeting` → ожидается HTTP 200, а `/actuator/health` → `"db": {"status": "UP"}`.

---

<a id="раздел-11"></a>
## Раздел 11. Как перенести всё на другого провайдера

Именно это и есть главная цель отказа от managed-сервисов — показать, что перенос сводится к четырём шагам, одинаковым для любого хостера VPS:

1. Заказать те же четыре виртуальных сервера с Ubuntu 22.04 у нового провайдера.
2. Заказать (или получить в комплекте с VPS) публичные IP-адреса.
3. Повторить установку k3s (Раздел 4), GitLab/Registry/Runner (Раздел 7) и PostgreSQL (Раздел 6) — команды идентичны, потому что не зависят от API конкретного облака.
4. Обновить DNS A-запись на новый IP и, если использовались плавающие IP старого провайдера — просто заказать их аналог у нового (или обойтись обычными публичными IP, если провайдер не предоставляет floating IP как отдельную услугу).

Ничего в `.gitlab-ci.yml`, Helm chart, Kubernetes-манифестах PostgreSQL или скриптах `setup-registry.sh`/`create-secrets.sh` менять не требуется — весь стек одинаково воспроизводим на VPS любого провайдера в мире.
