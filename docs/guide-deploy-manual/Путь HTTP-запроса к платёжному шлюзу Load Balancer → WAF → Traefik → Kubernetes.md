# Путь HTTP-запроса к платёжному шлюзу Load Balancer → WAF → Traefik → Kubernetes

## Содержание

- [Главная идея](#главная-идея)
- [Пример запроса](#пример-запроса)
- [Участники цепочки](#участники-цепочки)
- [Прохождение запроса](#прохождение-запроса)
- [Где находится Traefik](#где-находится-traefik)
- [Минимальная лабораторная схема](#минимальная-лабораторная-схема)

## Главная идея

**Traefik не обязан быть первой публичной машиной в интернете.** В небольшой учебной инфраструктуре он может стоять на публичном VPS и быть первой точкой входа. В корпоративной или банковской системе перед ним обычно находятся другие компоненты: внешний балансировщик нагрузки (**Load Balancer**) и межсетевой экран для веб-приложений (**WAF, Web Application Firewall**).

В такой схеме Traefik остаётся маршрутизатором Kubernetes: он получает уже разрешённый запрос, смотрит на домен и путь и передаёт его нужному Kubernetes Service. Он не выполняет платёжную бизнес-логику и не заменяет WAF.

Типовой путь выглядит так:

```text
Клиент
  ↓
Внешний Load Balancer
  ↓
WAF
  ↓
Traefik Ingress Controller
  ↓
Kubernetes Service
  ↓
Pod с Spring Boot payment-gateway
```

---

## Пример запроса

Представим учебный платёжный шлюз. Интернет-магазин отправляет запрос на создание платежа:

```http
POST https://api.pay.example.com/api/v1/payments
Authorization: Bearer <JWT-токен>
Content-Type: application/json
Idempotency-Key: 7c72af86-...

{
  "orderId": "order-100045",
  "amount": 2590.00,
  "currency": "RUB"
}
```

DNS-запись домена `api.pay.example.com` указывает на публичный IP **внешнего Load Balancer**, а не на IP Pod, Kubernetes Node или Spring Boot-приложения.

Например:

```text
api.pay.example.com → 203.0.113.10
```

Клиент знает только домен и публичный IP. Он не должен знать, где расположены Pod’ы, сколько у приложения реплик и как устроен кластер.

---

## Участники цепочки

| Компонент | Что это такое | Ответственность |
|---|---|---|
| Load Balancer | Устройство, VM или облачный сервис балансировки | Принимает соединение на публичном IP и направляет его на доступный следующий узел |
| WAF | Web Application Firewall, защита HTTP/HTTPS-приложений | Анализирует запрос и блокирует типовые веб-атаки |
| Traefik | Reverse proxy и Ingress Controller | Маршрутизирует запрос по `Host` и `Path` к Kubernetes Service |
| Kubernetes Service | Внутренний объект Kubernetes, обычно `ClusterIP` | Даёт стабильное имя сервису и распределяет запросы между Pod’ами |
| Pod | Запущенный экземпляр контейнера | Выполняет Java/Spring Boot бизнес-логику |

### Load Balancer

Load Balancer — это балансировщик нагрузки. Он может быть отдельным корпоративным решением, например **F5 BIG-IP**, либо managed-сервисом облака: AWS Network Load Balancer, AWS Application Load Balancer, Azure Load Balancer и т. п.

Его основная задача — не направлять все соединения на одну машину. Допустим, у компании есть два WAF-узла: `waf-01` и `waf-02`. Балансировщик проверяет их health check-ами. Если `waf-01` перестал отвечать, новые запросы уйдут на `waf-02`.

```text
Клиент
  ↓ HTTPS
Load Balancer: 203.0.113.10:443
  ↓ HTTPS
waf-01.internal.example.com
или
waf-02.internal.example.com
```

AWS в примере с Kubernetes показывает эту же идею: Ingress Controller публикуется наружу через Load Balancer. Это означает, что внешний балансировщик и Ingress Controller не конкурируют — они могут работать последовательно и выполнять разные роли.

- Источник: https://aws.amazon.com/blogs/containers/exposing-kubernetes-applications-part-3-nginx-ingress-controller/

EN:

> "The Ingress-Nginx Controller Service is exposed for external traffic via a load balancer."

RU:

> «Service контроллера Ingress-Nginx публикуется для внешнего трафика через балансировщик нагрузки».

### WAF

**WAF** — это сокращение от **Web Application Firewall**, то есть межсетевой экран для веб-приложений. Это не просто образное название «охраны», а конкретный класс продуктов и сервисов.

WAF анализирует HTTP/HTTPS-запрос: URL, query-параметры, заголовки, cookies и иногда тело запроса. Он применяет правила безопасности и может заблокировать подозрительный запрос до его попадания в Traefik, Kubernetes и Spring Boot.

Например, клиент отправил значение, похожее на SQL-инъекцию:

```json
{
  "orderId": "1' OR '1'='1",
  "amount": 2590.00
}
```

WAF может ответить сам:

```http
HTTP/1.1 403 Forbidden
```

При этом backend всё равно обязан быть безопасным: использовать параметризованные SQL-запросы, валидировать входные данные и выполнять авторизацию. WAF — дополнительный защитный слой, а не замена безопасной разработке.

WAF бывает нескольких видов:

- Аппаратный или виртуальный appliance в дата-центре, например F5 BIG-IP с WAF-функциями.
- Программный WAF на VM или в контейнерах, например F5 NGINX App Protect или ModSecurity.
- Облачный managed WAF, например AWS WAF, Cloudflare WAF или Azure WAF.
- WAF, интегрированный рядом с NGINX/Ingress-контроллером.

F5 определяет WAF как механизм фильтрации, мониторинга и блокировки вредоносного HTTP/S-трафика; в том числе он предназначен для защиты от XSS и SQL injection.

- Источник: https://www.f5.com/glossary/web-application-firewall-waf

EN:

> "A WAF protects your web apps by filtering, monitoring, and blocking any malicious HTTP/S traffic traveling to the web application."

RU:

> «WAF защищает веб-приложения, фильтруя, отслеживая и блокируя вредоносный HTTP/S-трафик, направленный к веб-приложению».

Простое различие ролей:

```text
WAF:      «Этот HTTP-запрос вообще можно пропустить?»
Traefik:  «В какой Kubernetes Service отправить уже разрешённый запрос?»
```

### Traefik

Traefik — это reverse proxy и Ingress Controller. В рассматриваемой корпоративной схеме он обычно находится в Kubernetes либо в приватной сети перед кластером, а не является первой машиной, доступной всему интернету.

Traefik может быть запущен несколькими репликами и иметь доступ к Kubernetes API. Он читает правила `Ingress` и по ним выбирает Service.

Пример правила:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: payment-gateway
spec:
  ingressClassName: traefik
  rules:
    - host: api.pay.example.com
      http:
        paths:
          - path: /api/v1/payments
            pathType: Prefix
            backend:
              service:
                name: payment-gateway
                port:
                  number: 8080
```

Получив запрос с параметрами:

```text
Host: api.pay.example.com
Path: /api/v1/payments
Method: POST
```

Traefik находит совпадение и пересылает запрос в:

```text
payment-gateway Service:8080
```

Ingress в Kubernetes задаёт HTTP/HTTPS-маршруты снаружи к Service внутри кластера. Для реальной работы правил необходим Ingress Controller — например Traefik; одного YAML-ресурса Ingress недостаточно.

- Источник: https://kubernetes.io/docs/concepts/services-networking/ingress/

EN:

> "Ingress exposes HTTP and HTTPS routes from outside the cluster to services within the cluster. Traffic routing is controlled by rules defined on the Ingress resource."

RU:

> «Ingress предоставляет HTTP- и HTTPS-маршруты извне к сервисам внутри кластера. Маршрутизацией трафика управляют правила, определённые в ресурсе Ingress».

- Источник: https://kubernetes.io/docs/concepts/services-networking/ingress/

EN:

> "You must have an Ingress controller to satisfy an Ingress. Only creating an Ingress resource has no effect."

RU:

> «Чтобы Ingress работал, необходим Ingress Controller. Само по себе создание ресурса Ingress не даёт никакого эффекта».

---

## Прохождение запроса

### Шаг 1. DNS и публичный IP

Клиент открывает `https://api.pay.example.com/api/v1/payments`. DNS возвращает публичный IP Load Balancer — например `203.0.113.10`. Клиент устанавливает TLS-соединение с этим IP на порту `443`.

На этом этапе внешний клиент не видит Kubernetes и не имеет прямого доступа к Node, Service или Pod.

### Шаг 2. Внешний балансировщик

Load Balancer принимает соединение и выбирает здоровый WAF-узел. Если один WAF-инстанс недоступен, он исключается из пула.

```text
Клиент → Load Balancer → waf-01
                         или waf-02
```

### Шаг 3. WAF

WAF проверяет URL, заголовки и тело HTTP-запроса. Если запрос нарушает политику безопасности, WAF завершает обработку и возвращает клиенту ошибку, например `403 Forbidden`. Если запрос нормальный, WAF передаёт его дальше.

### Шаг 4. Traefik

WAF проксирует разрешённый запрос на внутренний адрес Traefik:

```text
waf-01 → traefik.kube-platform.internal:443
```

Traefik можно закрыть firewall-правилами от прямого интернета: разрешить входящий трафик на `443` только от WAF или Load Balancer. В этом случае он является входом **в Kubernetes-платформу**, но не первой публичной точкой компании.

### Шаг 5. Маршрутизация в Service

Traefik сопоставляет `Host` и `Path` с правилами Ingress:

```text
api.pay.example.com + /api/v1/payments
  ↓
payment-gateway Service:8080
```

Если совпадения нет, типичный результат — `404 Not Found`. Если правило найдено, но доступных Pod’ов нет, может быть `503 Service Unavailable`.

Kubernetes указывает, что Service выбирается только при совпадении и `host`, и `path` с входящим запросом.

- Источник: https://kubernetes.io/docs/concepts/services-networking/ingress/

EN:

> "Both the host and path must match the content of an incoming request before the load balancer directs traffic to the referenced Service."

RU:

> «И host, и path должны соответствовать входящему запросу, прежде чем балансировщик направит трафик к указанному Service».

### Шаг 6. Service и Pod

Traefik отправляет запрос в Kubernetes Service, а не на IP конкретного Pod:

```text
payment-gateway.default.svc.cluster.local:8080
```

Например, за этим Service могут стоять три Pod-реплики:

```text
payment-gateway-...-abc12
payment-gateway-...-def34
payment-gateway-...-ghi56
```

Kubernetes направит запрос в готовую реплику. Если одна реплика упала и перестала проходить readiness-проверку, она должна быть исключена из списка доступных endpoints.

### Шаг 7. Spring Boot

Только теперь запрос приходит в Spring Boot-приложение, например в endpoint:

```java
@PostMapping("/api/v1/payments")
public PaymentResponse createPayment(
        @RequestBody PaymentRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey) {
    return paymentService.createPayment(request, idempotencyKey);
}
```

Здесь уже выполняется прикладная логика: валидация, проверка пользователя, идемпотентность, запись операции в PostgreSQL, взаимодействие с antifraud или внешним процессингом, отправка событий в Kafka и формирование ответа.

---

## Где находится Traefik

| Вариант | Положение Traefik | Когда подходит |
|---|---|---|
| Учебный или маленький проект | На публичном VPS; Traefik доступен из интернета | Один кластер, мало сервисов, нет отдельного WAF/F5 |
| Обычный production | В Kubernetes или приватной сети за внешним Load Balancer | Несколько реплик, требуется отказоустойчивость |
| Банк / крупный enterprise | За Load Balancer и WAF; иногда также за API Gateway | Высокие требования к безопасности, аудиту и сегментации сети |

Для текущего `greeting-service-infra` нормальна простая схема:

```text
Интернет
  ↓
Публичный VPS с Traefik
  ↓
k3s Service
  ↓
greeting-service Pod
```

Для платёжного шлюза production-уровня более типична схема:

```text
Интернет
  ↓
Публичный Load Balancer
  ↓
WAF
  ↓
Traefik Ingress Controller
  ↓
Kubernetes Service
  ↓
Spring Boot Pod
```

Итоговая формулировка: **в маленькой системе Traefik может быть первой дверью из интернета. В корпоративной системе он чаще является последней управляемой дверью непосредственно перед сервисами Kubernetes; перед ним стоят Load Balancer и WAF.**

---

## Минимальная лабораторная схема

Чтобы пощупать эту архитектуру без F5 и дорогого корпоративного оборудования, можно собрать такой вариант:

```text
Интернет
  ↓
Cloudflare или managed WAF провайдера
  ↓
Публичный VPS с Traefik
  ↓
Private network / VPN
  ↓
k3s-кластер
  ↓
payment-gateway Service
  ↓
payment-gateway Pod
```

Роли в такой лаборатории будут теми же:

- Cloudflare или аналог — внешний WAF-слой.
- Traefik — маршрутизация домена и пути в Kubernetes.
- Kubernetes Service — стабильная внутренняя точка доступа к Pod’ам.
- Spring Boot — бизнес-логика платёжного шлюза.
- PostgreSQL, Kafka и внутренние сервисы — приватные зависимости без прямого внешнего доступа.
