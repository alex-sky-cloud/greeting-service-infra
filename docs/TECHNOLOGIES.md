# Пояснение технологий: от контейнеров до Kubernetes

> Этот документ объясняет, как работает каждая технология в проекте.
> Технические термины на английском. Объяснения — с аналогиями из жизни.

---

## Оглавление

1. Как код попадает в продакшн: общая картина
2. Docker: что такое контейнер и зачем он нужен
3. Docker Registry: склад для образов
4. Kubernetes: что это и зачем нужен оркестратор
5. Объекты Kubernetes: полное объяснение каждого (Pod, Deployment, Service, Ingress, ConfigMap, Secret, HPA)
6. Helm: пакетный менеджер для Kubernetes
7. Terraform: инфраструктура как код
8. Bitbucket Pipelines: CI/CD от git push до деплоя
9. Ingress и внешний доступ
10. Secrets и ConfigMaps: управление конфигурацией
11. VPC и сетевая изоляция

---

## 1. Как код попадает в продакшн: общая картина

Прежде чем разбирать каждую технологию отдельно, важно понять общий маршрут. Ниже — схема всего пути от git push до пользователя:

<!--IMG:tech-docker-lifecycle.png-->

Каждый шаг этого маршрута:

1. Разработчик пишет код и выполняет `git push` в Bitbucket.
2. Bitbucket Pipelines получает webhook и запускает pipeline.
3. Pipeline последовательно выполняет: `mvn test` (тесты), `mvn package` (сборка JAR), `docker build` (упаковка в контейнер), `docker push` (загрузка в Docker Registry), `helm upgrade` (деплой в Kubernetes).
4. Kubernetes скачивает Docker image из Registry.
5. Kubernetes запускает новые поды (контейнеры) с новой версией.
6. NGINX Ingress пропускает внешний трафик к подам.
7. Пользователь открывает `greeting.example.com` и получает ответ.

Каждую из этих технологий подробно разбираем ниже.

---

## 2. Docker: что такое контейнер и зачем он нужен

### Аналогия

Представьте, что ваше приложение — это блюдо, которое нужно приготовить. Раньше повар (разработчик) готовил у себя на кухне, а затем пытался воспроизвести блюдо в ресторане (на сервере) — и что-то шло не так: другая плита, другие специи, другая температура.

Docker — это контейнер для доставки еды, в котором уже готовое блюдо вместе со всей кухней, плитой, специями и рецептом. Куда бы вы ни привезли этот контейнер — блюдо будет одинаковым.

### Как работает технически

Docker изолирует приложение вместе со всеми зависимостями в container (контейнер). Контейнер — это процесс на хост-машине, изолированный с помощью Linux-механизмов:

- namespaces — изолируют сеть, процессы, файловую систему
- cgroups — ограничивают CPU и память

Docker image — неизменяемый снимок (шаблон), из которого запускаются контейнеры. Image состоит из слоёв (layers). Каждая инструкция в Dockerfile добавляет новый слой. Docker кэширует слои — поэтому повторная сборка быстрее, если ранние слои не изменились.

### Схема

<!--IMG:tech-docker-lifecycle.png-->

### Ключевые концепции

- **Dockerfile** — текстовый файл с инструкциями для сборки image
- **image** — неизменяемый шаблон (read-only), из которого создаются контейнеры
- **container** — запущенный экземпляр image; при удалении контейнера изменения в нём теряются
- **layer** — слой файловой системы, каждый соответствует одной инструкции Dockerfile
- **multi-stage build** — сборка в несколько этапов: сначала компиляция (полный JDK), затем финальный образ только с JRE

### Dockerfile нашего проекта — разбор по строкам

```dockerfile
# Stage 1: Build
# Используем полный JDK + Maven только для сборки.
# Этот образ НЕ попадёт в финальный image.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Сначала копируем только pom.xml и скачиваем зависимости.
# Docker кэширует этот слой — пока pom.xml не меняется,
# mvn dependency:go-offline не выполняется повторно.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Теперь копируем исходный код и собираем.
COPY src ./src
RUN mvn package -DskipTests -B

# Stage 2: Runtime
# Финальный образ — только JRE (без Maven, без JDK исходников).
# eclipse-temurin:21-jre-alpine весит ~200 МБ против ~600 МБ у JDK.
FROM eclipse-temurin:21-jre-alpine AS runtime

# Создаём отдельного пользователя.
# Запуск от root в контейнере — уязвимость безопасности.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Копируем только JAR из предыдущего stage.
COPY --from=build /workspace/target/*.jar app.jar

USER appuser
EXPOSE 8080

# -XX:+UseContainerSupport — JVM 21 читает CPU/RAM лимиты из cgroups.
# Без этого JVM видит всю RAM хоста, а не лимит контейнера.
# -XX:MaxRAMPercentage=75.0 — heap = 75% от memory limit контейнера.
ENTRYPOINT ["java",
  "-XX:+UseContainerSupport",
  "-XX:MaxRAMPercentage=75.0",
  "-jar", "app.jar"]
```

### Основные команды Docker

```bash
# Собрать image из Dockerfile в текущей директории:
docker build -t greeting-service:v1 .
# -t — тег (имя:версия)

# Запустить контейнер:
docker run -p 8080:8080 -e APP_ENV=local greeting-service:v1
# -p 8080:8080 — пробросить порт хоста 8080 на порт контейнера 8080
# -e APP_ENV=local — передать переменную окружения

# Список запущенных контейнеров:
docker ps

# Логи контейнера:
docker logs <container_id>

# Выполнить команду внутри работающего контейнера:
docker exec -it <container_id> /bin/sh

# Остановить и удалить:
docker stop <container_id>
docker rm <container_id>

# Список всех images:
docker images
```

