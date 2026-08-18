# Облачные провайдеры без обязательного телефона

Дата строгой перепроверки: 17 августа 2026.

## Результат

Для обычного публичного signup подтверждены **10** вариантов с поминутной или посекундной тарификацией.

Шесть из них имеют официальную инструкцию с полями/шагами регистрации без телефона. У четырёх публичная signup-форма или логин-поток содержит только email/OAuth; это достаточно для обычной регистрации, но не является гарантией против индивидуального anti-fraud запроса после оплаты.

## Критерий включения

Провайдер включён, только если официальный источник:

1. показывает реальные обязательные поля signup либо прямо описывает регистрацию без номера;
2. подтверждает поминутную или посекундную тарификацию compute;
3. не является почасовым VPS, 10-минутным биллингом или закрытым waitlist.

Дополнительная антифрод-проверка при подозрительной оплате возможна у любого сервиса. Здесь оценивается обычный публичный сценарий регистрации.

## Подтверждённые варианты

| Провайдер | Ресурс | Уровень подтверждения регистрации без телефона | Тарификация | Остановка | RU | Terraform / API |
|---|---|---|---|---|---|---|
| [Krova Cloud](https://krova.cloud/pricing/cube) | Linux microVM | Инструкция signup: email magic-link или Google | Поминутная | `power off` останавливает compute; диск остаётся платным | Нет | REST API, CLI, TypeScript SDK |
| [IONOS Cloud](https://www.ionos.com/cloud/cloud-servers) | Cloud Cube / VM | Форма: First name, Last name, Email, Password; телефона нет | Поминутная | Полностью прекращается после удаления инстанса | Нет | API, Terraform |
| [IBM Cloud VPC](https://cloud.ibm.com/docs/vpc?topic=vpc-suspend-billing) | VM | Форма IBMid: Email, First name, Last name, Country; телефона нет | Посекундная | Suspend Billing прекращает vCPU/RAM/GPU; storage и сеть могут остаться платными | Нет | Terraform |
| [shellbox](https://shellbox.dev/) | Linux box | Регистрации нет: аккаунт создаётся из отпечатка SSH-ключа | Поминутная, округление вверх | Stop останавливает running-time billing; stopped storage оплачивается отдельно | Нет | SSH CLI |
| [Gcore Edge Cloud](https://docs.gcore.com/cloud/billing) | VM | Инструкция: email/password либо Google/GitHub; телефон не указан | Поминутная | VM compute до полного stop; volume/IP/snapshot — до удаления | Нет | API, официальный Terraform |
| [Crusoe Cloud](https://docs.crusoecloud.com/compute/virtual-machines/overview.md) | CPU/GPU VM | Инструкция: Google, GitHub или email verification; телефон не указан | Посекундная | Stopped VM не оплачивает compute; OS disk — до удаления | Нет | API, Terraform |
| [Fly.io Machines](https://fly.io/docs/about/billing/) | Firecracker VM | Публичная форма: GitHub, Google или email; телефона нет | Посекундная | Stopped/suspended: rootfs платный, compute = 0 | Нет | API, `flyctl` |
| [Render](https://render.com/pricing) | Managed web service / worker | Публичная форма: GitHub, GitLab, Google, email/password; телефона нет | Посекундная | Неактивный compute = 0; storage отдельно | Нет | REST API, Terraform |
| [Railway](https://docs.railway.com/platform/compare-to-vps) | Managed container compute | Публичный login: GitHub или email; телефона нет | Посекундная | Serverless sleep: compute = 0; volume отдельно | Нет | CLI, GraphQL API |
| [Runpod Pods](https://docs.runpod.io/accounts-billing/billing) | CPU/GPU Pod | Публичная форма: Google, GitHub, email/password; телефона нет | Посекундная | GPU compute остановлен; persistent storage остаётся платным | Нет | REST API, CLI, SDK |

### Krova Cloud

**Регистрация**

> Sign up with a magic link (email) or with Google.

Источник: <https://krova.cloud/docs/getting-started>

Перевод: регистрация идёт по magic-link на email или через Google.

**Тарификация и остановка**

> Rates are quoted per hour and charged per minute.

Источник: <https://krova.cloud/pricing/cube>

Перевод: ставки показываются за час, но списание идёт по минутам.

> `krova cubes power-off my-api # stop compute billing (data is preserved)`

Источник: <https://krova.cloud/docs/cli>

Перевод: выключение прекращает тарификацию вычислений, данные сохраняются.

### IONOS Cloud

**Регистрация**

> First name* … Last name* … Email* … Password*

Источник: <https://cloud.ionos.com/compute/sign-up>

Перевод: обязательны имя, фамилия, email и пароль; обязательного телефона в форме нет.

**Тарификация**

> We charge by the minute for the time your cloud server is active.

Источник: <https://www.ionos.com/cloud/cloud-servers>

Перевод: время активности облачного сервера тарифицируется по минутам.

### IBM Cloud VPC

**Регистрация**

> Email / First name / Last name / Country

Источник: <https://www.ibm.com/account/reg/us-en/signup?formid=urx-19776>

Перевод: в форме обязательны email, имя, фамилия и страна; телефона нет.

**Тарификация**

> Usage times are calculated per second … No minimum usage requirement exists for an instance.

Источник: <https://cloud.ibm.com/docs/vpc?topic=vpc-suspend-billing>

Перевод: использование считается посекундно, минимального периода использования нет.

### shellbox

**Регистрация**

> SSH key fingerprint = account. No signup.

Источник: <https://shellbox.dev/>

Перевод: отпечаток SSH-ключа является аккаунтом; отдельной регистрации нет.

**Тарификация**

> Usage time is tracked in minutes and rounded up to the nearest minute.

Источник: <https://shellbox.dev/>

Перевод: время использования учитывается в минутах и округляется вверх до полной минуты.

### Gcore Edge Cloud

**Регистрация**

> On the sign-up page, enter an email address and password … Alternatively, click Google or GitHub to create the account …

Источник: <https://docs.gcore.com/account-settings/account/create-account>

Перевод: в signup вводятся email и пароль; вместо этого можно создать аккаунт через Google или GitHub.

**Тарификация**

> Edge Cloud resources are charged per minute for the time they are in use.

Источник: <https://docs.gcore.com/cloud/billing>

Перевод: ресурсы Edge Cloud тарифицируются поминутно за время использования.

### Crusoe Cloud

**Регистрация**

> Use Google or Github for the quickest signup. If you sign up with your email address, you'll receive a verification email …

Источник: <https://docs.crusoecloud.com/create-an-account.md>

Перевод: для быстрой регистрации используются Google или GitHub; при регистрации по email приходит письмо для подтверждения.

**Тарификация**

> VMs are charged based on the time the machine is in the `running` state (per second).

Источник: <https://docs.crusoecloud.com/compute/virtual-machines/overview.md>

Перевод: VM тарифицируется посекундно, пока находится в состоянии `running`.

### Публичные signup-потоки: Fly.io, Render, Railway и Runpod

У этих четырёх сервисов публичная форма показывает только email/OAuth, однако сведения об anti-fraud после добавления платёжного метода могут меняться. Поэтому перед пополнением баланса перепроверьте форму в своей стране.

> Sign up with GitHub / Sign up with Google / Sign up with email.

Источник: <https://fly.io/app/sign-up>

Перевод: форма Fly.io предлагает регистрацию через GitHub, Google или email.

> Compute costs … prorated to the second.

Источник: <https://render.com/pricing>

Перевод: стоимость compute у Render пропорционально рассчитывается по секундам.

> $20/vCPU-month, $10/GB-month RAM, billed per second of actual usage.

Источник: <https://docs.railway.com/platform/compare-to-vps>

Перевод: Railway считает vCPU и RAM посекундно по фактическому использованию.

> All compute and storage charges are billed per second.

Источник: <https://docs.runpod.io/accounts-billing/billing>

Перевод: Runpod тарифицирует compute и storage посекундно.

## Не включены без дополнительной проверки

Эти сервисы могут подходить технически, но официальный источник не доказывает отсутствие телефона на **всём** signup/onboarding пути. Они не считаются ответом на требование «без обязательного телефона».

| Провайдер | Что подтверждено | Почему не в основном списке |
|---|---|---|
| Infrawire | Поминутно, до секунды | Privacy policy перечисляет phone number среди собираемых данных |
| NODED.CLOUD | Посекундный KVM VPS | Privacy policy с email не является перечнем обязательных полей формы |
| Exoscale | VM посекундно | Документация описывает этапы регистрации, но не форму и обязательность телефона |
| machine0 | VM поминутно | Экран предлагает email/Google, но не доказывает отсутствие телефона на следующем этапе |
| Vast.ai | GPU-инстансы посекундно | Подтверждён email verification, но не опубликован исчерпывающий signup flow |
| DigitalOcean | Droplet посекундно | Есть противоречивые публичные сведения о phone verification; не включён без однозначного доказательства |
| EdgeCenter | Поминутно, есть Terraform | Пользователь подтвердил обязательный телефон в актуальной форме signup |
| THE.Hosting | FAQ заявляет поминутную модель | Реальная форма signup без телефона не подтверждена; страница называет модель «почасовой» |

## Исключены

| Провайдер | Причина |
|---|---|
| EdgeCenter | Обязательный телефон в актуальной форме signup: <https://auth.edgecenter.ru/login/signup?lang=ru> |
| DigitalOcean | Неоднозначные сведения о phone verification; не проходит строгую проверку |
| AWS | SMS/voice verification при создании аккаунта |
| Google Cloud | Для части регионов, в том числе Индии, mobile phone — обязательная identity verification |
| Oracle Cloud | Phone/SMS verification |
| Yandex Cloud, VK Cloud, Cloud.ru, Selectel, Timeweb, 1cloud | Обязательные телефон/SMS в onboarding |
| Cloudzy, Vultr, Hetzner, HOSTKEY, LightNode, Aeza, UpCloud, Linode | Почасовой биллинг |
| Serverspace, 1cloud | Шаг биллинга 10 минут |
| mashines.dev | Закрытый early access / waitlist |

## Вывод

* **10 кандидатов** подтверждены по опубликованной signup-форме или официальному signup-сценарию и granular billing. Для Gcore, Crusoe, IONOS, IBM, Krova и shellbox доказательство сильнее: документация прямо описывает поля/механизм регистрации.
* **Поставщика с подтверждённым RU-интерфейсом, без телефона и с поминутной/посекундной VM-тарификацией** в этом исследовании не найдено.
* Если телефон можно разрешить, российский вариант с подходящим биллингом — EdgeCenter. Если RU-интерфейс не обязателен, наиболее практичные кандидаты — Krova Cloud, Gcore Edge Cloud, IONOS Cloud, IBM Cloud VPC и Fly.io.
