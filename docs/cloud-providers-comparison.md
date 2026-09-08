# VPS ≥4 CPU / ≥4 ГБ — час или минута, без телефона

Дата проверки: 2 сентября 2026.

## Критерии

| | |
|---|---|
| CPU | ≥ **4** |
| RAM | ≥ **4 ГБ** (ориентир) |
| Диск | не важен |
| Биллинг | **почасовой** или **поминутный** |
| Телефон | **не требуется** при регистрации / активации |

**₽ через BYN** (НБ РБ 02.09.2026): 1 USD ≈ **86,2 ₽**, 1 EUR ≈ **100 ₽**.

Источник: https://www.belarus.kp.ru/online/news/7151968/

**Цитата:**
> …1 доллар США равен 3,0629 белорусского рубля, 1 евро — 3,5512 белорусского рубля, 100 российских рублей — 3,5525 белорусского рубля.

---

## Serverspace и «условный» почасовой биллинг

**Что не так у Serverspace:** списания каждые **10 минут** с предоплаченного баланса; услуги создаются только если баланс «достаточный» под все ресурсы. При нескольких серверах сразу часто просят доложить деньги, хотя «на один» вроде хватало.

Источник (шаг биллинга): https://serverspace.io/support/help/billing-rules/

**Цитата:**
> Once the server is ordered, its cost is being gradually debited from your account on control panel. Such debiting occurs every 10 minutes.

**Перевод:** после заказа стоимость постепенно списывается с баланса в панели; списание — каждые 10 минут.

Источник (условие «достаточный баланс»): https://serverspace.io/conditions/terms-of-service/

**Цитата:**
> The Services are only provided if the Balance in the Project's personal account is positive and sufficient to pay for the Services.

**Перевод:** услуги предоставляют только если баланс проекта положительный и **достаточный** для оплаты услуг.

То же по сути у **Aeza** на почасовых: лимиты на число серверов/час. Чтобы взять **3+** почасовых сразу, нужно хотя бы раз пополнить на **€10** (лимиты в кабинете → Finance).

Источник: https://wiki.aeza.net/en/cabinet/limits/

**Цитата:**
> …to rent several virtual servers at once, it will be enough for you to top up your balance by 10 euros over the entire time. … Getting for this, the desired ability to rent 3 or more virtual servers per hour.

**Перевод:** чтобы арендовать несколько VPS сразу, достаточно суммарно пополнить баланс на 10 € — тогда можно брать 3 и более серверов в час.

---

## Настоящий PAYG: час/минута + несколько серверов сразу

Ищите модель: **списали только за фактическое время**, создание N машин не требует «месяц × N на балансе».