### Типичные ошибки

**Ошибка: `exec format error` в Kubernetes после деплоя**

Причина: вы собрали image на Mac с процессором Apple Silicon (arm64), а Kubernetes-узлы работают на amd64. Исправление: добавьте явный target platform в Dockerfile:

```dockerfile
FROM --platform=linux/amd64 eclipse-temurin:21-jre-alpine AS runtime
```

Или собирайте с флагом:

```bash
docker buildx build --platform linux/amd64 -t greeting-service:v1 .
```

**Ошибка: контейнер занимает слишком много памяти и получает OOMKill**

Причина: JVM не учитывает лимиты контейнера (старые версии Java до 8u191). Исправление: используйте Java 21 с флагом `-XX:+UseContainerSupport` (уже в нашем Dockerfile).

### Источники

Официальная документация Docker: https://docs.docker.com/get-started/docker-overview/

> **EN:** Each instruction in a Dockerfile creates a layer in the image. When you change the Dockerfile and rebuild the image, only those layers which have changed are rebuilt. This is part of what makes images so lightweight, small, and fast, when compared to other virtualization technologies.

> **RU:** Каждая инструкция в Dockerfile создаёт слой в образе. При изменении Dockerfile и повторной сборке перестраиваются только изменившиеся слои. Именно это делает образы такими лёгкими, небольшими и быстрыми по сравнению с другими технологиями виртуализации.

---

## 3. Docker Registry: склад для образов

### Аналогия

Docker Registry — это как npm registry (если вы работали с Node.js) или Maven Central (если вы Java-разработчик), только не для библиотек, а для Docker images.

Вы собираете image на одной машине (в pipeline), загружаете (`docker push`) в Registry, а потом Kubernetes на совсем другой машине скачивает (`docker pull`) этот же image.

### Как работает технически

В нашем проекте используется distribution/registry:2 — официальный open-source Docker Registry от Docker Inc. Это HTTP-сервер, который хранит image layers на диске.

```
docker push <IP>:5000/greeting-service:abc12345
     │
     ▼
Registry API v2 (HTTP на порту 5000)
     │
     ├── проверяет credentials (htpasswd)
     ├── принимает layers по одному (каждый слой Dockerfile)
     └── сохраняет на диск (/var/lib/registry/)
```

### Жизненный цикл image в нашем проекте

```bash
# 1. Pipeline: сборка image с тегом из commit hash
docker build -t <REGISTRY>/<IMAGE>:<TAG> .
# TAG = первые 8 символов git commit hash
# Пример: 1.2.3.4:5000/greeting-service:abc12345

# 2. Pipeline: отправка в Registry
docker push <REGISTRY>/<IMAGE>:<TAG>

# 3. Kubernetes получает манифест от Helm с image тегом abc12345

# 4. Kubernetes на каждом worker-узле скачивает image
# (использует Secret registry-credentials как imagePullSecret)
docker pull <REGISTRY>/<IMAGE>:<TAG>

# 5. Kubernetes запускает контейнер из скачанного image
```

### Ключевые концепции

- **Registry** — сервер для хранения и раздачи Docker images
- **repository** — именованная коллекция image с разными тегами (например, `greeting-service`)
- **tag** — метка конкретной версии image (например, `abc12345`)
- **imagePullSecret** — Kubernetes Secret с credentials для скачивания из приватного Registry
- **push / pull** — загрузка image в Registry и скачивание из него

### Почему тег = commit hash, а не latest

Тег `latest` — антипаттерн в production. Если всегда пушить `latest`, невозможно понять:
- какую именно версию кода запустил Kubernetes
- что именно изменилось при обновлении
- как откатиться к конкретной версии

Тег из commit hash решает всё это:

```bash
# По тегу abc12345 всегда можно найти точный commit в git:
git show abc12345
# И посмотреть, что именно изменилось
```

---

## 4. Kubernetes: что это и зачем нужен оркестратор

### Аналогия

Представьте, что вы управляете рестораном с десятками официантов (контейнеров).

Без Kubernetes (просто docker run на сервере):
- Официант упал? Вы идёте вручную его поднимать.
- Нагрузка выросла? Вы вручную запускаете ещё официантов.
- Сервер сломался? Вы вручную переносите официантов на другой сервер.

Kubernetes — это умный метрдотель, который:
- Следит за официантами: если один упал — сразу ставит замену
- Масштабирует: если гостей стало больше — нанимает новых официантов
- Распределяет нагрузку: направляет гостей к свободным официантам
- При поломке стола (сервера) — пересаживает официантов за другой стол (узел)

### Как работает технически

Kubernetes — это декларативная система управления контейнерами. Вы описываете желаемое состояние (сколько копий, какой image, сколько памяти), а Kubernetes сам приводит реальное состояние к желаемому и поддерживает его.

### Схема

<!--IMG:tech-k8s-architecture.png-->

**Control Plane** (в Timeweb Cloud Managed K8S — управляется за вас):
- **API Server** — единственная точка входа. Все команды kubectl идут сюда.
- **etcd** — распределённая база данных, хранит всё состояние кластера.
- **Scheduler** — решает, на каком Worker Node запустить новый Pod.
- **Controller Manager** — следит, чтобы фактическое состояние соответствовало желаемому.

**Worker Nodes** — ваши VPS-серверы, где реально работают контейнеры:
- **kubelet** — агент, запускает и следит за подами на узле.
- **kube-proxy** — настраивает сетевые правила для Service.
- **container runtime** — containerd, реально запускает контейнеры.

### Ключевые концепции

- **cluster** — набор узлов под управлением Kubernetes
- **node** — физический или виртуальный сервер в кластере
- **control plane** — компоненты управления кластером
- **desired state** — описанное вами желаемое состояние системы
- **actual state** — реальное состояние в данный момент
- **reconciliation** — непрерывный процесс приведения actual state к desired state

