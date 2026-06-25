# -*- coding: utf-8 -*-
"""PNG-диаграммы для docs/terraform-gitlab-vps-guide.md"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(r"D:/Project_infra/greeting-service-infra/docs/Images-docs")


def font(size: int, bold: bool = False):
    for p in (
        ("C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
        ("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"),
    ):
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def box(d, xy, title, lines, fill=(255, 255, 255), outline=(55, 65, 81), tc=(0, 112, 192)):
    d.rounded_rectangle(xy, radius=10, fill=fill, outline=outline, width=2)
    x1, y1, _, _ = xy
    y = y1 + 10
    d.text((x1 + 12, y), title, fill=tc, font=font(13, True))
    y += 22
    for ln in lines:
        d.text((x1 + 12, y), ln, fill=(30, 41, 59), font=font(11))
        y += 17


def arrow(d, a, b, color=(71, 85, 105)):
    d.line([a, b], fill=color, width=2)
    ex, ey = b
    d.polygon([(ex, ey), (ex - 10, ey - 5), (ex - 10, ey + 5)], fill=color)


def save(img: Image.Image, name: str) -> None:
    path = OUT / name
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(path, "PNG")
    print(f"saved {path}")


def diagram_terraform_cycle() -> None:
    w, h = 1100, 420
    img = Image.new("RGB", (w, h), (248, 250, 252))
    d = ImageDraw.Draw(img)
    d.text((24, 14), "Цикл Terraform: от кода до облака", fill=(15, 23, 42), font=font(16, True))

    box(d, (24, 60, 200, 170), "1. .tf файлы", ["HCL-конфигурация", "provider, resource", "variables"])
    box(d, (230, 60, 410, 190), "2. terraform init", ["Скачивание провайдера", "twc, serverspace...", "terraform init"], fill=(239, 246, 255))
    box(d, (440, 60, 620, 190), "3. terraform plan", ["План изменений", "diff без apply", "terraform plan"], fill=(236, 253, 245))
    box(d, (650, 60, 830, 190), "4. terraform apply", ["Создание ресурсов", "API облака", "terraform apply"], fill=(255, 251, 235))
    box(d, (860, 60, 1060, 190), "5. state", ["terraform.tfstate", "ID ресурсов", "локально / S3"], fill=(243, 244, 246))

    arrow(d, (200, 120), (230, 120))
    arrow(d, (410, 125), (440, 125))
    arrow(d, (620, 125), (650, 125))
    arrow(d, (830, 125), (860, 125))

    box(d, (24, 230, 520, 360), "Локальный Docker на ПК", ["terraform init/plan/apply", "state: terraform.tfstate", "секреты: docker/.env"], fill=(236, 253, 245), tc=(5, 150, 105))
    box(d, (560, 230, 1060, 360), "Облачные API", ["Timeweb / Serverspace", "только HTTP-вызовы", "создание VPS, K8s…"], fill=(254, 242, 242), tc=(185, 28, 28))
    arrow(d, (520, 295), (560, 295), color=(185, 28, 28))

    save(img, "terraform-cycle.png")


def diagram_local_docker() -> None:
    w, h = 1100, 520
    img = Image.new("RGB", (w, h), (248, 250, 252))
    d = ImageDraw.Draw(img)
    d.text((24, 14), "Ядро: Terraform ТОЛЬКО на персональном ПК в Docker", fill=(15, 23, 42), font=font(16, True))

    box(d, (24, 55, 340, 210), "Ваш ПК (Windows)", [
        "D:\\Project_infra\\greeting-service-infra",
        "infra/terraform/ — .tf, state",
        "docker/.env — токены API",
    ], fill=(236, 253, 245), tc=(5, 150, 105))

    box(d, (380, 55, 680, 210), "Docker: hashicorp/terraform", [
        "docker compose run terraform",
        "scripts/terraform-docker.sh",
        "init / plan / apply",
    ], fill=(239, 246, 255))

    box(d, (720, 55, 1060, 210), "Облака (цель API)", [
        "Timeweb, Serverspace…",
        "создаются VPS, K8s, БД",
        "Terraform в облаке НЕ работает",
    ], fill=(255, 251, 235))

    arrow(d, (340, 130), (380, 130))
    arrow(d, (680, 130), (720, 130))

    d.rounded_rectangle((24, 240, 520, 380), radius=10, outline=(220, 38, 38), width=3)
    d.text((272, 255), "ЗАПРЕЩЕНО", fill=(185, 28, 28), font=font(13, True), anchor="mm")
    box(d, (40, 275, 500, 365), "HCP Terraform / Terraform Cloud", [
        "блок cloud { } в .tf",
        "remote execution в облаке HashiCorp",
        "state на чужом сервисе",
    ], fill=(254, 242, 242), outline=(220, 38, 38), tc=(185, 28, 28))

    d.rounded_rectangle((580, 240, 1060, 380), radius=10, outline=(5, 150, 105), width=3)
    d.text((820, 255), "РАЗРЕШЕНО", fill=(5, 120, 85), font=font(13, True), anchor="mm")
    box(d, (596, 275, 1044, 365), "Локальный Docker CLI", [
        "terraform.tfstate на диске ПК",
        "provider-плагины в .terraform/",
        "вы сами запускаете plan/apply",
    ], fill=(236, 253, 245), outline=(5, 150, 105), tc=(5, 120, 85))

    d.text((24, 450), "Provider-плагины (twc, serverspace) скачиваются при init, но выполняются внутри контейнера на вашем ПК.", fill=(71, 85, 105), font=font(11))
    save(img, "terraform-local-docker.png")


def diagram_gitlab_vps_arch() -> None:
    w, h = 1100, 520
    img = Image.new("RGB", (w, h), (248, 250, 252))
    d = ImageDraw.Draw(img)
    d.text((24, 14), "VPS Timeweb Cloud MSK 100: GitLab + Registry + Runner", fill=(15, 23, 42), font=font(16, True))

    d.rounded_rectangle((180, 70, 920, 400), radius=14, fill=(239, 246, 255), outline=(37, 99, 235), width=3)
    d.text((550, 88), "Cloud MSK 100 — Москва", fill=(30, 64, 175), font=font(14, True), anchor="mm")
    d.text((550, 108), "8 vCPU · 12 GB RAM · 100 GB NVMe", fill=(71, 85, 105), font=font(11), anchor="mm")

    box(d, (220, 130, 480, 250), "GitLab (Omnibus)", ["Rails / Puma / Sidekiq", "PostgreSQL, Redis", "Gitaly, nginx"], fill=(255, 255, 255))
    box(d, (520, 130, 780, 250), "Container Registry", ["registry.example.com", "образы CI/CD", "встроен в Omnibus"], fill=(236, 253, 245))
    box(d, (220, 280, 480, 380), "GitLab Runner", ["shell или docker executor", "теги: self-hosted", "сборка и deploy"], fill=(255, 251, 235))
    box(d, (520, 280, 780, 380), "Let's Encrypt", ["HTTPS для GitLab", "порты 80 / 443", "бесплатный TLS"], fill=(243, 244, 246))

    box(d, (24, 150, 160, 250), "Разработчик", ["git push", "git clone", "CI pipeline"])
    box(d, (940, 150, 1060, 250), "Интернет", ["HTTPS", "Docker pull", "Web UI"])

    arrow(d, (160, 200), (220, 200))
    arrow(d, (780, 200), (940, 200))
    arrow(d, (160, 330), (220, 330))

    d.text((24, 430), "Один VPS для учебного/небольшого стенда; GitLab рекомендует Runner на отдельной машине для production.", fill=(71, 85, 105), font=font(11))
    save(img, "gitlab-vps-architecture.png")


def diagram_dns_dynadot() -> None:
    w, h = 1100, 400
    img = Image.new("RGB", (w, h), (248, 250, 252))
    d = ImageDraw.Draw(img)
    d.text((24, 14), "DNS: Dynadot → A-запись → публичный IP VPS", fill=(15, 23, 42), font=font(16, True))

    box(d, (24, 70, 240, 190), "Dynadot", ["NS: dyna-ns.net", "DNS Settings", "Subdomain Records"], fill=(239, 246, 255))
    box(d, (280, 70, 500, 190), "A-запись", ["gitlab → 1.2.3.4", "registry → 1.2.3.4", "TTL / propagation"], fill=(236, 253, 245))
    box(d, (540, 70, 760, 190), "Публичный IPv4", ["Timeweb floating IP", "180 ₽/мес", "привязка к VPS"], fill=(255, 251, 235))
    box(d, (800, 70, 1060, 190), "GitLab VPS", ["nginx :443", "Let's Encrypt", "проверка домена"], fill=(243, 244, 246))

    arrow(d, (240, 130), (280, 130))
    arrow(d, (500, 130), (540, 130))
    arrow(d, (760, 130), (800, 130))

    box(
        d,
        (24, 230, 1060, 360),
        "Важно для Let's Encrypt",
        [
            "До gitlab-ctl reconfigure A-запись gitlab.ваш-домен должен указывать на IP VPS",
            "Порты 80 и 443 открыты с интернета — иначе выпуск сертификата не пройдёт",
            "DNS в Timeweb (dns.tf) не обязателен, если зона остаётся в Dynadot",
        ],
        fill=(255, 255, 255),
        tc=(185, 28, 28),
    )
    save(img, "gitlab-dns-dynadot.png")


def diagram_multi_cloud() -> None:
    w, h = 1100, 440
    img = Image.new("RGB", (w, h), (248, 250, 252))
    d = ImageDraw.Draw(img)
    d.text((24, 14), "Один Terraform — несколько облаков (multi-provider)", fill=(15, 23, 42), font=font(16, True))

    box(d, (380, 60, 720, 160), "main.tf", ["provider twc { }", "provider serverspace { }", "alias при необходимости"], fill=(239, 246, 255))

    box(d, (24, 200, 320, 320), "Timeweb Cloud", ["twc_server.gitlab", "twc_k8s_cluster", "twc_database_cluster"], fill=(236, 253, 245))
    box(d, (390, 200, 710, 320), "Serverspace", ["serverspace_server", "другой регион/цены", "свой API token"], fill=(255, 251, 235))
    box(d, (760, 200, 1060, 320), "Общий state", ["terraform.tfstate", "или remote S3", "один каталог infra/"], fill=(243, 244, 246))

    arrow(d, (550, 160), (172, 200))
    arrow(d, (550, 160), (550, 200))
    arrow(d, (550, 160), (910, 200))

    d.text((24, 370), "Каждый provider — отдельная аутентификация; ресурсы разных облаков описываются в одних .tf файлах.", fill=(71, 85, 105), font=font(11))
    save(img, "terraform-multi-cloud.png")


def diagram_https_le() -> None:
    w, h = 1100, 400
    img = Image.new("RGB", (w, h), (248, 250, 252))
    d = ImageDraw.Draw(img)
    d.text((24, 14), "HTTPS: external_url + Let's Encrypt в GitLab Omnibus", fill=(15, 23, 42), font=font(16, True))

    box(d, (24, 70, 250, 190), "/etc/gitlab/gitlab.rb", ["external_url https://...", "letsencrypt enable", "registry_external_url"], fill=(239, 246, 255))
    box(d, (290, 70, 520, 190), "gitlab-ctl reconfigure", ["Chef recipes", "nginx + certs", "автообновление ~90 дней"], fill=(236, 253, 245))
    box(d, (560, 70, 790, 190), "Let's Encrypt", ["HTTP-01 challenge", "порт 80", "бесплатно"], fill=(255, 251, 235))
    box(d, (830, 70, 1060, 190), "Клиент", ["https://gitlab...", "Docker login registry", "доверенный TLS"], fill=(243, 244, 246))

    arrow(d, (250, 130), (290, 130))
    arrow(d, (520, 130), (560, 130))
    arrow(d, (790, 130), (830, 130))

    box(
        d,
        (24, 230, 1060, 360),
        "Стоимость TLS",
        ["Let's Encrypt — бесплатно", "Альтернатива: свой сертификат (платный CA) — не требуется для учебного стенда", "Dynadot DNS не продаёт SSL для VPS — сертификат на стороне GitLab"],
        fill=(255, 255, 255),
        tc=(5, 150, 105),
    )
    save(img, "gitlab-https-letsencrypt.png")


def main() -> None:
    diagram_terraform_cycle()
    diagram_local_docker()
    diagram_gitlab_vps_arch()
    diagram_dns_dynadot()
    diagram_multi_cloud()
    diagram_https_le()


if __name__ == "__main__":
    main()
