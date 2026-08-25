# Часть III: CI/CD и развёртывание в Рег.облаке (reg.cloud), БЕЗ Terraform

*GitLab · Runner · Kubernetes · Helm · Рег.облако (reg.ru cloud) — ручное создание инфраструктуры*

Версия: 1.0 | 2026-08 | Целевая аудитория: backend developer middle+

Все описания — на русском языке. Технические термины — на английском. Команды — на английском.

> **Смена провайдера.** Предыдущая адаптация была сделана под Timeweb Cloud. Пользователь уточнил: работаем с **Рег.облако** (**reg.cloud**, облачная платформа reg.ru). Ниже — та же логика замены (Раздел 9 исходного документа «Создание инфраструктуры через Terraform» + все Terraform-специфичные упоминания), но под сервисы конкретно **Рег.облака**: **Облачные серверы**, **Кластеры Kubernetes** (Managed Kubernetes / KaaS), **PostgreSQL облачная база данных** (DBaaS), **Объектное хранилище S3**, **Приватные сети (VPC)**. Все остальные разделы оригинального документа (6–8, 10a–23: локальная разработка, GitLab CE, Docker Registry, GitLab Runner, Helm chart, CI/CD pipeline, стратегия окружений, PostgreSQL-подключение из Spring Boot, безопасность, эксплуатация, диагностика) применяются **без изменений**, потому что они работают через SSH на уже готовом сервере и через Kubernetes API (`kubectl`/`helm`), не завися от того, какой облачный провайдер и каким способом создал инфраструктуру.

## Оглавление