### Главная идея Kubernetes: декларативное управление

Вы не говорите Kubernetes, что делать — вы описываете, каким должно быть состояние.

Например:

```yaml
# Я хочу, чтобы в кластере ВСЕГДА работало 3 копии greeting-service
replicas: 3
```

Kubernetes сам следит: запустил 3 пода → один упал → автоматически запускает новый. Вы не пишете скрипт «если под упал, то запусти новый» — Kubernetes делает это сам.

### Источники

Официальная документация Kubernetes: https://kubernetes.io/docs/concepts/overview/

> **EN:** You can describe the desired state for your deployed containers using Kubernetes, and it can change the actual state to the desired state at a controlled rate. Kubernetes comprises a set of independent, composable control processes that continuously drive the current state towards the provided desired state.

> **RU:** Вы можете описать желаемое состояние развёрнутых контейнеров с помощью Kubernetes, и он будет изменять реальное состояние до желаемого с контролируемой скоростью. Kubernetes состоит из набора независимых, компонуемых процессов управления, которые непрерывно приводят текущее состояние к указанному желаемому.

---

## 5. Объекты Kubernetes: полное объяснение каждого

### 5.1 Namespace

**Аналогия:** Namespace — это как отдельная квартира в жилом доме (кластере). Кластер один, но в нём есть изолированные пространства: `dev`, `stage`, `prod`. Ресурсы внутри разных namespace не видят друг друга напрямую.

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: prod
```

```bash
# Создать:
kubectl create namespace prod

# Список:
kubectl get namespaces

# Работать с конкретным namespace (флаг -n):
kubectl get pods -n prod
kubectl get pods -n dev
```

Зачем это нам: dev-разработчики деплоят в `dev`, не трогая `prod`. Secrets у каждого окружения свои.

---

### 5.2 Pod

**Аналогия:** Pod — это как квартира, где живёт один (иногда несколько) жилец-контейнер. У квартиры есть свой сетевой адрес (IP), и все жильцы в ней видят друг друга.

Pod — минимальная единица деплоя в Kubernetes. Внутри пода один или несколько контейнеров, которые делят сеть и тома (volumes).

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: greeting-pod
  namespace: prod
spec:
  containers:
    - name: greeting
      image: 1.2.3.4:5000/greeting-service:abc12345
      ports:
        - containerPort: 8080
      env:
        - name: APP_ENV
          value: "production"
```

Pod — эфемерен. Если он упал или его удалили, он не восстановится сам. Именно поэтому в реальной практике Pod напрямую почти никогда не создают — используют Deployment.

```bash
# Посмотреть поды:
kubectl get pods -n prod

# Подробная информация о конкретном поде:
kubectl describe pod greeting-service-abc123 -n prod

# Логи пода:
kubectl logs greeting-service-abc123 -n prod

# Войти в под (если есть shell):
kubectl exec -it greeting-service-abc123 -n prod -- /bin/sh
```

---

### 5.3 Deployment

**Аналогия:** Deployment — это как трудовой договор с кадровым агентством. Вы говорите: «Мне нужно 3 официанта типа greeting-service». Агентство (Kubernetes) следит, чтобы их было ровно 3. Один уволился (pod упал) — агентство немедленно находит замену.

Deployment управляет ReplicaSet, который в свою очередь управляет подами.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: greeting-service
  namespace: prod
spec:
  replicas: 3          # Сколько копий (podов) должно работать
  selector:
    matchLabels:
      app: greeting-service  # Deployment управляет подами с этим label
  strategy:
    type: RollingUpdate      # Обновляем постепенно, не всё сразу
    rollingUpdate:
      maxSurge: 1            # Разрешить на 1 под больше нормы при обновлении
      maxUnavailable: 0      # Нельзя иметь недоступных подов в процессе
  template:                  # Шаблон для создания подов
    metadata:
      labels:
        app: greeting-service
    spec:
      containers:
        - name: greeting
          image: 1.2.3.4:5000/greeting-service:abc12345
```

**RollingUpdate — как работает обновление без даунтайма:**

При обновлении image (`abc12345` → `abc99999`) Kubernetes не останавливает все поды сразу. Он поочерёдно заменяет старые поды новыми: сначала запускает один новый (maxSurge=1), затем убирает один старый, и так далее. На каждом шаге работает не менее 3 подов (maxUnavailable=0) — сервис не прерывается.

```bash
# Посмотреть Deployment:
kubectl get deployments -n prod

# Следить за ходом обновления:
kubectl rollout status deployment/greeting-service -n prod

# История обновлений:
kubectl rollout history deployment/greeting-service -n prod

# Откатить к предыдущей версии:
kubectl rollout undo deployment/greeting-service -n prod

# Масштабировать:
kubectl scale deployment greeting-service -n prod --replicas=5
```

---

### 5.4 Service

**Аналогия:** У подов постоянно меняются IP-адреса — при перезапуске под получает новый IP. Service — это как стойка ресепшн в отеле. Гость всегда приходит на ресепшн (постоянный IP), а ресепшн уже знает, в каком номере (поде) его поселить.

Service обеспечивает:
- Стабильное DNS-имя и IP независимо от перезапуска подов
- Load balancing — распределение запросов между всеми подами

```yaml
apiVersion: v1
kind: Service
metadata:
  name: greeting-service
  namespace: prod
spec:
  type: ClusterIP      # Доступен только внутри кластера
  selector:
    app: greeting-service  # Направляет трафик к подам с этим label
  ports:
    - port: 80         # Порт Service
      targetPort: 8080  # Порт контейнера