| # | Провайдер | Биллинг | Несколько серверов | Мин. деньги | Без тел. | RU | Ссылка |
|---|---|---|---|---|---|---|---|
| 1 | **Infrawire Cloud** | **поминутно** до секунды | явно несколько под пик (см. их сравнение с месячным VPS) | $5 на signup | email, без документов | да `/ru/` | https://infrawire.net/ru/cloud |
| 2 | **Cloudzy** | час | FAQ: create as many VPS as you need | карта/крипта при деплое | явно без phone | да `/ru/` | https://cloudzy.com/ru/ · https://cloudzy.com/sandbox-vps/ |
| 3 | **X-ZoneServers** | час, потолок = месяц | несколько планов с баланса (не 10‑мин шаг) | пополнение WHMCS | email | да | https://x-zoneservers.com/hourly-vps |
| 4 | **LightNode** | час | квота **30** хостов по умолчанию (больше — тикет) | пополнение | email / Google | да | https://www.lightnode.com/ru-RU · quota: https://doc.lightnode.com/Instance/instancequota.html |
| 5 | **CloudCone SC2** | час | слайдеры, несколько SC2 | пополнение | email | нет | https://cloudcone.com/cloud-servers/ |
| 6 | **BitLaunch** | час | много инстансов с prepaid-криптобаланса | депозит криптой | email | частично | https://bitlaunch.io/ |
| 7 | **bithost** | час | DO/Vultr/Linode с баланса | мин. ~$20 | email, без phone | нет | https://bithost.io/ |
| 8 | **deploynode** | час | DO/Vultr с баланса | мин. $20 | email, без KYC | нет | https://deploynode.io/hourly-billing-vps |
| 9 | **surfvps** | час | DO/Vultr/Hetzner с баланса | крипта | email | нет | https://surfvps.com/ |
| 10 | **Virtua.Cloud** | час с баланса | delete → стоп биллинга | пополнение | проверить signup | нет | https://www.virtua.cloud/vps/hourly-billing |
| 11 | **GhostVPS** | час | несколько Discovery/GP | PayPal/крипта | No KYC | нет | https://ghostvps.com/hourly-vps/ |
| 12 | **SERVERZ** | час | custom VPS на Vultr | крипта | без email/тел. | нет | https://serverz.com/vps |
| 13 | **Colonel Server** | час | PAYG-планы | пополнение | проверить | нет | https://colonelserver.com/pay-as-you-go-vps/ |
| 14 | **SporeStack** | prepaid/API | много через API (DO/Vultr) | депозит на токен | без email | нет | https://sporestack.com/ |
| 15 | **Vultr / DigitalOcean** (напрямую) | час / секунда | десятки Droplet/инстансов, **postpaid** по карте | карта (не «месяц вперёд» на балансе) | часто **SMS** | нет | https://www.vultr.com/ · https://www.digitalocean.com/ |

**Практичный выбор под ваши прошлые критерии (без тел. + несколько машин + настоящий PAYG):**

1. **Infrawire** — минута, $5, без документов, несколько инстансов.  
2. **Cloudzy** — час, явно «сколько угодно» VPS, без телефона.  
3. **X-Zone Basic** — дёшево 4/16, час с потолком.  
4. **bithost / deploynode / BitLaunch** — час на железе Vultr/DO без их SMS (через крипту).  
5. **LightNode** — до 30 серверов без просьбы в support.

**Не брать для «много серверов сразу»:** Serverspace (шаг 10 мин + «достаточный» баланс), **Aeza** почасовые на новом аккаунте (лимиты до пополнения €10 / тикета в support).

---

## 15 провайдеров (общий список ≥4 CPU / ≥4 ГБ)

Сортировка примерно по цене тарифа ≥4 CPU / ≥4 ГБ при работе весь месяц. Колонка **RU** — сайт/панель на русском.