1. [Почему Рег.облако и что меняется](#раздел-0)
2. [Приватная сеть (VPC) в Рег.облаке](#раздел-1)
3. [Devtools-сервер (VPS) в Рег.облаке](#раздел-2)
4. [Managed Kubernetes в Рег.облаке](#раздел-3)
5. [Managed PostgreSQL в Рег.облаке](#раздел-4)
6. [Объектное хранилище S3 в Рег.облаке](#раздел-5)
7. [Получение kubeconfig в Рег.облаке](#раздел-6)
8. [API Рег.облака: краткий обзор](#раздел-7)
9. [Сводная таблица замены ресурсов](#раздел-8)
10. [Что остаётся без изменений](#раздел-9)
11. [Итоговый порядок первого запуска](#раздел-10)

---

<a id="раздел-0"></a>
## Раздел 0. Почему Рег.облако и что меняется

Рег.облако — облачная платформа reg.ru, построенная на технологиях **OpenStack, Kubernetes и KVM**:

> «Инфраструктура облачных технологий построена на OpenStack, Kubernetes и KVM.»

- Источник: [https://reg.cloud/cloud/](https://reg.cloud/cloud/)

В отличие от Timeweb Cloud, у Рег.облака нет официального Terraform-провайдера, но зато есть полноценный **REST API** для автоматизации:

> «API для облачных серверов Рег.облака: методы, параметры запросов и примеры интеграции... Через API можно выполнять те же операции, что и через панель управления. Используя его, вы сможете встраивать функции облачных VPS в собственные интернет-проекты.»

- Источник: [https://reg.cloud/support/cloud/oblachnyye-servery/rabota-s-serverom/api-dlya-oblachnykh-serverov](https://reg.cloud/support/cloud/oblachnyye-servery/rabota-s-serverom/api-dlya-oblachnykh-serverov)

Полная документация API (аналог Terraform Registry для этого провайдера — это то место, куда обращались бы Terraform-провайдеры, если бы они существовали):

- Источник: [https://developers.cloudvps.reg.ru/](https://developers.cloudvps.reg.ru/)

> «API предоставляет вам возможность автоматизировать многие операции: управлять виртуальными серверами (создание, удаление, перезагрузка и проч.), управлять снимками серверов, получать информацию о заказанных услугах, тарифах, статистике, создавать приватные сети, добавлять в них серверы и прочее.»

- Источник: [https://developers.cloudvps.reg.ru/](https://developers.cloudvps.reg.ru/)

Поскольку задача — работать **без Terraform**, архитектура (VPC → devtools-VPS → Kubernetes-кластер → Managed PostgreSQL → S3 → GitLab → CI/CD → Helm-деплой) остаётся идентичной оригинальному документу, меняется только способ создания каждого ресурса: вместо `.tf`-файлов — панель управления Рег.облака и, при необходимости, прямые запросы к API.

---

<a id="раздел-1"></a>
## Раздел 1. Приватная сеть (VPC) в Рег.облаке

**Что делал Terraform в оригинале:** `twc_vpc greeting-service-vpc` с подсетью `10.10.0.0/24`.

**Особенность Рег.облака:** приватная сеть (VPC) и подсеть для неё создаются **автоматически** при заказе первого сервера в регионе — отдельно создавать VPC заранее обычно не требуется:

> «При заказе сервера одна приватная облачная сеть (VPC) и подсеть для неё создаются автоматически. Эта подсеть будет использоваться по умолчанию для всех новых серверов в том же регионе. Поэтому, если вы закажете новый сервер в другом регионе, после активации услуги будет создана отдельная изолированная сеть и подсеть для неё.»

- Источник: [https://reg.cloud/services/vpc-networks](https://reg.cloud/services/vpc-networks)

**Что делаете вы, если нужна дополнительная или отдельная сеть:**

1. Войдите в панель управления Рег.облака.
2. Перейдите **Мои ресурсы → Приватные сети**.
3. Нажмите **Создать приватную сеть**, задайте название (например, `greeting-service-vpc`) и подтвердите создание.

- Источник: [https://reg.cloud/support/cloud/oblachnyye-servery/rabota-s-serverom/privatnye-seti-i-podseti-dlya-oblachnyh-serverov](https://reg.cloud/support/cloud/oblachnyye-servery/rabota-s-serverom/privatnye-seti-i-podseti-dlya-oblachnyh-serverov)

Для более старых тарифов (архивных VPS) сети создаются через отдельную вкладку:

> «Перейдите на вкладку Сети архивных тарифов VPS → Нажмите Создать приватную сеть → Введите название и создайте приватную сеть.»

- Источник: [https://reg.cloud/support/cloud/oblachnyye-servery/rabota-s-serverom/nastroyka-privatnoy-seti-na-oblachnom-servere](https://reg.cloud/support/cloud/oblachnyye-servery/rabota-s-serverom/nastroyka-privatnoy-seti-na-oblachnom-servere)

Практический вывод: в большинстве сценариев отдельный шаг «создать VPC» можно пропустить — достаточно заказать devtools-сервер и Kubernetes-кластер **в одном регионе**, чтобы они автоматически оказались в общей приватной сети.

---

<a id="раздел-2"></a>
## Раздел 2. Devtools-сервер (VPS) в Рег.облаке

**Что делал Terraform в оригинале:** `twc_server devtools` — VPS 4 CPU / 8 ГБ RAM / 100 ГБ, Ubuntu, с автоустановкой Docker + JDK 17 + Nginx через cloud-init.

**Что делаете вы:**

1. Зарегистрируйтесь или авторизуйтесь в панели Рег.облака.
2. Нажмите **Новый ресурс → Сервер**.
3. Выберите операционную систему **Ubuntu 22.04**.
4. Подберите конфигурацию, близкую к оригиналу — 4 vCPU / 8 ГБ RAM / 100 ГБ SSD (в тарифной линейке Рег.облака конфигурации называются, например, `C4-M8-D80` и аналогичные — уточните точное соответствие в калькуляторе при заказе).
5. На этапе заказа добавьте свой публичный SSH-ключ (`~/.ssh/id_ed25519.pub`) — это заменяет ресурс `twc_ssh_key operator` из Terraform.
6. Если панель поддерживает **cloud-init / user data**, вставьте туда содержимое скрипта `infra/terraform/scripts/devtools-init.sh` из исходного документа.
7. Нажмите **Создать**.

- Источник: [https://reg.cloud/support/cloud/oblachnyye-servery/zakaz-i-upravleniye-uslugoy-oblachnyye-servery/upravleniye-uslugoy-oblachnyye-servery](https://reg.cloud/support/cloud/oblachnyye-servery/zakaz-i-upravleniye-uslugoy-oblachnyye-servery/upravleniye-uslugoy-oblachnyye-servery)

Если cloud-init на этапе заказа недоступен, выполните тот же скрипт вручную после первого SSH-подключения (по паролю, который придёт на почту, либо по вашему ключу, если он был добавлен при заказе):

```bash
ssh root@<PUBLIC_IP>
bash -s < infra/terraform/scripts/devtools-init.sh
```

**Через API (аналог `terraform apply` для этого одного ресурса):**

Полная документация методов доступна в Swagger по адресу [developers.cloudvps.reg.ru](https://developers.cloudvps.reg.ru/). Общий вид запроса на создание сервера:

```bash
curl -X POST "https://api.cloudvps.reg.ru/v1/servers" \
  -H "Authorization: Bearer ${REGCLOUD_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "greeting-service-devtools",
    "image": "ubuntu-22.04",
    "flavor": "C4-M8-D80",
    "ssh_key_id": "<ваш_ssh_key_id>"
  }'
```

API-ключ для работы с Облачными серверами создаётся автоматически и доступен в разделе **Настройки** окружения облачных серверов:

> «Ключ API используется для идентификации при работе с облачным окружением через API... API-ключ создается автоматически и доступен в окружении облачных серверов во вкладке Настройки.»

- Источник: [https://reg.cloud/support/cloud/oblachnyye-servery/rabota-s-serverom/api-dlya-oblachnykh-serverov](https://reg.cloud/support/cloud/oblachnyye-servery/rabota-s-serverom/api-dlya-oblachnykh-serverov)

Обратите внимание: точные названия полей запроса (`image`, `flavor` и т.д.) нужно сверить с актуальной Swagger-схемой на [developers.cloudvps.reg.ru](https://developers.cloudvps.reg.ru/) перед использованием — пример выше показывает общий подход, а не гарантированно точный контракт API.

Все дальнейшие разделы оригинального документа (10a — установка GitLab CE и Docker Registry, 11a — регистрация GitLab Runner) выполняются **абсолютно так же**, как в оригинале, — они работают через SSH на уже созданном сервере и не зависят от провайдера.

---

<a id="раздел-3"></a>
## Раздел 3. Managed Kubernetes в Рег.облаке

**Что делал Terraform в оригинале:** `twc_k8s_cluster` + `twc_k8s_node_group workers` (2 worker-узла).

У Рег.облака есть собственный сервис **Managed Kubernetes (KaaS)** с очень быстрым временем создания:

> «Создание услуги с Kubernetes (managed Kubernetes) занимает не более 5 минут. После этого вы сразу можете приступить к работе над кластером... Кластер Kubernetes подстраивается под ваши потребности в режиме реального времени. Например, при пиковых нагрузках создаются дополнительные узлы... Обслуживанием оборудования и обновлением версии Kubernetes кластера занимаемся мы — дополнительных действий от вас не потребуется.»

- Источник: [https://reg.cloud/services/managed-kubernetes](https://reg.cloud/services/managed-kubernetes)

**Что делаете вы:**

1. В панели Рег.облака откройте раздел **Кластеры Kubernetes**.
2. Выберите **Тип кластера**: Стандартный.
3. Выберите **Версию Kubernetes** (на момент проверки была доступна, например, `1.31.1` — уточните актуальную версию в конфигураторе при заказе).
4. Настройте **группу нод** — в тарифной линейке доступны готовые конфигурации:

| Тариф | vCPU | RAM | Диск | Цена |
|---|---|---|---|---|
| C2-M4-D40 | 2 ядра | 4 ГБ | 40 ГБ SSD | ~1 560 ₽/мес (2,17 ₽/час) |
| C4-M8-D80 | 4 ядра | 8 ГБ | 80 ГБ SSD | ~3 120 ₽/мес (4,35 ₽/час) |
| C8-M16-D120 | 8 ядер | 16 ГБ | 120 ГБ SSD | ~5 760 ₽/мес (8,02 ₽/час) |

- Источник: [https://reg.cloud/services/managed-kubernetes](https://reg.cloud/services/managed-kubernetes)

5. Выберите количество нод в группе (для аналога оригинального документа — 2 воркер-ноды, например по `C2-M4-D40`, что близко к 2 CPU / 4 ГБ RAM / 50 ГБ из Terraform-конфигурации).
6. При необходимости внешнего доступа к сервисам подключите **Балансировщик нагрузки** — в Рег.облаке он создаётся не отдельным ресурсом в панели, а прямо из `kubectl`:

> «Подключение балансировщика осуществляется в kubectl путем создания ресурса Service с типом LoadBalancer.»

- Источник: [https://reg.cloud/services/managed-kubernetes](https://reg.cloud/services/managed-kubernetes)

Это отличается от Timeweb Cloud, где Ingress работал через hostNetwork worker-узлов без отдельного LoadBalancer — в Рег.облаке можно (и обычно нужно) создать полноценный `Service` типа `LoadBalancer` для NGINX Ingress Controller, и тогда `EXTERNAL-IP` появится сразу в `kubectl get svc -n ingress-nginx`, без обходного пути через `EXTERNAL-IP` worker-узла, который использовался в оригинальном документе для Timeweb Cloud.

7. Нажмите **Создать**. Разделение ответственности зафиксировано официально:

> «Рег.облако: развертывание и настройка кластера, обновление версии Kubernetes и компонентов, мониторинг и восстановление при сбоях, обеспечение физической безопасности ЦОД. Клиент: управление приложениями и контейнерами, контроль версий и сборка образов, настройка прав доступа для команды, конфигурация CI/CD и деплой приложений.»

- Источник: [https://reg.cloud/services/managed-kubernetes](https://reg.cloud/services/managed-kubernetes)

Подробная инструкция по заказу и управлению кластером есть в базе знаний Рег.облака (раздел «Заказ и управление услугой "Кластеры Kubernetes"» и «Управление кластером Kubernetes»), доступной прямо со страницы сервиса:

- Источник: [https://reg.cloud/services/managed-kubernetes](https://reg.cloud/services/managed-kubernetes)

---

<a id="раздел-4"></a>
## Раздел 4. Managed PostgreSQL в Рег.облаке

**Что делал Terraform в оригинале:** `twc_database_cluster` + `twc_database_instance` + `twc_database_user`.

У Рег.облака есть отдельный сервис **облачной базы данных PostgreSQL (DBaaS)**, построенный на managed-кластере Kubernetes с автоматическим failover:

> «Услуга построена на базе управляемого кластера kubernetes с автоматическим failover-механизмом мастер-реплики. Реплики размещаются на независимых физических серверах в разных зонах доступности.»

- Источник: [https://www.reg.ru/company/news/12623](https://www.reg.ru/company/news/12623)

**Что делаете вы:**

1. В панели Рег.облака откройте **Облачная база данных PostgreSQL**.
2. Выберите версию (доступна, например, PostgreSQL 17).
3. Выберите тариф из линейки:

| Тариф | vCPU | RAM | Диск | Цена |
|---|---|---|---|---|
| C1-M1-D20 | 1 ядро | 1 ГБ | 20 ГБ SSD | ~784 ₽/мес |
| C2-M4-D80 | 2 ядра | 4 ГБ | 80 ГБ SSD | ~2 672 ₽/мес |
| C4-M8-D120 | 4 ядра | 8 ГБ | 120 ГБ SSD | ~4 704 ₽/мес |

- Источник: [https://reg.cloud/services/postgresql](https://reg.cloud/services/postgresql)

4. Настройте количество реплик (от 0 до 5) — это заменяет ручное планирование отказоустойчивости, которое в Terraform задавалось бы отдельным параметром `replications`:

> «При создании кластера можно заказать от 1 до 5 реплик. Реплики повышают отказоустойчивость ваших баз данных. Автоматический запуск failover-механизма мастера при подключенных репликах.»

- Источник: [https://reg.cloud/services/postgresql](https://reg.cloud/services/postgresql)

5. В настройках кластера создайте базу данных `greeting_db` и пользователя `greeting_user` — панель позволяет задать локаль и расширенные настройки PostgreSQL.
6. Нажмите **Создать** — кластер запускается за считанные секунды/минуты:

> «Облачная база данных PostgreSQL создается и запускается за считанные секунды.»

- Источник: [https://reg.cloud/services/postgresql](https://reg.cloud/services/postgresql)

7. После создания host, порт и учётные данные для подключения видны на странице кластера в панели — эти значения заменяют `terraform output db_host` и `terraform output db_port` из оригинального документа.

Как и в случае с Timeweb Cloud, если позже понадобятся Flyway-миграции с `CREATE SCHEMA`, убедитесь, что пользователю `greeting_user` выданы права `CREATE`/`DROP`/`ALTER` — в Рег.облаке это делается на той же странице настройки пользователей БД.

Инструкция по подключению к базе (аналог JDBC-строки, использованной в оригинальном документе):

- Источник: [https://reg.cloud/services/postgresql](https://reg.cloud/services/postgresql) — раздел «Документация, инструкции и статьи по PostgreSQL» → «Как подключиться к базе данных PostgreSQL»

---

<a id="раздел-5"></a>
## Раздел 5. Объектное хранилище S3 в Рег.облаке

**Что делал Terraform в оригинале:** `twc_s3_bucket artifacts`.

**Что делаете вы:**

1. Войдите в панель управления Рег.облака.
2. Нажмите **Новый ресурс** и выберите **Бакет хранилища S3**.
3. Укажите название бакета, например `greeting-service-artifacts`.
4. При необходимости задайте максимальный размер бакета.
5. Выберите тип доступа к объектам:
   - **По ключам** — требует аутентификации парой Access Key ID / Secret Key (аналог `s3_access_key`/`s3_secret_key` из Terraform outputs);
   - **Открыт для всех** — публичный доступ по URL без авторизации.
6. Нажмите **Создать бакет**.

- Источник: [https://reg.cloud/support/cloud/obyektnoye-khranilishche-s3/zakaz-i-upravlenie-uslugoj-obektnoe-hranilishche-s3/zakaz-i-upravleniye-obyektnym-khranilishchem-s3](https://reg.cloud/support/cloud/obyektnoye-khranilishche-s3/zakaz-i-upravlenie-uslugoj-obektnoe-hranilishche-s3/zakaz-i-upravleniye-obyektnym-khranilishchem-s3)

Хранилище доступно по стандартному S3-совместимому endpoint:

> «https://s3.regru.cloud/bucket_name/object_id»

- Источник: [https://reg.cloud/support/instrukcii/obektnoe-hranilishe-s3/sposoby-dostupa-k-faylam-v-s3](https://reg.cloud/support/instrukcii/obektnoe-hranilishe-s3/sposoby-dostupa-k-faylam-v-s3)

Пример проверки доступа через AWS CLI (совместимость с S3 API означает, что стандартные инструменты работают без модификаций):

```bash
aws s3api put-object-acl --bucket bucket_name --key object_id --acl public-read \
  --endpoint-url https://s3.regru.cloud
```

- Источник: [https://reg.cloud/support/instrukcii/obektnoe-hranilishe-s3/sposoby-dostupa-k-faylam-v-s3](https://reg.cloud/support/instrukcii/obektnoe-hranilishe-s3/sposoby-dostupa-k-faylam-v-s3)

Access Key и Secret Key бакета доступны на странице бакета в разделе **Мои ресурсы → Хранилище S3** — это заменяет вывод `terraform output s3_access_key` / `s3_secret_key` из оригинального документа.

---

<a id="раздел-6"></a>
## Раздел 6. Получение kubeconfig в Рег.облаке

В Terraform-версии документа kubeconfig был output, сохраняемый командой `terraform output -raw kubeconfig > ~/.kube/timeweb-greeting.yaml`. Без Terraform в Рег.облаке kubeconfig скачивается напрямую со страницы кластера:

1. Откройте созданный кластер в разделе **Кластеры Kubernetes**.
2. Найдите раздел управления кластером (базовый flow подтверждён официальной документацией «Управление кластером Kubernetes», ссылка доступна со страницы сервиса — [https://reg.cloud/services/managed-kubernetes](https://reg.cloud/services/managed-kubernetes)).
3. Скачайте файл конфигурации доступа (kubeconfig) и сохраните под тем же именем, что использовалось в оригинальном документе, чтобы все последующие разделы (12–20) продолжали работать без изменений:

```bash
mkdir -p ~/.kube
mv ~/Downloads/kubeconfig.yaml ~/.kube/regcloud-greeting.yaml
chmod 600 ~/.kube/regcloud-greeting.yaml
export KUBECONFIG=~/.kube/regcloud-greeting.yaml
kubectl get nodes
```

> Обратите внимание на переименование файла: `regcloud-greeting.yaml` вместо `timeweb-greeting.yaml` — везде далее в командах, скопированных из оригинального документа (например, в `scripts/get-kubeconfig.sh`, в `~/.bashrc`, в примерах `kubectl --kubeconfig ...`), нужно заменить это имя файла на актуальное.

Общий принцип загрузки kubeconfig одинаков для managed Kubernetes любого провайдера — kubeconfig скачивается из консоли/панели проекта и указывается через флаг `--kubeconfig` или переменную `KUBECONFIG`:

> «Your project kubeconfig (downloaded from the console or provided by your administrator)... Download Your Project kubeconfig: Log in to the console, Navigate to your project, Click Settings or Access, Click Download kubeconfig. Save the file, for example as ~/.kube/my-project.yaml.»

- Источник: [https://docs.stakater.com/stakater-cloud-orchestrator/latest/cloud-user-guide/kubectl-access/setup-kubectl.html](https://docs.stakater.com/stakater-cloud-orchestrator/latest/cloud-user-guide/kubectl-access/setup-kubectl.html)

RU: «Ваш kubeconfig проекта (скачанный из консоли или предоставленный администратором)... Шаг: скачать kubeconfig проекта: войдите в консоль, перейдите в свой проект, откройте раздел Settings или Access, нажмите Download kubeconfig. Сохраните файл, например, как ~/.kube/my-project.yaml.»

Как только файл сохранён и переменная `KUBECONFIG` указывает на него, **весь остальной оригинальный документ** (Разделы 12–20: развёртывание, ручное управление kubectl, Helm chart, CI/CD pipeline, эксплуатация, диагностика) работает без изменений.

---

<a id="раздел-7"></a>
## Раздел 7. API Рег.облака: краткий обзор

Если требуется автоматизация уровня Terraform (например, скрипт, который одной командой поднимает VPS + проверяет статус), используйте REST API Рег.облака напрямую:

- Портал разработчика и Swagger-документация: [https://developers.cloudvps.reg.ru/](https://developers.cloudvps.reg.ru/)
- Есть неофициальная Python-библиотека-обёртка для API (полезна как справочник структур запросов):

> «Неофициальная библиотека для работы с API услуги Reg.ru Облачные VPS. Документация по API Reg.ru Облачные VPS — developers.cloudvps.reg.ru.»

- Источник: [https://github.com/plvskiy/regru_cloudapi](https://github.com/plvskiy/regru_cloudapi)

Общая логика работы с API одинакова для всех разделов панели:

> «API предоставляет вам возможность автоматизировать многие операции... Через API можно выполнять те же операции, что и через панель управления.»

- Источник: [https://reg.cloud/support/cloud/oblachnyye-servery/rabota-s-serverom/api-dlya-oblachnykh-serverov](https://reg.cloud/support/cloud/oblachnyye-servery/rabota-s-serverom/api-dlya-oblachnykh-serverov)

Практический пример из блога Рег.ру, подтверждающий доступность полного цикла управления сервером через API:

> «Все действия с сервером можно делать и через API: например, быстро создать новый облачный VDS сервер, проапгрейдить, перезагрузить или остановить его. Описание API можно найти здесь: developers.cloudvps.reg.ru»

- Источник: [https://reg.cloud/blog/10-fishek-oblachnyh-serverov-reg-ru/](https://reg.cloud/blog/10-fishek-oblachnyh-serverov-reg-ru/)

Такой API-скрипт может частично заменить привычный `terraform apply` — например, bash-обёртка, которая последовательно вызывает `POST /servers`, дожидается готовности через `GET /servers/{id}`, затем выводит IP в терминал, аналогично тому, как `terraform apply` в оригинале выводил `devtools_public_ip` в блоке `Outputs`.

---

<a id="раздел-8"></a>
## Раздел 8. Сводная таблица замены ресурсов

| Ресурс Terraform (оригинал, Timeweb Cloud) | Сервис в Рег.облаке | Где заказать |
|---|---|---|
| `twc_vpc` | Приватная сеть (VPC) — часто создаётся автоматически | Мои ресурсы → Приватные сети |
| `twc_server devtools` | Облачный сервер | Новый ресурс → Сервер |
| `twc_ssh_key` | SSH-ключ при заказе сервера | Форма заказа сервера, поле SSH-ключ |
| `twc_k8s_cluster` + `twc_k8s_node_group` | Кластеры Kubernetes (Managed Kubernetes / KaaS) | Раздел «Кластеры Kubernetes» |
| `twc_database_cluster` + `twc_database_instance` + `twc_database_user` | Облачная база данных PostgreSQL (DBaaS) | Раздел «PostgreSQL» |
| `twc_s3_bucket` | Объектное хранилище S3 | Новый ресурс → Бакет хранилища S3 |
| `terraform output kubeconfig` | Скачивание kubeconfig со страницы кластера | Кластеры Kubernetes → страница кластера |
| `terraform output devtools_public_ip` | IP сервера в карточке ресурса | Мои ресурсы → карточка сервера |
| `terraform output s3_access_key/secret_key` | Ключи доступа бакета | Мои ресурсы → Хранилище S3 → страница бакета |
| `dns.tf` | DNS-записи домена | У внешнего регистратора (как и в оригинале для случая, когда NS не делегированы провайдеру) |

---

<a id="раздел-9"></a>
## Раздел 9. Что остаётся без изменений

Как и в адаптации под Timeweb Cloud, подавляющая часть исходного документа не привязана к конкретному облачному провайдеру и Terraform:

- **Раздел 6** оригинала (инструменты на локальном ПК: Git, JDK 21, Docker, kubectl, Helm, SSH) — нужны в любом случае; Terraform как инструмент можно не устанавливать.
- **Разделы 7–8** (Spring Boot приложение, локальная разработка) — полностью независимы от инфраструктуры.
- **Раздел 10a** (установка GitLab CE + Docker Registry на devtools-сервере) — работает после SSH-подключения к серверу, созданному в Рег.облаке, буквально теми же командами `curl | sudo bash`, `apt-get install gitlab-ce` и т.д.
- **Раздел 11a** (регистрация GitLab Runner self-hosted) — идентично.
- **Раздел 12** (namespace, Kubernetes Secrets, сборка и push Docker-образа, первый `helm install`, Ingress, DNS) — единственная точка, где раньше был `terraform output`, теперь заменяется значением из панели Рег.облака (host/port PostgreSQL, IP devtools-сервера, IP или hostname LoadBalancer Ingress).
- **Разделы 13–22** (kubectl-справочник, Helm chart и его жизненный цикл, GitLab CI/CD pipeline, окружения dev/stage/prod, PostgreSQL-подключение из Spring Boot через `spring.datasource`, Flyway-миграции, безопасность, эксплуатация rollback/scale/redeploy, диагностика типовых проблем) — используют только Kubernetes API и не завязаны на провайдера или Terraform.

Единственное **важное техническое отличие** от Timeweb Cloud, которое стоит учитывать при переносе команд из Раздела 12 оригинала: у Рег.облака Ingress Controller получает `EXTERNAL-IP` напрямую через `Service` типа `LoadBalancer` (см. Раздел 3 этого документа), поэтому команда

```bash
kubectl get svc -n ingress-nginx
```

в Рег.облаке **должна** показывать `EXTERNAL-IP` — в отличие от Timeweb Cloud, где это поле оставалось пустым и приходилось брать IP worker-узла через `kubectl get nodes -o wide`. При адаптации DNS-настройки (п. 12.10–12.12 оригинала) в Рег.облаке используйте именно `EXTERNAL-IP` из `kubectl get svc -n ingress-nginx`.

---

<a id="раздел-10"></a>
## Раздел 10. Итоговый порядок первого запуска (Рег.облако, без Terraform)

1. Зарегистрироваться / авторизоваться в панели Рег.облака.
2. Создать API-ключ для облачных серверов (раздел **Настройки** окружения облачных серверов) — пригодится для скриптов автоматизации, аналогичных Terraform-вызовам.
3. Сгенерировать SSH-ключ на локальном ПК (`ssh-keygen -t ed25519 ...`), как в Разделе 6 оригинала.
4. Заказать Облачный сервер (devtools) с Ubuntu 22.04, добавить SSH-ключ, при возможности — cloud-init со скриптом `devtools-init.sh` (Раздел 2 этого документа). Приватная сеть создастся автоматически.
5. Заказать кластер Managed Kubernetes с группой из двух worker-нод (Раздел 3).
6. Заказать облачную базу данных PostgreSQL, создать базу `greeting_db` и пользователя `greeting_user` с нужными правами (Раздел 4).
7. Заказать бакет S3 при необходимости хранения артефактов (Раздел 5).
8. Скачать kubeconfig со страницы кластера и сохранить как `~/.kube/regcloud-greeting.yaml` (Раздел 6).
9. Установить Docker Registry на devtools-сервере: `bash scripts/setup-registry.sh` — идентично Разделу 10a оригинального документа.
10. Установить GitLab CE на devtools-сервере — идентично Разделу 10a.
11. Зарегистрировать GitLab Runner (self-hosted, executor shell) — идентично Разделу 11a.
12. Создать namespace и Kubernetes Secrets: `bash scripts/create-secrets.sh`, подставив host/port PostgreSQL из панели Рег.облака вместо `terraform output` (Раздел 12 оригинала).
13. Создать репозиторий в GitLab, выполнить первый `git push`, настроить CI/CD Variables — идентично Разделу 15а.
14. Создать `Service` типа `LoadBalancer` для NGINX Ingress Controller (если ещё не создан по умолчанию) и получить `EXTERNAL-IP` через `kubectl get svc -n ingress-nginx`.
15. Выполнить ручной `helm upgrade --install` для первой проверки (Разделы 12, 14 оригинала).
16. Настроить DNS A-запись на полученный `EXTERNAL-IP` (Раздел 12.12 оригинала, вариант «DNS вручную у провайдера»).
17. Финальная проверка: `curl http://greeting-dev.<ваш-домен>/api/greeting` → ожидается HTTP 200.

Как и в случае с Timeweb Cloud, повторяющийся цикл разработки (push в `develop`/`main` → GitLab CI/CD собирает образ → Helm обновляет Deployment, описанный в Разделах 15а и 19 оригинала) не изменяется вообще — он никогда не зависел от Terraform, даже в исходной версии документа.