```

Типы Service:

| Тип | Где доступен | Когда использовать |
|---|---|---|
| ClusterIP | Только внутри кластера | Для большинства сервисов (наш случай) |
| NodePort | Снаружи через порт узла (30000-32767) | Для отладки, не для production |
| LoadBalancer | Снаружи через публичный IP | Для Ingress Controller, облачные LB |

Наш `greeting-service` использует `ClusterIP` — он не должен быть напрямую доступен снаружи. Снаружи трафик приходит через Ingress.

```bash
# Посмотреть Services:
kubectl get services -n prod

# DNS-имя внутри кластера:
# <service-name>.<namespace>.svc.cluster.local
# Пример: greeting-service.prod.svc.cluster.local

# Проверить изнутри кластера через временный под:
kubectl run curl-test --image=curlimages/curl -it --rm --restart=Never -n prod \
  -- curl http://greeting-service.prod.svc.cluster.local/api/greeting
```

---

### 5.5 Ingress

**Аналогия:** Ingress — это как шлагбаум на въезде в жилой комплекс с охранником. Снаружи едет машина (HTTP-запрос). Охранник (Ingress Controller) смотрит на номерной знак (HTTP заголовок `Host`) и решает, в какой подъезд (Service) пустить.

Без Ingress нужно было бы для каждого сервиса заводить отдельный публичный IP — дорого и неудобно. Ingress позволяет одному IP обслуживать много сервисов по разным hostname или path.

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: greeting-service
  namespace: prod
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  ingressClassName: nginx     # Указываем, что используем NGINX Ingress Controller
  rules:
    - host: greeting.example.com    # Входящий hostname
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: greeting-service   # Направить в этот Service
                port:
                  number: 80
```

---

### 5.6 ConfigMap

**Аналогия:** ConfigMap — это как доска объявлений в офисе. Все сотрудники (поды) читают её и используют в работе. Если директор изменил объявление — все видят новую версию.

ConfigMap хранит несекретные конфигурационные данные: имена окружений, URL сервисов, флаги feature toggle и т.д.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: greeting-config
  namespace: prod
data:
  APP_ENV: "production"
  APP_VERSION: "1.2.0"
  LOG_LEVEL: "INFO"
```

В Deployment ссылаемся на ConfigMap:

```yaml
envFrom:
  - configMapRef:
      name: greeting-config
```

---

### 5.7 Secret

**Аналогия:** Secret — это как сейф в том же офисе. Хранит секретные данные: пароли, токены, ключи. Доступ ограничен. Содержимое кодируется в base64 (но не шифруется по умолчанию!).

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: greeting-service-secret
  namespace: prod
type: Opaque
data:
  # Значения кодируются в base64: echo -n "value" | base64
  DB_URL: amRiYzpwb3N0Z3Jlc3FsOi8v...
  DB_PASSWORD: c3VwZXJzZWNyZXQ=
```

Отличие Secret от ConfigMap:

| Параметр | ConfigMap | Secret |
|---|---|---|
| Данные | Открытый текст | base64 (не шифрование!) |
| Назначение | Несекретная конфигурация | Пароли, токены, ключи |
| Логи | Видны в логах kubectl | Маскируются в большинстве инструментов |
| RBAC | Обычные права | Можно ограничить отдельно |

```bash
# Создать Secret из командной строки (предпочтительно — значения не попадут в git):
kubectl create secret generic greeting-service-secret \
  --namespace prod \
  --from-literal=DB_URL="jdbc:postgresql://10.10.0.5:5432/greeting_db" \
  --from-literal=DB_PASSWORD="my_password"

# Прочитать Secret (значение в base64):
kubectl get secret greeting-service-secret -n prod -o yaml

# Декодировать конкретное значение:
kubectl get secret greeting-service-secret -n prod \
  -o jsonpath='{.data.DB_PASSWORD}' | base64 -d
```

---

### 5.8 ServiceAccount

**Аналогия:** ServiceAccount — это как удостоверение сотрудника. Под использует его при обращении к Kubernetes API. По умолчанию у пода есть `default` ServiceAccount с минимальными правами.

В нашем Helm chart мы создаём отдельный ServiceAccount для приложения — хорошая практика безопасности (принцип минимальных привилегий).

---

### 5.9 HorizontalPodAutoscaler (HPA)

**Аналогия:** HPA — это автоматический HR. Когда нагрузка растёт (официанты не справляются), HR нанимает новых. Когда нагрузка спала — увольняет лишних, чтобы не тратить деньги.

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: greeting-service
  namespace: prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: greeting-service
  minReplicas: 3     # Минимум подов (всегда)
  maxReplicas: 10    # Максимум подов (при пиковой нагрузке)
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70  # Если CPU > 70% — добавляем поды
```

```bash
# Посмотреть HPA:
kubectl get hpa -n prod

