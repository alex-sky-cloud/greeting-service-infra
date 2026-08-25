# VPS-провайдеры: email, повременная оплата и resize существующего сервера

Дата проверки: 18 августа 2026.

Условия: регистрация без обязательных телефона/SMS/KYC; почасовая или поминутная оплата; после остановки CPU/RAM не тарифицируются, но диск и IP могут оставаться платными; CPU/RAM/диск существующей VM изменяются без её удаления; нет обязательного невозвратного платежа за создание услуги.

| Провайдер | Язык | Тарификация | После остановки | Resize существующей VM |
|---|---|---|---|---|
| [Virtua.Cloud](https://www.virtua.cloud/ru/) | Русский сайт | Почасовая | Диск, IP, лицензия Windows | CPU, RAM, диск |
| [Serverspace](https://serverspace.io/) | Английский | 10 минут | Диск, IP и лицензии | CPU, RAM, диск |
| [Clouding.io](https://clouding.io/en/) | Английский | Почасовая | Диск | CPU, RAM, диск |
| [Exoscale](https://www.exoscale.com/) | Английский | До минуты | Подключённый том | CPU, RAM, диск |
| [Paperspace Core](https://www.paperspace.com/core) | Английский | Почасовая | Диск, IP и add-ons | Тип машины и диск |
| [Catalyst Cloud](https://catalystcloud.nz/) | Английский | Почасовая | Диск | CPU, RAM, persistent volume |

## Virtua.Cloud

Форма регистрации содержит только email, пароль, повтор пароля и сайт.

**Источник:** https://manager.virtua.cloud/register

**Цитата:**
> Email address  
> Password  
> Confirm Password  
> Website

**Источник:** https://www.virtua.cloud/ru/vps/hourly-billing

**Цитата:**
> Остановите сервер — CPU и RAM освобождаются; вы продолжаете платить только за зарезервированные ресурсы, такие как IP-адрес и диск.
>
> Ничто не обновляется автоматически, карта не списывается без вашего ведома, минимального срока нет.

**Источник:** https://www.virtua.cloud/ru/features

**Цитата:**
> Вы можете в любой момент запросить повышение или понижение конфигурации облачного сервера без потери данных.
>
> Переустановка не требуется — сервер лишь перезагрузится в процессе.

## Serverspace

**Источник:** https://serverspace.io/support/help/how-to-sign-up-in-the-serverspace-control-panel/

**Цитата:**
> There are several methods you can use to create an account:
> - using your email address;
> - sign up using services like Google or GitHub.

**Перевод:**
> Аккаунт можно создать по email либо через Google или GitHub.

**Источник:** https://serverspace.io/services/cloud-servers/

**Цитата:**
> When the server is powered off, charges only apply for disk space (SSD), backups, snapshots, licenses, and public IP addresses. You will NOT be charged for RAM and CPU.
>
> You can change vCPU, RAM, Volume, and bandwidth.

**Перевод:**
> При выключенном сервере списания применяются только к диску, резервным копиям, снимкам, лицензиям и публичным IP. За RAM и CPU плата не взимается.
>
> Можно изменить vCPU, RAM, том и пропускную способность.

**Источник:** https://serverspace.io/conditions/faq/

**Цитата:**
> Funds remaining on your balance after you delete servers or stop services are not charged and can be refunded.

**Перевод:**
> Неиспользованный остаток на балансе не списывается и может быть возвращён.

## Clouding.io

**Источник:** https://help.clouding.io/hc/en-us/articles/360011091939-Terms-of-Use-and-Service-Conditions

**Цитата:**
> CLOUDING.IO will ask for the verification, by electronic means (mail or SMS).
>
> a 1 euro charge will be done, being returned at the confirmation time.

**Перевод:**
> Проверка выполняется электронным способом — по почте или SMS.
>
> Проверочный платёж €1 возвращается при подтверждении.

**Источник:** https://clouding.io/en/

**Цитата:**
> Pay by the hour and without monthly minimums.
>
> From our control panel, you can archive your Cloud VPS Server ... you will only pay for the disk space you have contracted and you’ll stop paying for the CPU or RAM of your server.

**Перевод:**
> Почасовая оплата без месячного минимума.
>
> При архивировании через панель оплачивается только диск, а CPU и RAM больше не оплачиваются.

**Источник:** https://help.clouding.io/hc/en-us/articles/360010073119-How-to-resize-your-cloud-server

**Цитата:**
> Your Server(s) can be resized, that is, its/their resources can be increased or reduced whenever you want to.
>
> Your Server will restart during the process.

**Перевод:**
> Ресурсы существующего сервера можно увеличить или уменьшить; в процессе он перезапустится.

Диск можно только увеличить.

## Exoscale

**Источник:** https://community.exoscale.com/platform/quick-start/

**Цитата:**
> Verify your email address
>
> Register your credit card, or add some credits in advance
>
> The card will not be actually charged, the amount is only pre-authorized.

**Перевод:**
> Подтвердите email; зарегистрируйте карту или внесите средства заранее. С карты удерживается только временный холд, а не платёж.

**Источник:** https://community.exoscale.com/platform/billing/

**Цитата:**
> Most resources are metered on exact consumption usage or up to the minute.
>
> Powered off machines will only be charged for resources still in use. Therefore, we will continue to charge your account for the volume attached to your instance.

**Перевод:**
> Большинство ресурсов тарифицируется по фактическому потреблению или до минуты. После выключения VM оплачивается только остающийся в использовании ресурс — подключённый том.

**Источник:** https://community.exoscale.com/product/compute/instances/how-to/instance-scaling/

**Цитата:**
> you will be able to resize your instances up and down, scale instance type (the combination of CPU cores and RAM) and disk size
>
> every scaling operation needs to be performed on a stopped instance.

**Перевод:**
> Можно изменить CPU/RAM и размер диска существующего инстанса; для операции его нужно остановить.

## Paperspace Core

**Источник:** https://console.paperspace.com/signup

**Цитата:**
> Email address  
> Password  
> Create account

**Источник:** https://docs.digitalocean.com/products/paperspace/pricing/

**Цитата:**
> All Paperspace resources are billed on a per-hour basis.
>
> Machine compute charges apply only while a machine is powered on. When you shut down or power off a machine, compute billing stops for that machine.
>
> When a Paperspace machine is powered off, attached storage, public IP addresses, and other add-ons continue to be billed on an hourly basis.

**Перевод:**
> Все ресурсы Paperspace тарифицируются почасово. Compute списывается только при включённой машине; после выключения остаются оплачиваемыми подключённое хранилище, публичный IP и add-ons.

**Источник:** https://docs.digitalocean.com/products/paperspace/machines/how-to/resize/

**Цитата:**
> You can resize your machine’s machine type and disk size depending on your needs.
>
> Before resizing your machine, you need to turn off your machine.

**Перевод:**
> У существующей машины можно изменить тип и размер диска; перед resize её нужно выключить.

## Catalyst Cloud

**Источник:** https://catalystcloud.nz/signup/

**Цитата:**
> Full name  
> Email

**Источник:** https://docs.catalystcloud.nz/getting-started/services.html

**Цитата:**
> Catalyst Cloud charges by the hour.
>
> There is no minimum limit for the resources you need to consume on Catalyst Cloud per hour.

**Перевод:**
> Catalyst Cloud тарифицирует ресурсы почасово и не устанавливает минимальное потребление.

**Источник:** https://docs.catalystcloud.nz/compute/stop-instance.html

**Цитата:**
> Shelve will ... preserve the disk and deallocate the compute resources (CPU and RAM).
>
> We only charge for the disks ... while the instance is powered off.

**Перевод:**
> Режим Shelve сохраняет диск, освобождает CPU/RAM и оставляет только плату за диск.

**Источник:** https://docs.catalystcloud.nz/compute/resize-instance.html

**Цитата:**
> The resize operation can be used to change the flavor (increase or decrease the amount of CPU and RAM, or change the type) of a compute instance.

**Перевод:**
> Resize изменяет CPU/RAM существующего инстанса.

**Источник:** https://docs.catalystcloud.nz/block-storage/faq.html

**Цитата:**
> The block storage service supports the live extension of volumes regardless of whether that are boot volumes or additional volumes attached to your instance.

**Перевод:**
> Persistent volume можно увеличить без удаления VM; это работает и для загрузочного, и для дополнительного тома.
