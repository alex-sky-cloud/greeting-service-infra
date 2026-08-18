## Оглавление

1. [Как вообще работает Terraform](#kak-voobshche-rabotaet-terraform)
2. [Что такое «провайдер» в Terraform](#chto-takoe-provaider-v-terraform)
3. [Кто пишет провайдер](#kto-pishet-provaider)
4. [Значит ли отсутствие официального провайдера, что ничего нельзя сделать](#znachit-li-otsutstvie)
5. [Алгоритм действий, если у провайдера нет официальной поддержки Terraform](#algoritm-deistvii)
6. [Риски community-провайдера](#riski-community-provaidera)
7. [Практический вывод](#prakticheskii-vyvod)

---

<a id="kak-voobshche-rabotaet-terraform"></a>
## Как вообще работает Terraform

Terraform — это программа на вашем компьютере (или сервере), которая умеет «разговаривать» с облаком через интернет и говорить ему: «создай мне такой-то сервер», «удали такой-то диск». Сам Terraform ничего не умеет делать напрямую с конкретным облаком — ему для каждого облака нужен переводчик.

Схема взаимодействия:

```

Ваш файл конфигурации (main.tf)
         ↓
    Terraform (ядро программы)
         ↓
  Provider-плагин (переводчик)
         ↓
   API облачного провайдера (интернет)
         ↓
   Реальный сервер создаётся в облаке
```

Официальное определение самого Terraform как инструмента:

- Источник: https://developer.hashicorp.com/terraform/intro

> "Terraform is an infrastructure as code tool that lets you build, change, and version cloud and on-prem resources safely and efficiently."

RU:

> «Terraform — это инструмент инфраструктуры как кода, который позволяет безопасно и эффективно создавать, изменять и версионировать облачные и локальные ресурсы».

---

<a id="chto-takoe-provaider-v-terraform"></a>
## Что такое «провайдер» в Terraform

«Провайдер» (provider) — это отдельный файл-плагин, программа, которая знает, как превратить команды Terraform в конкретные HTTP-запросы к API нужного облака. Он скачивается на ваш компьютер автоматически при команде `terraform init`. Вы не пишете этот переводчик сами — его пишет либо сам облачный провайдер, либо кто-то из сообщества.

- Источник: https://developer.hashicorp.com/terraform/language/providers

> "Terraform relies on plugins called providers to interact with cloud providers, SaaS providers, and other APIs... Each provider adds a set of resource types and/or data sources that Terraform can manage."

RU:

> «Terraform использует плагины, называемые провайдерами, для взаимодействия с облачными провайдерами, SaaS-провайдерами и другими API... Каждый провайдер добавляет набор типов ресурсов и/или источников данных, которыми может управлять Terraform».

---

<a id="kto-pishet-provaider"></a>
## Кто пишет провайдер

У каждого облака есть свой API — это «меню команд», через которое можно управлять серверами по интернету (создать, удалить, включить). Практически у всех современных хостеров такой API есть, даже если у них нет Terraform.

Provider для Terraform — это программа, которая вызывает этот API от имени Terraform. Написать такую программу может:

- **Сам облачный провайдер** — тогда это называется «официальный провайдер» (например, у Timeweb Cloud, Selectel).
- **Любой человек со стороны** — если у облака есть открытый API, энтузиаст может сам написать такой переводчик и опубликовать его в реестре Terraform.

Так появился провайдер для RuVDS — его написал не сам RuVDS, а сторонний разработчик, воспользовавшись открытым API RuVDS.

- Источник: https://registry.terraform.io/providers/rustamkulenov/ruvds/latest/docs/resources/vps

Обратите внимание на namespace в ссылке — `rustamkulenov/ruvds`, а не `ruvds/ruvds`. В реестре Terraform название автора перед слэшем указывает владельца провайдера — здесь это частное лицо, а не компания.

Официальный реестр Terraform прямо разделяет провайдеров по статусу верификации:

- Источник: https://developer.hashicorp.com/terraform/registry/providers/publishing

> "Providers on the Terraform Registry are either maintained by HashiCorp, verified as maintained by third-party providers, or are community providers with no official verification."

RU:

> «Провайдеры в реестре Terraform либо поддерживаются HashiCorp, либо верифицированы как поддерживаемые сторонними компаниями, либо являются community-провайдерами без официальной верификации».

---

<a id="znachit-li-otsutstvie"></a>
## Значит ли отсутствие официального провайдера, что ничего нельзя сделать

Нет. Provider не является частью самого облачного провайдера — это отдельная программа, которая обращается к API облака снаружи, как обычный клиент. Облаку не важно, откуда пришёл запрос — из официального Terraform-плагина или из стороннего. Для облака это просто HTTP-запрос вида «создай сервер с такими параметрами».

Поэтому:

- **Есть открытый API** (документация, ключи доступа) — можно использовать сторонний (community) провайдер, если такой существует, или написать свой.
- **Нет никакого API** — Terraform в принципе не сможет работать с этим облаком, ни через официальный, ни через сторонний провайдер, потому что провайдеру просто не с чем «разговаривать».

Это подтверждается и универсальным провайдером `restapi`, специально созданным для облаков без выделенного Terraform-провайдера:

- Источник: https://registry.terraform.io/providers/Mastercard/restapi/latest/docs

> "This is a terraform provider that allows you to interact with APIs that may not have a native Terraform provider by defining the API's URL structure and behavior in a terraform config."

RU:

> «Это провайдер Terraform, который позволяет взаимодействовать с API, у которых может не быть собственного Terraform-провайдера, определяя структуру URL и поведение API в конфигурации Terraform».

---

<a id="algoritm-deistvii"></a>
## Алгоритм действий, если у провайдера нет официальной поддержки Terraform

1. **Проверьте, есть ли у облака API.** Почти у всех современных хостеров он есть — ищите в личном кабинете раздел «API» или «Токены доступа».
2. **Проверьте реестр Terraform** (registry.terraform.io) — возможно, кто-то уже написал community-провайдер для этого облака, как в случае с RuVDS.
3. **Если community-провайдер существует** — используйте его так же, как официальный: прописываете в `main.tf`, указываете API-ключ, и Terraform работает через этот сторонний переводчик.
4. **Если провайдера вообще нет** (ни официального, ни стороннего), варианты такие:
   - управлять сервером через сам API напрямую, без Terraform — через `curl` или Python-скрипты;
   - использовать универсальный провайдер `restapi`, настроив его вручную под конкретный API — Источник: https://registry.terraform.io/providers/Mastercard/restapi/latest/docs;
   - написать свой собственный провайдер (сложный вариант, требует программирования на Go) — процесс описан HashiCorp:

- Источник: https://developer.hashicorp.com/terraform/plugin/framework/getting-started

> "The framework lets you focus on business logic rather than implementation logic, making provider development easier and providers more consistent."

RU:

> «Фреймворк позволяет фокусироваться на бизнес-логике, а не на логике реализации, что упрощает разработку провайдера и делает провайдеров более согласованными».

---

<a id="riski-community-provaidera"></a>
## Риски community-провайдера

- Его может забросить автор — обновления перестанут выходить, если облако поменяет API.
- Он не проходит официальную проверку самим облачным провайдером — если что-то работает не так, поддержка облака может отказаться помогать, ссылаясь на то, что это «не наш инструмент».
- Обычно у него меньше функций, чем в самом API — автор реализует только то, что ему самому было нужно.

Реестр Terraform прямо предупреждает об этом статусе провайдеров без верификации:

- Источник: https://developer.hashicorp.com/terraform/registry/providers/publishing

> "Community providers are unlisted providers... published by individual maintainers, without official verification from HashiCorp or the associated organization."

RU:

> «Community-провайдеры — это неверифицированные провайдеры... опубликованные отдельными разработчиками, без официальной верификации со стороны HashiCorp или связанной с провайдером организации».

---

<a id="prakticheskii-vyvod"></a>
## Практический вывод

Если хостер (например, RuVDS) официально не поддерживает Terraform, но у него есть открытый API — можно использовать сторонний community-провайдер из реестра, и технически всё будет работать, потому что провайдер просто обращается к тому же самому API, которым можно было бы пользоваться и без Terraform. Единственная разница — вы получаете удобный декларативный язык конфигурации вместо ручных запросов к API, но без гарантии поддержки от самого облака.