# Детали: текущая нагрузка, количество реплик:
kubectl describe hpa greeting-service -n prod
```

---

### 5.10 Liveness и Readiness Probes

- **Liveness probe** — «жив ли под?» Если нет — Kubernetes перезапускает его.
- **Readiness probe** — «готов ли под принимать трафик?» Если нет — Service не направляет к нему запросы.

Поток для readiness: под запускается → Kubernetes опрашивает `GET /actuator/health/readiness` → пока ответ не 200, трафик не идёт → как только 200 — под добавляется в Service.

Поток для liveness: каждые 10 секунд Kubernetes опрашивает `GET /actuator/health/liveness` → если 3 раза подряд нет 200 — контейнер перезапускается.

Spring Boot Actuator предоставляет оба endpoint из коробки при включении `livenessState` и `readinessState` в `application.yml`.

---

## 6. Helm: пакетный менеджер для Kubernetes

### Аналогия

Helm — это как `apt` или `yum` для Kubernetes. Вместо того чтобы писать 5 отдельных YAML-файлов (Deployment, Service, Ingress, ServiceAccount, HPA) и применять каждый через `kubectl apply`, вы пишете один Helm chart — шаблонизированный пакет.

Helm решает три проблемы:

1. **Повторение кода между окружениями.** Dev и prod — почти одинаковые манифесты, только значения разные. Helm позволяет вынести значения в `values.yaml`.
2. **Отслеживание истории деплоев.** Helm помнит каждый `helm upgrade` как отдельную ревизию.
3. **Атомарный откат.** `helm rollback` откатывает всё (Deployment, ConfigMap, Ingress) к нужной ревизии одной командой.

### Схема

<!--IMG:tech-helm-flow.png-->

### Структура Helm chart

```
greeting-service/          ← директория chart
├── Chart.yaml             ← метаданные: имя, версия, описание
├── values.yaml            ← значения по умолчанию (dev/local)
├── values-dev.yaml        ← переопределения для dev
├── values-prod.yaml       ← переопределения для prod
└── templates/             ← шаблоны K8S манифестов
    ├── _helpers.tpl        ← вспомогательные функции (Go templates)
    ├── deployment.yaml
    ├── service.yaml
    ├── ingress.yaml
    ├── serviceaccount.yaml
    └── hpa.yaml
```

### Как работает шаблонизация

В `deployment.yaml` вместо конкретного значения используется переменная из `values.yaml`:

```yaml
# templates/deployment.yaml (шаблон):
replicas: {{ .Values.replicaCount }}
image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
```

```yaml
# values.yaml (значения по умолчанию):
replicaCount: 2
image:
  repository: registry.example.com/greeting-service
  tag: latest
```

```yaml
# values-prod.yaml (переопределения для prod):
replicaCount: 3
```

При деплое в prod Helm объединяет `values.yaml` + `values-prod.yaml` и рендерит итоговый YAML:

```yaml
replicas: 3
image: "registry.example.com/greeting-service:abc12345"
```

### Ключевые концепции

- **chart** — пакет шаблонизированных Kubernetes-манифестов
- **release** — установленный экземпляр chart в кластере (один chart может быть установлен несколько раз с разными именами)
- **revision** — версия release после каждого `helm upgrade`
- **values** — параметры конфигурации, подставляемые в шаблоны
- **templates** — файлы Go-шаблонов, из которых генерируются Kubernetes-манифесты

### Основные команды Helm

```bash
# Проверить синтаксис chart:
helm lint infra/helm/greeting-service

# Посмотреть, что получится после рендеринга шаблонов (без деплоя):
helm template greeting-service infra/helm/greeting-service \
  -f infra/helm/greeting-service/values.yaml \
  -f infra/helm/greeting-service/values-prod.yaml \
  --set image.tag="abc12345"

# Установить или обновить release:
# --install — создать, если не существует
# --atomic  — откатить, если деплой упал
# --timeout — максимальное время ожидания готовности подов
helm upgrade --install greeting-service infra/helm/greeting-service \
  --namespace prod \
  --create-namespace \
  -f infra/helm/greeting-service/values.yaml \
  -f infra/helm/greeting-service/values-prod.yaml \
  --set image.tag="abc12345" \
  --atomic \
  --timeout 5m

# Список установленных releases:
helm list --all-namespaces

# История конкретного release:
helm history greeting-service -n prod

# Откат к предыдущей ревизии:
helm rollback greeting-service -n prod

# Откат к конкретной ревизии:
helm rollback greeting-service 2 -n prod

# Удалить release (поды, service, ingress будут удалены):
helm uninstall greeting-service -n prod
```

### Источники

Официальная документация Helm: https://helm.sh/docs/topics/charts/

> **EN:** Helm uses a packaging format called charts. A chart is a collection of files that describe a related set of Kubernetes resources. A single chart might be used to deploy something simple, like a memcached pod, or something complex, like a full web app stack with HTTP servers, databases, caches, and so on.

> **RU:** Helm использует формат упаковки, называемый charts. Chart — это коллекция файлов, описывающих связанный набор Kubernetes-ресурсов. Один chart может использоваться для деплоя чего-то простого — например, пода memcached — или чего-то сложного, например, полного стека веб-приложения с HTTP-серверами, базами данных, кэшами и т.д.

---

## 7. Terraform: инфраструктура как код

### Аналогия

Terraform — это как строительный проект здания. Без него: прораб вручную нанимает каждого рабочего, заказывает материалы, следит за стройкой. Всё в голове, не воспроизводимо, при смене прораба — хаос.

С Terraform: архитектор пишет проект (`.tf` файлы), в котором описано всё: сколько этажей, какие материалы, какие коммуникации. Любой прораб берёт проект и строит точно такое же здание. Хочешь добавить этаж — меняешь проект, Terraform сам разберётся, что именно нужно изменить.

### Как работает технически

Terraform читает `.tf` файлы (декларативное описание желаемой инфраструктуры), сравнивает с текущим состоянием (`terraform.tfstate`) и вызывает API облачного провайдера для приведения реального состояния к желаемому.

### Схема

<!--IMG:tech-terraform-lifecycle.png-->

### Жизненный цикл Terraform

```bash
# 1. init — скачать провайдер, настроить backend
terraform init

# 2. plan — показать, что будет сделано (ничего не создаёт)
terraform plan
# Вывод: список изменений (create, update, destroy)

# 3. apply — применить изменения
terraform apply
# Вызывает Timeweb Cloud API:
# POST https://api.timeweb.cloud/api/v1/k8s/clusters
# PATCH https://api.timeweb.cloud/api/v1/servers/{id}
# DELETE https://api.timeweb.cloud/api/v1/vpcs/{id}

