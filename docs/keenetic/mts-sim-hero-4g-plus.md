# SIM МТС в Keenetic Hero 4G Plus: пошаговая настройка

> Роутер: Keenetic Hero 4G+ (KN-2311), встроенный LTE-модем, слот **nano-SIM**.  
> Оператор: **МТС**. Безлимит работает в телефоне, в роутере — нет или «не то».

Инструкция идёт **строго по порядку**. Не перескакивайте шаги. После каждого шага смотрите, что изменилось: появилась сеть, появился IP, открываются сайты, нормальная скорость.

Keenetic **не снимает** запрет тарифа «только для смартфона». Он умеет вставить SIM во встроенный модем, задать APN и TTL. Если МТС режет доступ именно потому, что устройство — роутер, легальный выход — тариф для модема/роутера (у МТС это обычно линейка «Для ноутбука»).

---

## Оглавление

- [Что понадобится](#что-понадобится)
- [Как понять, на каком шаге вы застряли](#как-понять-на-каком-шаге-вы-застряли)
- [Шаг 1. Проверьте SIM в телефоне](#шаг-1-проверьте-sim-в-телефоне)
- [Шаг 2. Отключите PIN](#шаг-2-отключите-pin)
- [Шаг 3. Запишите APN с телефона](#шаг-3-запишите-apn-с-телефона)
- [Шаг 4. Вставьте nano-SIM в Hero](#шаг-4-вставьте-nano-sim-в-hero)
- [Шаг 5. Откройте веб-интерфейс](#шаг-5-откройте-веб-интерфейс)
- [Шаг 6. Настройте мобильное подключение и APN МТС](#шаг-6-настройте-мобильное-подключение-и-apn-мтс)
- [Шаг 7. Подождите и посмотрите дашборд](#шаг-7-подождите-и-посмотрите-дашборд)
- [Шаг 8. Зафиксируйте TTL](#шаг-8-зафиксируйте-ttl)
- [Шаг 9. Проверьте интернет с устройства в Wi-Fi](#шаг-9-проверьте-интернет-с-устройства-в-wi-fi)
- [Шаг 10. Если всё ещё не работает](#шаг-10-если-всё-ещё-не-работает)
- [Источники](#источники)

---

## Что понадобится

- Keenetic Hero 4G+ (KN-2311) с питанием и доступом в его Wi-Fi (или кабель в LAN).
- SIM МТС формата **nano-SIM** (если карта micro/mini — нужна обрезка или адаптер наоборот: из nano в слот телефона на шагах 1–3).
- Телефон МТС, в котором эта SIM точно даёт интернет.
- Браузер на компьютере или телефоне, подключённом к Keenetic.

Встроенный модем Hero 4G+ — это не отдельная USB-флешка: карта ставится в слот роутера.

**Источник:** https://support.keenetic.com/hero-4g-plus/kn-2311/en/13777-internet-connection-via-a-3g-4g,-lte-modem.html

**Цитата:**
> Keenetic Hero 4G+ (KN-2311) has a built-in LTE/4G/3G modem for mobile internet connection. You don't need to buy an additional USB modem; just insert a SIM card of any operator into the special slot on the router.

**Перевод:**
> У Keenetic Hero 4G+ (KN-2311) есть встроенный LTE/4G/3G-модем. Отдельный USB-модем не нужен: достаточно вставить SIM любого оператора в слот роутера.

Интерфейс этого модема в Keenetic называется **`UsbLte0`** (режим RNDIS/NDIS). Не путайте с обычным Hero 4G (KN-2310), там `UsbQmi0`.

**Источник:** https://support.keenetic.com/hero-4g-plus/kn-2311/en/34991-5g-4g-3g-modem-connection-types--ras,-cdc-ethernet,-ndis,-qmi-.html

**Цитата:**
> The Keenetic Hero 4G+ (KN-2311) and Skipper 4G (KN-2910) routers have a built-in LTE/4G/3G modem that operates in RNDIS mode.
>
> When the modem is connected to the router, the `UsbLte` interface is automatically created in the settings.

**Перевод:**
> У Keenetic Hero 4G+ (KN-2311) и Skipper 4G (KN-2910) встроенный LTE/4G/3G-модем работает в режиме RNDIS.
>
> При подключении модема в настройках автоматически создаётся интерфейс `UsbLte`.

---

## Как понять, на каком шаге вы застряли

| Что видите на дашборде Keenetic | Что это значит | Что делать |
|---|---|---|
| Нет регистрации в сети / нет сигнала / ошибка SIM | PIN, карта не до конца вставлена, нет покрытия, или МТС не пускает **этот тип устройства** | Шаги 1–4, затем 7. Если сигнала нет при хорошем покрытии телефона — шаг 10 |
| Сеть есть, IP нет | Часто APN | Шаг 6 |
| IP есть, сайты не открываются или скорость «в ноль» | Часто TTL или ограничение тарифа на роутер | Шаги 8–10 |

---

## Шаг 1. Проверьте SIM в телефоне

1. Вставьте ту же SIM в телефон.
2. Выключите Wi-Fi на телефоне.
3. Откройте любой сайт и спидтест.

Если в телефоне интернета нет — в роутере тоже не будет. Сначала разберитесь с тарифом, балансом и опцией «Мобильный интернет» в приложении «Мой МТС».

Если в телефоне всё есть — выньте SIM и переходите к шагу 2.

---

## Шаг 2. Отключите PIN

Пока карта ещё в телефоне:

1. Откройте настройки SIM / безопасность.
2. Отключите **запрос PIN**.
3. Выключите телефон, выньте SIM.

Keenetic прямо просит сделать это до установки карты в модем.

**Источник:** https://support.keenetic.com/hero-4g-plus/kn-2311/en/13777-internet-connection-via-a-3g-4g,-lte-modem.html

**Цитата:**
> Disable the PIN code request on your SIM card before installing the modem. This can be done in a cell phone menu by temporarily inserting the card…

**Перевод:**
> Перед установкой в модем отключите запрос PIN на SIM. Это делается в меню телефона: временно вставьте карту туда…

---

## Шаг 3. Запишите APN с телефона

Пока карта в телефоне (или по памяти, если уже вынули — используйте официальные значения МТС):

| Поле | Значение |
|---|---|
| Точка доступа (APN) | `internet.mts.ru` |
| Имя пользователя | `mts` |
| Пароль | `mts` |

**Источник:** https://support.mts.ru/mts_mobilnyy_internet/nastroiki-mobilnogo-interneta/kak-nastroit-internet-na-telefone

**Цитата:**
> Стандартные параметры для настройки интернета:
>
> Точка доступа / APN — `internet.mts.ru`  
> Имя пользователя / User name — `mts`  
> Пароль / Password — `mts`

**Перевод:** те же значения, страница МТС на русском.

На Android: **Настройки → Точки доступа (APN) → MTS Internet**. Если на телефоне APN другой — запишите **тот, что реально работает**, и используйте его на шаге 6.

---

## Шаг 4. Вставьте nano-SIM в Hero

1. Выключите роутер из розетки (безопаснее для слота).
2. Найдите слот **nano-SIM** на корпусе KN-2311.
3. Вставьте карту контактами как на схеме у слота, до щелчка.
4. Поставьте роутер ближе к окну, антенны LTE вертикально.
5. Включите питание.
6. Подождите **не меньше 30 секунд**.

**Источник:** https://support.keenetic.com/hero-4g-plus/kn-2311/en/13777-internet-connection-via-a-3g-4g,-lte-modem.html

**Цитата:**
> Some USB modems require up to `30 seconds` to get ready for use.
>
> Place the 3G/4G modem close to a window and do not block it with objects that could weaken the cellular network signal.

**Перевод:**
> Некоторым модемам нужно до `30 секунд`, чтобы стать готовыми к работе.
>
> Ставьте 3G/4G-модем ближе к окну и не загораживайте его предметами, которые ослабляют сигнал.

---

## Шаг 5. Откройте веб-интерфейс

С телефона или ПК подключитесь к Wi-Fi Keenetic (или LAN-кабелем).

В браузере откройте:

- `http://my.keenetic.net`  
  или
- `http://192.168.1.1`

Если адрес роутера меняли — используйте свой. Войдите паролем администратора.

---

## Шаг 6. Настройте мобильное подключение и APN МТС

1. Меню **Интернет** (или **Mobile**) → **Мобильное широкополосное подключение**.
2. Включите использование этого подключения для выхода в интернет (перетащите его вверх в политиках, если есть Ethernet и он «главный»).
3. Оператор: **МТС**, либо ручной APN.
4. Задайте:

   - APN: `internet.mts.ru`
   - пользователь: `mts`
   - пароль: `mts`

5. Тип сети: сначала **Авто**, если не заработает — **только 4G**.
6. Сохраните.

Дополнительные настройки модема Keenetic как раз на этой странице.

**Источник:** https://support.keenetic.com/hero-4g-plus/kn-2311/en/13777-internet-connection-via-a-3g-4g,-lte-modem.html

**Цитата:**
> Additional configuration of 5G/4G/3G, LTE, CDMA USB modem can be performed in Mobile menu on the Mobile Broadband Internet Connection page.

**Перевод:**
> Дополнительная настройка 5G/4G/3G, LTE, CDMA модема делается в меню Mobile на странице Mobile Broadband Internet Connection.

---

## Шаг 7. Подождите и посмотрите дашборд

Откройте **Системный монитор** / стартовую страницу.

Запишите четыре факта:

1. SIM определяется (есть ICCID / оператор МТС)?
2. Есть регистрация в сети (LTE/4G, уровень сигнала)?
3. Интерфейс `UsbLte0` в состоянии **Connected** / есть IP?
4. Сайты с устройства в Wi-Fi Keenetic открываются?

Статус соединения смотрят на дашборде.

**Источник:** https://support.keenetic.com/hero-4g-plus/kn-2311/en/13777-internet-connection-via-a-3g-4g,-lte-modem.html

**Цитата:**
> You can see the connection status on the System dashboard page.

**Перевод:**
> Состояние подключения видно на странице System dashboard.

**Ветвление:**

- Нет SIM / нет сети → повторите шаги 2 и 4. Если в телефоне на этом же месте сеть есть, а в Hero нет — переходите к шагу 10.
- Сеть есть, IP нет → ещё раз шаг 6 (APN).
- IP есть, интернет плохой или «режет» — шаг 8.

---

## Шаг 8. Зафиксируйте TTL

Роутер уменьшает TTL пакетов на 1. Оператор может увидеть, что это не телефон, а раздача, и ограничить скорость.

Keenetic умеет задать TTL исходящих пакетов.

**Источник:** https://support.keenetic.com/hero-4g-plus/kn-2311/en/19729-changing-the-ttl-values.html

**Цитата:**
> Keenetic routers have the ability to control the TTL value for incoming (from your ISP) and outgoing (to your ISP) packets. By default, if we connect to the router to access the Internet, the TTL value will decrease by `1` when a packet passes through the router…
>
> To control the TTL value for outgoing packets on the selected interface, use the command:
>
> `interface {name} ip adjust-ttl send {ttl}`

**Перевод:**
> Роутеры Keenetic умеют управлять TTL входящих (от провайдера) и исходящих (к провайдеру) пакетов. По умолчанию при выходе в интернет через роутер TTL уменьшается на `1`.
>
> Для исходящих пакетов на выбранном интерфейсе:
>
> `interface {name} ip adjust-ttl send {ttl}`

### 8.1. Откройте командную строку Keenetic

В адресной строке браузера, если вы на `http://192.168.1.1/dashboard`, сотрите `dashboard` и поставьте `a`:

`http://192.168.1.1/a`

**Источник:** https://support.keenetic.com/hero-4g-plus/kn-2311/en/18480-command-line-interface--cli-.html

**Цитата:**
> Connecting to the web interface in the address bar of your browser, you will see an address like http://192.168.1.1/dashboard
>
> Erase the word dashboard and after the slash `/` add a small lowercase letter `a` of the English alphabet http://192.168.1.1/a

**Перевод:**
> В адресной строке будет что-то вроде `http://192.168.1.1/dashboard`.
>
> Сотрите `dashboard` и после `/` добавьте маленькую латинскую `a`: `http://192.168.1.1/a`.

Надёжнее Telnet/SSH на `192.168.1.1` (нужен пароль администратора).

### 8.2. Команды для Hero 4G+

Для Android-подобного TTL (МТС чаще смотрит это):

```bash

interface UsbLte0 ip adjust-ttl send 64
system configuration save
```

Если не помогло — то же самое со значением `65`:

```bash

interface UsbLte0 ip adjust-ttl send 65
system configuration save
```

На странице мобильного подключения в новых KeeneticOS иногда есть поле **Изменить TTL** — тогда задайте `64` там и сохраните, CLI не обязателен.

После сохранения перезагрузите роутер: **Система → Перезагрузка**.

---

## Шаг 9. Проверьте интернет с устройства в Wi-Fi

1. Подключите телефон или ноутбук к Wi-Fi Keenetic.
2. Откройте `https://ya.ru` и любой спидтест.
3. Сравните с телефоном на той же SIM (если карту снова вставите в телефон — вернитесь к шагам 4–7).

Ожидание:

- Сайты открываются, скорость близка к телефону — готово.
- Сайты открываются, скорость сильно ниже (сотни кбит/с) — МТС, скорее всего, ограничил карту как модем. TTL уже не поможет. Шаг 10.
- Сети/IP по-прежнему нет — шаг 10.

---

## Шаг 10. Если всё ещё не работает

Дальше это уже **не настройка Keenetic**, а условие тарифа МТС.

МТС для модемов и роутеров рекомендует отдельные тарифы (не смартфонный безлимит).

**Источник:** https://media.mts.ru/gadgets/186570

**Цитата:**
> В МТС очень хороший вариант — тариф «Для ноутбука» с гигантским месячным пакетом мобильного интернета.

**Перевод:** не требуется, текст на русском.

Сделайте так:

1. В приложении **Мой МТС** откройте свой тариф и поищите формулировки про модем, роутер, раздачу.
2. Спросите в чате МТС: «Можно ли эту SIM использовать в LTE-роутере?» Если ответ «нет» — попросите тариф/опцию **для модема или роутера** (часто «Для ноутбука»).
3. Не копируйте чужой IMEI и не подменяйте идентификатор устройства: это нарушение договора с оператором и может быть незаконно.

Hero 4G Plus умеет слать в модем диагностические AT-команды, но это не официальный «обход запрета МТС».

---

## Источники

- APN МТС: https://support.mts.ru/mts_mobilnyy_internet/nastroiki-mobilnogo-interneta/kak-nastroit-internet-na-telefone
- Встроенный модем Hero 4G+: https://support.keenetic.com/hero-4g-plus/kn-2311/en/13777-internet-connection-via-a-3g-4g,-lte-modem.html
- Интерфейс `UsbLte` / RNDIS: https://support.keenetic.com/hero-4g-plus/kn-2311/en/34991-5g-4g-3g-modem-connection-types--ras,-cdc-ethernet,-ndis,-qmi-.html
- TTL: https://support.keenetic.com/hero-4g-plus/kn-2311/en/19729-changing-the-ttl-values.html
- CLI Keenetic: https://support.keenetic.com/hero-4g-plus/kn-2311/en/18480-command-line-interface--cli-.html
- Тарифы МТС для модема: https://media.mts.ru/gadgets/186570
