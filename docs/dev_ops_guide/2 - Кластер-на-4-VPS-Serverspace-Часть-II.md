

## Оглавление

### Часть II — Кластер на 4 VPS Serverspace

11. [Цель и схема на 4 VPS](#11-%D1%86%D0%B5%D0%BB%D1%8C-%D0%B8-%D1%81%D1%85%D0%B5%D0%BC%D0%B0-%D0%BD%D0%B0-4-vps)
12. [Роли VPS и стартовые конфигурации](#12-%D1%80%D0%BE%D0%BB%D0%B8-vps-%D0%B8-%D1%81%D1%82%D0%B0%D1%80%D1%82%D0%BE%D0%B2%D1%8B%D0%B5-%D0%BA%D0%BE%D0%BD%D1%84%D0%B8%D0%B3%D1%83%D1%80%D0%B0%D1%86%D0%B8%D0%B8)
13. [Структура infra terraform под 4 VPS](#13-%D1%81%D1%82%D1%80%D1%83%D0%BA%D1%82%D1%83%D1%80%D0%B0-infraterraform-%D0%BF%D0%BE%D0%B4-4-vps)
14. [Провайдер Serverspace в Terraform](#14-%D0%BF%D1%80%D0%BE%D0%B2%D0%B0%D0%B9%D0%B4%D0%B5%D1%80-serverspace-%D0%B2-terraform)
15. [Параметры terraform tfvars для VPS](#15-%D0%BF%D0%B0%D1%80%D0%B0%D0%BC%D0%B5%D1%82%D1%80%D1%8B-terraform-tfvars-%D0%B4%D0%BB%D1%8F-vps)
16. [init plan apply для создания 4 VPS](#16-init-plan-apply-%D0%B4%D0%BB%D1%8F-%D1%81%D0%BE%D0%B7%D0%B4%D0%B0%D0%BD%D0%B8%D1%8F-4-vps)

***

# Часть II — Кластер на 4 VPS Serverspace

## 11. Цель и схема на 4 VPS

**Цель.** Локальный Terraform в Docker создает кластер из четырех VPS в Serverspace:

- VPS‑1 — control plane / платформа (Kubernetes или другой оркестратор).
- VPS‑2 — приложения (worker‑нода или Docker‑хост).
- VPS‑3 — PostgreSQL (отдельная нода, акцент на RAM и диск).
- VPS‑4 — GitLab (отдельный сервер, перенос текущего GitLab на него).


Коротко:

- **VPS-1** — это сервер, где стоит **сам Kubernetes**.
- **VPS-2** — это сервер, где будут работать **твои приложения** внутри Kubernetes.

То есть:

- на **VPS-1** — управление кластером;
- на **VPS-2** — запуск сервисов.

Если совсем просто:
**VPS-1 = мозг Kubernetes**,
**VPS-2 = место, где крутятся контейнеры**

Основная идея: разделить роли по VPS, чтобы:

- платформа не мешала приложениям;
- база данных имела отдельные ресурсы и дисковую подсистему;
- **GitLab** жил отдельно и не конкурировал ни с чем.

***

## 12. Роли VPS и стартовые конфигурации

Для старта берем конфигурации «не скудно, но без лишнего»:

**VPS‑1 — control plane**

- 2 vCPU
- 4 GB RAM
- 40–60 GB SSD

Роль: управляющие компоненты кластера, системные сервисы, API.

**VPS‑2 — приложения**

- 2 vCPU
- 4–8 GB RAM
- 40–80 GB SSD

Роль: запуск **backend‑сервисо**в, микросервисов, Docker‑контейнеров.

**VPS‑3 — PostgreSQL**

- 2–4 vCPU
- 8 GB RAM
- 80–160 GB SSD

**Роль**: основная база данных, приоритет по RAM и скорости диска.

- Для старта можно взять 2 vCPU и 8 GB RAM, с планом повышения vCPU/RAM по мере роста нагрузки.

**VPS‑4 — GitLab**

- 4 vCPU
- 8 GB RAM
- ≥100 GB SSD

Роль: GitLab, CI/CD, хранение репозиториев и артефактов.

***

## 13. Структура infra terraform под 4 VPS

В каталоге `infra/terraform` добавляем:

- `providers/serverspace.tf` — провайдер Serverspace.
- `modules/vps_control_plane/` — модуль для VPS‑1.
- `modules/vps_apps/` — модуль для VPS‑2.
- `modules/vps_postgres/` — модуль для VPS‑3.
- `modules/vps_gitlab/` — модуль для VPS‑4.
- `main.tf` — точка входа: подключает четыре модуля и задает связи.

Каждый модуль должен описывать:

- размер VPS (vCPU, RAM, SSD);
- локацию;
- образ (Linux дистрибутив);
- сетевые настройки;
- SSH‑ключи.

***

## 14. Провайдер Serverspace в Terraform

В `providers/serverspace.tf`:

- блок `provider "serverspace"` с:
    - токеном API из переменных окружения;
    - указанием региона;
    - базовыми настройками timeouts.

Пример структуры:

```hcl
provider "serverspace" {
  token  = var.serverspace_token
  region = var.serverspace_region
}
```

Переменные `serverspace_token` и `serverspace_region` задаются в `terraform.tfvars` и в `.env`.

***

## 15. Параметры terraform tfvars для VPS

В `terraform.tfvars` описываем:

- размеры для каждого VPS (control plane, apps, postgres, gitlab);
- SSH‑ключ;
- локацию (Россия или Беларусь);
- имена хостов и домены.

Пример логики:

```hcl
control_plane_cpu = 2
control_plane_ram = 4
control_plane_disk = 60

apps_cpu  = 2
apps_ram  = 8
apps_disk = 80

postgres_cpu  = 2
postgres_ram  = 8
postgres_disk = 120

gitlab_cpu  = 4
gitlab_ram  = 8
gitlab_disk = 120
```


***

## 16. init plan apply для создания 4 VPS

Шаги в Git Bash:

```bash
cd '/d/Project_infra/greeting-service-infra'
./scripts/terraform-docker.sh init
./scripts/terraform-docker.sh validate
./scripts/terraform-docker.sh plan
./scripts/terraform-docker.sh apply
```

Ожидаемый результат:

- Terraform создает четыре VPS в Serverspace с нужными параметрами.
- После `apply` доступны:
    - VPS‑1 по SSH для установки control plane;
    - VPS‑2 для приложений;
    - VPS‑3 для PostgreSQL;
    - VPS‑4 для GitLab.

***