# 4. terraform.tfstate обновляется — хранит реальные ID созданных ресурсов
```

### Terraform State

State (`terraform.tfstate`) — это как инвентарная книга. Terraform записывает туда: «я создал кластер с ID 12345, сервер с ID 67890». При следующем `plan` он сравнивает `.tf` файлы с тем, что уже создано по state.

Если вы работаете в команде и у каждого свой локальный state — произойдёт рассинхронизация. Решение: remote backend на S3. В нашем `main.tf` эта секция уже написана и закомментирована.

### Ключевые концепции

- **provider** — плагин, знающий API конкретного облака (в нашем случае timeweb-cloud)
- **resource** — объект инфраструктуры (кластер, сервер, база данных, VPC)
- **state** — файл с текущим состоянием всех управляемых ресурсов
- **plan** — предварительный расчёт изменений перед применением
- **apply** — фактическое применение изменений через API провайдера
- **output** — значения, которые Terraform выводит после apply (например, IP кластера, kubeconfig)

### Основные команды Terraform

```bash
# Инициализация — скачать провайдер, настроить backend:
terraform init
# Обязательно после первого клона и после изменения версии провайдера

# Планирование — показать, что будет сделано (ничего не создаёт):
terraform plan
# ВСЕГДА выполняйте перед apply

# Применение:
terraform apply
# Terraform запросит подтверждение: введите 'yes'
# Для автоматизации: terraform apply -auto-approve (используйте осторожно)

# Просмотр текущего state:
terraform state list              # список всех ресурсов в state
terraform state show <resource>   # детали конкретного ресурса

# Получить output-значения:
terraform output                          # все outputs
terraform output k8s_cluster_status       # конкретный output
terraform output -raw kubeconfig          # sensitive output без маскировки

# Обновить state из реального состояния ресурсов (если что-то изменили вне Terraform):
terraform refresh

# Уничтожить ВСЕ ресурсы из state:
# ВНИМАНИЕ: удаляет кластер, БД, все данные!
terraform destroy
```

### Провайдер timeweb-cloud

Провайдер — это плагин, который знает, как общаться с API Timeweb Cloud. Список всех ресурсов провайдера: https://github.com/timeweb-cloud/terraform-provider-timeweb-cloud

Ключевые ресурсы, используемые в проекте:

| Ресурс | Что создаёт |
|---|---|
| `twc_vpc` | Приватную сеть (Virtual Private Cloud) |
| `twc_k8s_cluster` | Managed Kubernetes кластер |
| `twc_k8s_node_group` | Группу worker-узлов кластера |
| `twc_database_cluster` | Managed PostgreSQL / MySQL / Redis |
| `twc_database_instance` | Базу данных внутри кластера |
| `twc_database_user` | Пользователя БД |
| `twc_s3_bucket` | S3-совместимое хранилище |
| `twc_server` | VPS-сервер |
| `twc_ssh_key` | SSH-ключ для доступа к серверу |

### Источники

Официальная документация Terraform: https://developer.hashicorp.com/terraform/intro

> **EN:** HashiCorp Terraform is an infrastructure as code tool that lets you define both cloud and on-prem resources in human-readable configuration files that you can version, reuse, and share. Terraform configuration files are declarative, meaning that they describe the end state of your infrastructure.

> **RU:** HashiCorp Terraform — это инструмент инфраструктуры как кода, который позволяет описывать как облачные, так и локальные ресурсы в читаемых конфигурационных файлах, которые можно версионировать, переиспользовать и распространять. Конфигурационные файлы Terraform декларативны: они описывают конечное состояние вашей инфраструктуры.

---

## 8. Bitbucket Pipelines: CI/CD от git push до деплоя

### Аналогия

Bitbucket Pipelines — это как конвейер на заводе. Сырьё (git commit) поступает на вход. На каждом этапе конвейера что-то происходит: сборка, контроль качества (тесты), упаковка (Docker), доставка на склад (Registry), установка (Kubernetes деплой). Если на любом этапе брак — конвейер останавливается, продукт не идёт дальше.

### Как работает технически

Bitbucket Pipelines — это встроенный CI/CD сервис, интегрированный прямо в Bitbucket. При каждом git push Bitbucket запускает изолированный Docker-контейнер и выполняет в нём команды, описанные в `bitbucket-pipelines.yml`.

### Структура pipeline файла

Файл `bitbucket-pipelines.yml` лежит в корне репозитория. Bitbucket автоматически его читает.

```yaml
# Базовый Docker image для шагов pipeline
image: eclipse-temurin:21-jdk

pipelines:
  branches:
    develop:              # Правила для ветки develop
      - step:             # Один шаг
          name: "Build"
          script:         # Команды шага
            - mvn package

    main:
      - step:
          name: "Build"
          script:
            - mvn package
      - step:
          name: "Deploy"
          trigger: manual  # Ручной запуск
          script:
            - helm upgrade ...
```

### Ключевые концепции

- **pipeline** — последовательность шагов, запускаемых автоматически при git-событиях
- **step** — один изолированный шаг pipeline; запускается в отдельном контейнере
- **trigger: manual** — шаг требует ручного подтверждения в UI (используем для деплоя в prod)
- **Repository Variables** — переменные окружения, задаваемые в UI Bitbucket (секреты не попадают в код)

### Переменные окружения в pipeline

Секреты (пароли, токены) никогда не хранятся в `bitbucket-pipelines.yml`. Они задаются в Bitbucket UI: Repository → Settings → Repository Variables.

В pipeline они доступны как обычные env variables:

```bash
# В pipeline можно использовать:
docker login "${REGISTRY_HOST}" -u "${REGISTRY_USER}" -p "${REGISTRY_PASSWORD}"
```

### Как pipeline получает доступ к Kubernetes

```bash
# В Bitbucket Variables хранится KUBE_CONFIG_BASE64:
# cat ~/.kube/timeweb-greeting.yaml | base64 -w0