| # | Провайдер | Сайт | RU | Без тел. | Биллинг | Конфиг ≥4/≥4 | Цена ≈ мес | ≈ ₽ |
|---|---|---|---|---|---|---|---|---|
| 1 | **X-ZoneServers** | https://x-zoneservers.com/hourly-vps | да | email (проверить форму) | час + потолок | **4 / 16 ГБ** Basic | **€16** · €0.0222/ч | **~1 600** |
| 2 | **NODED.CLOUD** | https://noded.cloud/vps-hosting | нет (EN) | email; phone в публичных docs не как SMS-signup | час / сек | **4 / 16 ГБ** VPS V | ориентир **€0.0417/ч** / потолок по тиру | **~3 000** |
| 3 | **Colonel Server** | https://colonelserver.com/pay-as-you-go-vps/ | нет | проверить форму | час | **4 / 8 ГБ** GO-VPS-5 | **€0.0263/ч** ≈ €19 | **~1 920** |
| 4 | **Aeza** | https://aeza.net/ | **да** | гайды: только email | час (**есть лимиты** на число серверов/час) | **4 / 8 ГБ** | **€19.77** | **~1 977** |
| 5 | **Infrawire** | https://infrawire.net/ru/cloud | **да** | email + $5, без документов | **минута** | **4 / 8 ГБ** iCS-8 | **€19.99** · €0.027/ч | **~1 999** |
| 6 | **Cloudzy** | https://cloudzy.com/ru/ · pricing | **да** | явно без phone/KYC | час | готовый **4 / 8 ГБ** $26.48 *или* custom **4 / 4 ГБ** $29.90 | $26.48–29.90 | **~2 280–2 580** |
| 7 | **LightNode** | https://www.lightnode.com/ru-RU | **да** | docs: email / Google / GitHub | час | **4 / 8 ГБ** | **$27.7** | **~2 390** |
| 8 | **CloudCone SC2** | https://cloudcone.com/cloud-servers/ | нет | email (без phone-loop в обзорах) | час + **слайдеры** | **5 / 8 ГБ** Professional | **$31.99** · $0.043/ч | **~2 760** |
| 9 | **GhostVPS** | https://ghostvps.com/hourly-vps/ | нет | No KYC | час | **4 / 8 ГБ** Discovery | **$0.0527/ч** ≈ $38.5 | **~3 320** |
| 10 | **BitLaunch** | https://bitlaunch.io/ | частично | email + крипта | час | **4 / 4 ГБ** (свой) | ~**$0.055/ч ≈ $40** | **~3 450** |
| 11 | **bithost** | https://bithost.io/ | нет | email, без phone/ID | час | Vultr/DO **4 / 8 ГБ** | Vultr ~**$40** | **~3 450** |
| 12 | **deploynode** | https://deploynode.io/hourly-billing-vps | нет | email, без KYC/карты | час | Vultr/DO **4 / 8 ГБ** | ~**$40** (депозит от $20) | **~3 450** |
| 13 | **surfvps** | https://surfvps.com/ | нет | email + крипта (XMR и др.) | час | DO/Vultr/Hetzner **4 / 8 ГБ** | от ~**$40** (как upstream) | **~3 450+** |
| 14 | **SERVERZ** | https://serverz.com/vps | нет | **без email/телефона** (access key) | час | custom / Vultr **≥4 / ≥4** | от **~$0.021/ч** (тиры) | от **~1 560** (зависит от конфига) |
| 15 | **SporeStack** | https://sporestack.com/ | нет | **без email** (токен) | prepaid / API (DO/Vultr) | размеры upstream **≥4 / ≥8** | по тарифу DO/Vultr | **~3 450+** |

### С русским интерфейсом (из таблицы выше)

| | |
|---|---|
| Полный / хороший RU | **X-Zone**, **Aeza**, **Infrawire**, **Cloudzy**, **LightNode** |
| Остальные | EN-панель; русский только у части маркетинга |

### Конфигуратор (не только готовый план)

| | |
|---|---|
| **Cloudzy** | слайдеры в https://dash.cloudzy.com/configure |
| **CloudCone SC2** | слайдеры на https://cloudcone.com/cloud-servers/ |
| **SERVERZ** | any config на https://serverz.com/vps |

---

## X-Zone: какой план брать

Конфигуратора нет. Ссылка `product=vps-ii-kvm` = **2 CPU / 4 ГБ** — не подходит.

Брать: **Basic — 4 CPU / 16 ГБ**, €16/мес (~1 600 ₽) — https://x-zoneservers.com/hourly-vps

---

## Вычеркнуты

| | Почему |
|---|---|
| Serveroid | телефон при активации + дорого |
| Timeweb / Reg.ru / Selectel | телефон / ID (РФ) |
| Contabo | нет hourly |
| DigitalOcean / Kamatera напрямую | обычно SMS |
| NordBastion, BitVPS, Njalla «от $2» | чаще только месяц / слабый конфиг |
| Hostkey | RU + почасово есть, но РФ-провайдер — риск телефона/ID; отдельно проверять signup |
| VDSina | чаще **посуточно**, не час/минута |
| **Serverspace** | шаг **10 минут** + создание только при «достаточном» балансе — условный PAYG |
| **Aeza** (много серверов сразу) | лимиты на почасовые; 3+ VPS/час после пополнения ≥€10 или тикет |

Перед оплатой откройте форму signup сами.