# В pipeline шаге:
mkdir -p ~/.kube
echo "${KUBE_CONFIG_BASE64}" | base64 -d > ~/.kube/config
chmod 600 ~/.kube/config

# Теперь kubectl работает с кластером Timeweb Cloud:
kubectl get nodes
helm upgrade ...
```

### Встроенные переменные Bitbucket

- `BITBUCKET_COMMIT` — полный hash текущего коммита
- `BITBUCKET_BRANCH` — имя ветки
- `BITBUCKET_BUILD_NUMBER` — номер сборки
- `BITBUCKET_REPO_SLUG` — имя репозитория

Мы используем `${BITBUCKET_COMMIT::8}` — первые 8 символов hash как тег Docker image.

### Источники

Официальная документация Bitbucket Pipelines: https://support.atlassian.com/bitbucket-cloud/docs/get-started-with-bitbucket-pipelines/

> **EN:** Bitbucket Pipelines is an integrated CI/CD service built into Bitbucket Cloud. It allows you to automatically build, test, and even deploy your code based on a configuration file in your repository. A pipeline is defined using a YAML file called bitbucket-pipelines.yml, which is located at the root of your repository.

> **RU:** Bitbucket Pipelines — это встроенный CI/CD сервис, интегрированный в Bitbucket Cloud. Он позволяет автоматически собирать, тестировать и даже деплоить ваш код на основе конфигурационного файла в репозитории. Pipeline описывается в YAML-файле `bitbucket-pipelines.yml`, расположенном в корне репозитория.

---

## 9. Ingress и внешний доступ

### Аналогия

Ingress — это как главный вход в торговый центр с навигатором. Все посетители (HTTP-запросы) входят через один вход (один публичный IP). Навигатор (Ingress Controller) смотрит на адрес назначения (hostname или path) и указывает, в какой магазин (Service) идти.

Без Ingress нужно было бы для каждого сервиса заводить отдельный публичный IP и отдельный LoadBalancer — дорого и неудобно при большом числе сервисов.

### Схема

<!--IMG:tech-ingress-flow.png-->

### Полная цепочка от интернета до пода

```
DNS: greeting.example.com → A-запись → Ingress IP (публичный)
                                              │
                                              ▼
                                 Timeweb Cloud LoadBalancer
                                              │
                                              ▼
                              NGINX Ingress Controller Pod
                              (namespace: ingress-nginx)
                              Правило: host=greeting.example.com
                                              │
                                              ▼
                               Service: greeting-service:80
                               (namespace: prod, ClusterIP)
                                              │
                              ┌───────────────┼───────────────┐
                              ▼               ▼               ▼
                           Pod 1           Pod 2           Pod 3
                           :8080           :8080           :8080
                        (Spring Boot)
```

### Ключевые концепции

- **Ingress** — Kubernetes-ресурс, описывающий правила маршрутизации HTTP(S) трафика
- **Ingress Controller** — компонент (в нашем случае NGINX), реализующий правила Ingress
- **ingressClassName** — указывает, какой Ingress Controller должен обработать данный ресурс
- **host-based routing** — маршрутизация по HTTP-заголовку Host (разные hostname → разные Service)
- **path-based routing** — маршрутизация по пути URL (например, `/api` → один сервис, `/admin` → другой)

### NGINX Ingress Controller в Timeweb Cloud

При создании кластера с параметром `ingress = true` в Terraform, Timeweb Cloud автоматически устанавливает NGINX Ingress Controller в namespace `ingress-nginx`. Ему автоматически назначается публичный IP (через Service типа LoadBalancer).

```bash
# Найти публичный IP Ingress:
kubectl get svc -n ingress-nginx
# Смотрим EXTERNAL-IP у ingress-nginx-controller

# Посмотреть все Ingress ресурсы:
kubectl get ingress --all-namespaces

# Детали конкретного Ingress:
kubectl describe ingress greeting-service -n prod

# Логи Ingress Controller (для отладки):
kubectl logs -n ingress-nginx \
  -l app.kubernetes.io/name=ingress-nginx --tail=100
```

### Как настроить DNS

После получения Ingress IP нужно добавить A-запись в DNS вашего домена:

```
greeting.example.com.   IN  A  <INGRESS_IP>
```

Пока DNS не настроен, можно тестировать через `/etc/hosts` (только для локальной проверки):

```bash
echo "<INGRESS_IP> greeting.example.com" | sudo tee -a /etc/hosts
curl http://greeting.example.com/api/greeting
```

### Источники

Официальная документация Kubernetes Ingress: https://kubernetes.io/docs/concepts/services-networking/ingress/

> **EN:** An Ingress may be configured to give Services externally-reachable URLs, load balance traffic, terminate SSL / TLS, and offer name-based virtual hosting. Traffic routing is controlled by rules defined on the Ingress resource.

> **RU:** Ingress может быть настроен для предоставления Service внешнедоступных URL, балансировки нагрузки, терминации SSL/TLS и поддержки виртуального хостинга на основе имён. Маршрутизация трафика управляется правилами, определёнными в ресурсе Ingress.

---

## 10. Secrets и ConfigMaps: управление конфигурацией

### Аналогия

Представьте два вида документов в офисе:
- **ConfigMap** — публичный справочник с рабочей информацией: адреса, режим работы, правила. Любой сотрудник (под) может прочитать.
- **Secret** — сейф с конфиденциальными документами: пароли, токены, ключи. Доступ строго ограничен.

### Как работает технически

Kubernetes хранит оба типа в `etcd`. При запуске пода Kubernetes инжектирует значения в контейнер — либо как переменные окружения, либо как файлы в filesystem.

### Как значения из Secret попадают в Spring Boot приложение

```
kubectl create secret generic greeting-service-secret \
  --from-literal=DB_URL="jdbc:postgresql://..."
       │
       ▼
K8S хранит Secret в etcd (значения закодированы в base64)
       │
       ▼ При запуске пода
Kubernetes инжектирует переменные окружения в контейнер
       │
       ▼
Spring Boot читает: @Value("${DB_URL}") / application.yml: url: ${DB_URL}
       │
       ▼
JDBC подключается к PostgreSQL
```

В `deployment.yaml` (Helm template):

```yaml
envFrom:
  - secretRef:
      name: greeting-service-secret  # Все ключи из Secret → env vars контейнера
  - configMapRef:
      name: greeting-config          # Все ключи из ConfigMap → env vars контейнера
```

### Ключевые концепции

- **base64** — кодирование, не шифрование. Данные в Secret можно легко декодировать, зная base64.
- **etcd encryption at rest** — опциональное шифрование хранилища etcd (настраивается отдельно).
- **envFrom** — монтирование всех ключей из ConfigMap или Secret как переменных окружения.
- **secretKeyRef / configMapKeyRef** — монтирование конкретного ключа как переменной окружения.
- **volume mount** — монтирование Secret или ConfigMap как файла в filesystem контейнера.

### Практические команды

```bash
# Создать ConfigMap:
kubectl create configmap greeting-config \
  --namespace prod \
  --from-literal=APP_ENV="production" \
  --from-literal=LOG_LEVEL="INFO"

# Создать Secret:
kubectl create secret generic greeting-service-secret \
  --namespace prod \
  --from-literal=DB_URL="jdbc:postgresql://10.10.0.5:5432/greeting_db" \
  --from-literal=DB_PASSWORD="my_password"

# Прочитать ConfigMap:
kubectl get configmap greeting-config -n prod -o yaml

# Декодировать значение из Secret:
kubectl get secret greeting-service-secret -n prod \
  -o jsonpath='{.data.DB_PASSWORD}' | base64 -d
```

### Источники

Официальная документация Kubernetes Secrets: https://kubernetes.io/docs/concepts/configuration/secret/

> **EN:** A Secret is an object that contains a small amount of sensitive data such as a password, a token, or a key. Secrets are similar to ConfigMaps but are specifically intended to hold confidential data. Secrets can be mounted as data volumes or exposed as environment variables to be used by a container in a Pod.

> **RU:** Secret — это объект, содержащий небольшое количество чувствительных данных, таких как пароль, токен или ключ. Secrets похожи на ConfigMaps, но специально предназначены для хранения конфиденциальных данных. Secrets могут монтироваться как тома данных или передаваться как переменные окружения в контейнер внутри пода.

---

## 11. VPC и сетевая изоляция

### Аналогия

VPC (Virtual Private Cloud) — это как закрытый корпоративный офис внутри большого бизнес-центра. Весь бизнес-центр — это облако провайдера (Timeweb Cloud). Ваш офис — VPC: у него своя дверь, своя охрана, свои правила доступа. Снаружи видны только те окна и двери, которые вы намеренно открыли.

PostgreSQL — это сейф в глубине офиса: без публичного входа, доступен только тем, кто уже внутри.

### Как работает технически

VPC создаёт логически изолированный сегмент сети внутри публичного облака. Все ресурсы внутри VPC общаются по приватным IP-адресам и недоступны из интернета напрямую — если только вы явно не открыли к ним доступ через публичный IP или Ingress.

### Схема

<!--IMG:tech-vpc-network.png-->

### Наша сетевая топология

VPC `10.10.0.0/24` (изолировано от интернета):

```
Интернет
   │
   ├──► Ingress IP (публичный)
   │       └──► NGINX Ingress → поды K8S
   │
   └──► devtools VPS публичный IP
           ├── :7990 Bitbucket
           └── :5000 Docker Registry

VPC 10.10.0.0/24 (изолировано от интернета):
   ├── devtools VPS: 10.10.0.x
   ├── K8S worker-1: 10.10.0.y
   ├── K8S worker-2: 10.10.0.z
   └── PostgreSQL:   10.10.0.w (только внутри VPC!)
```

### Ключевые концепции

- **VPC** — Virtual Private Cloud, логически изолированная виртуальная сеть внутри публичного облака
- **subnet** — подсеть внутри VPC (диапазон IP-адресов для группы ресурсов)
- **CIDR** — нотация для описания диапазона IP-адресов (например, `10.10.0.0/24` = 256 адресов)
- **private IP** — адрес внутри VPC, недоступный из интернета
- **public IP** — адрес, доступный из интернета (назначается явно)
- **security group** — виртуальный firewall, контролирующий входящий и исходящий трафик

### Зачем PostgreSQL без публичного IP

PostgreSQL не имеет публичного IP — к нему можно подключиться только изнутри VPC. Это ключевой элемент безопасности: даже если злоумышленник знает пароль, он не сможет подключиться к базе данных снаружи. Приложение внутри кластера обращается к PostgreSQL по приватному IP (например, `10.10.0.w:5432`).

### Источники

Общее определение VPC (AWS документация): https://docs.aws.amazon.com/vpc/latest/userguide/how-it-works.html

> **EN:** A virtual private cloud (VPC) is a virtual network dedicated to your AWS account. It is logically isolated from other virtual networks in the AWS Cloud. You can specify an IP address range for the VPC, add subnets, add gateways, and associate security groups.

> **RU:** Virtual Private Cloud (VPC) — это виртуальная сеть, выделенная для вашего аккаунта. Она логически изолирована от других виртуальных сетей в облаке. Вы можете задать диапазон IP-адресов для VPC, добавить подсети, шлюзы и связать группы безопасности.
