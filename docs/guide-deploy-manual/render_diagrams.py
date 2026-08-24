# -*- coding: utf-8 -*-
"""
PNG-схемы для гайда (без Mermaid).

Важно для Word: картинка вставляется ~6.5\" шириной.
Чтобы текст на странице был ~14–16 pt, на холсте ~1600–1800 px
нужен кегль тела ~48 px, заголовков блоков ~52–56 px.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT_DIR = Path(__file__).resolve().parent / "images"

BG = (248, 246, 242)
PKG = (247, 243, 238)
PKG_OUT = (184, 155, 132)
NODE = (185, 217, 223)
NODE_OUT = (107, 158, 170)
SOFT = (231, 239, 217)
SOFT2 = (238, 244, 227)
CLOUD = (239, 231, 247)
CLOUD_OUT = (140, 107, 177)
TEXT = (40, 40, 40)
ARROW = (80, 80, 80)
TITLE = (0, 112, 192)
MUTED = (70, 70, 70)


def font(size: int, bold: bool = False):
    candidates = (
        ("C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
        ("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"),
        (
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
            if bold
            else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        ),
    )
    for p in candidates:
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def rounded(d, xy, fill, outline, width=4, radius=18):
    d.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


def text(d, x, y, s, *, size=48, bold=False, fill=TEXT):
    d.text((x, y), s, fill=fill, font=font(size, bold))


def lines(d, x, y, items, *, size=44, fill=TEXT, gap=58):
    for i, ln in enumerate(items):
        d.text((x, y + i * gap), ln, fill=fill, font=font(size))


def arrow_h(d, x1, y, x2, color=ARROW):
    d.line([(x1, y), (x2, y)], fill=color, width=5)
    d.polygon([(x2, y), (x2 - 18, y - 12), (x2 - 18, y + 12)], fill=color)


def arrow_v(d, x, y1, y2, color=ARROW):
    d.line([(x, y1), (x, y2)], fill=color, width=5)
    d.polygon([(x, y2), (x - 12, y2 - 18), (x + 12, y2 - 18)], fill=color)


def panel(d, xy, fill, outline, title, body, *, title_size=52, body_size=44):
    rounded(d, xy, fill, outline)
    x1, y1, _, _ = xy
    text(d, x1 + 28, y1 + 22, title, size=title_size, bold=True, fill=TITLE)
    lines(d, x1 + 28, y1 + 100, body, size=body_size, gap=max(56, body_size + 12))


def render_architecture() -> Path:
    """Обзор: четыре крупных контура сверху вниз — читаемо в Word."""
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    w, h = 1800, 2200
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)

    text(d, 40, 30, "Схема 1. Архитектура решения (крупный обзор)", size=56, bold=True, fill=TITLE)
    text(d, 40, 100, "Читайте блоки сверху вниз. Стрелки = направление работы.", size=40, fill=MUTED)

    # 1 Local
    panel(
        d,
        (40, 180, 1760, 420),
        PKG,
        PKG_OUT,
        "1. Локальный ПК разработчика",
        [
            "IDE (IntelliJ) — пишете код Spring Boot",
            "git — отправляете код в GitLab (git push)",
            "ssh / kubectl / helm — управляете серверами и кластером",
            "Terraform НЕ используется — инфраструктура руками через CLI",
        ],
    )
    arrow_v(d, 900, 420, 470)

    # 2 Devtools
    panel(
        d,
        (40, 470, 1760, 760),
        PKG,
        PKG_OUT,
        "2. VPS «devtools» — фабрика CI/CD",
        [
            "GitLab CE — хранит git-репозиторий и запускает pipeline",
            "GitLab Runner — на этом же сервере выполняет сборку и тесты",
            "Docker Registry :5000 — склад готовых Docker-образов",
        ],
    )
    arrow_v(d, 900, 760, 810)

    # 3 k3s
    panel(
        d,
        (40, 810, 1760, 1220),
        NODE,
        NODE_OUT,
        "3. VPS-кластер Kubernetes (k3s) — где живёт приложение",
        [
            "k8s-master — «мозг» кластера (API :6443, kubeconfig)",
            "k8s-worker-1 / worker-2 — здесь крутятся Pod с greeting-service",
            "Deployment + Service + Secret/ConfigMap + PostgreSQL (StatefulSet)",
            "Service типа NodePort — «дверь», в которую стучится Traefik",
        ],
    )
    arrow_v(d, 900, 1220, 1270)

    # 4 Traefik + Internet side by side conceptually in one panel
    panel(
        d,
        (40, 1270, 1760, 1620),
        CLOUD,
        CLOUD_OUT,
        "4. Отдельный кластер Traefik (edge) + Интернет",
        [
            "Два VPS: traefik-1 и traefik-2 (НЕ Ingress от облака)",
            "Floating IP — стабильный публичный адрес на :80 / :443",
            "DNS A-запись домена указывает на Floating IP Traefik",
            "Traefik по Host(имя) пересылает запрос на NodePort k3s",
        ],
    )
    arrow_v(d, 900, 1620, 1670)

    # 5 MinIO
    panel(
        d,
        (40, 1670, 1760, 1920),
        SOFT,
        NODE_OUT,
        "5. VPS «storage» — S3 через MinIO",
        [
            "MinIO даёт S3-совместимый API на своём сервере",
            "Bucket greeting-artifacts + AccessKey / SecretKey",
            "Нужен для артефактов/бэкапов без облачного S3-плагина",
        ],
    )

    text(d, 40, 1980, "Два главных потока (запомните):", size=48, bold=True, fill=TITLE)
    text(d, 40, 2050, "A) Код:  ПК → git push → GitLab → Runner → Registry → Helm → Pod в k3s", size=40)
    text(d, 40, 2110, "B) Трафик: браузер → DNS → Traefik Floating IP → NodePort → Pod", size=40)

    out = OUT_DIR / "architecture-manual.png"
    img.save(out, optimize=True)
    print(f"Saved: {out}")
    return out


def render_architecture_traffic() -> Path:
    """Отдельная крупная схема только пути HTTP-запроса."""
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    w, h = 1800, 1100
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)

    text(d, 40, 30, "Схема 1б. Путь HTTP-запроса из интернета", size=56, bold=True, fill=TITLE)
    text(d, 40, 100, "Один запрос GET /api/greeting — слева направо", size=40, fill=MUTED)

    boxes = [
        (40, 220, 340, 520, CLOUD, CLOUD_OUT, "1. Браузер", ["пользователь", "открывает", "сайт"]),
        (380, 220, 700, 520, PKG, PKG_OUT, "2. DNS", ["A-запись", "имя → IP", "Traefik"]),
        (740, 220, 1100, 520, CLOUD, CLOUD_OUT, "3. Traefik", ["Floating IP", "правило Host", "прокси"]),
        (1140, 220, 1460, 520, NODE, NODE_OUT, "4. NodePort", ["порт на", "worker k3s", "Service"]),
        (1500, 220, 1760, 520, SOFT, NODE_OUT, "5. Pod", ["Spring Boot", "/api/", "greeting"]),
    ]
    for x1, y1, x2, y2, fill, outl, title, body in boxes:
        panel(d, (x1, y1, x2, y2), fill, outl, title, body, title_size=44, body_size=36)
    for x in (340, 700, 1100, 1460):
        arrow_h(d, x + 4, 370, x + 36)

    text(d, 40, 600, "Аналогия: Traefik — швейцар в холле. Он смотрит на табличку (Host)", size=42)
    text(d, 40, 670, "и провожает гостя в нужный номер (NodePort → Pod).", size=42)
    text(d, 40, 760, "Почему Traefik отдельно от k3s?", size=48, bold=True, fill=TITLE)
    text(d, 40, 840, "Чтобы вход из интернета не зависел от «кнопки Ingress» конкретного облака.", size=40)
    text(d, 40, 910, "Кластер Traefik можно перенести на другого VPS-провайдера вместе с конфигом.", size=40)
    text(d, 40, 990, "k3s при этом остаётся «внутренней кухней» приложения.", size=40)

    out = OUT_DIR / "architecture-traffic.png"
    img.save(out, optimize=True)
    print(f"Saved: {out}")
    return out


def render_cicd() -> Path:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    w, h = 1800, 2000
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)

    text(d, 40, 30, "Схема 2. CI/CD: от git push до работающего Pod", size=56, bold=True, fill=TITLE)
    text(d, 40, 100, "Шаги идут сверху вниз. Каждый блок = одна понятная роль.", size=40, fill=MUTED)

    steps = [
        (180, PKG, PKG_OUT, "Шаг 1. Developer", [
            "Вы делаете git push в ветку develop",
            "Код уходит на GitLab CE (devtools)",
        ]),
        (430, NODE, NODE_OUT, "Шаг 2. GitLab CE", [
            "Видит новый commit и стартует pipeline",
            "Читает файл ci/.gitlab-ci.yml",
        ]),
        (680, SOFT, NODE_OUT, "Шаг 3. GitLab Runner", [
            "./gradlew test — сборка и тесты",
            "docker build + docker push — образ в Registry",
        ]),
        (930, SOFT2, NODE_OUT, "Шаг 4. Docker Registry", [
            "Образ: DEVTOOLS_IP:5000/greeting-service:<sha>",
            "Это «склад» версий приложения",
        ]),
        (1180, CLOUD, CLOUD_OUT, "Шаг 5. Helm в k3s", [
            "helm upgrade --install в namespace dev",
            "Kubernetes поднимает новый Pod с образом",
        ]),
        (1430, PKG, PKG_OUT, "Шаг 6. Проверка снаружи", [
            "Traefik уже знает Host → NodePort",
            "curl http://greeting-dev.example.com/api/greeting",
        ]),
    ]

    for y, fill, outl, title, body in steps:
        panel(d, (40, y, 1760, y + 220), fill, outl, title, body, title_size=50, body_size=42)
        if y < 1430:
            arrow_v(d, 900, y + 220, y + 250)

    text(d, 40, 1720, "Правила веток:", size=48, bold=True, fill=TITLE)
    text(d, 40, 1790, "develop → авто-деплой в dev", size=42)
    text(d, 40, 1850, "main → деплой в prod только вручную (кнопка в GitLab)", size=42)
    text(d, 40, 1910, "feature/* → только build/test, без деплоя", size=42)

    out = OUT_DIR / "cicd-manual.png"
    img.save(out, optimize=True)
    print(f"Saved: {out}")
    return out


def render_tech_map() -> Path:
    """Карта «что за технология» — экскурсия одной картинкой."""
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    w, h = 1800, 2100
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)

    text(d, 40, 30, "Схема 0. Экскурсия: что делает каждая технология", size=52, bold=True, fill=TITLE)
    text(d, 40, 100, "Короткая аналогия — чтобы схема архитектуры читалась легче.", size=40, fill=MUTED)

    items = [
        (170, PKG, PKG_OUT, "git / GitLab", [
            "git — «сохранить версию кода»",
            "GitLab — сейф с историей + кнопка «собери и выкати»",
        ]),
        (400, NODE, NODE_OUT, "GitLab Runner", [
            "Рабочий на заводе: берёт задание из GitLab",
            "и выполняет скрипты сборки на сервере",
        ]),
        (630, SOFT, NODE_OUT, "Docker + Registry", [
            "Docker — упаковка приложения в контейнер",
            "Registry — склад этих упаковок по версиям",
        ]),
        (860, NODE, NODE_OUT, "Kubernetes (k3s)", [
            "Оркестратор: следит, чтобы нужное число Pod жило",
            "k3s — лёгкий Kubernetes на обычных VPS",
        ]),
        (1090, CLOUD, CLOUD_OUT, "Traefik", [
            "Входная дверь из интернета",
            "Смотрит имя сайта (Host) и ведёт на нужный сервис",
        ]),
        (1320, SOFT2, NODE_OUT, "Helm", [
            "«Установщик» приложения в Kubernetes",
            "Один chart = набор манифестов + values",
        ]),
        (1550, PKG, PKG_OUT, "kubectl / MinIO / PostgreSQL", [
            "kubectl — пульт диагностики кластера",
            "MinIO — свой S3; PostgreSQL — база данных сервиса",
        ]),
    ]
    for y, fill, outl, title, body in items:
        panel(d, (40, y, 1760, y + 200), fill, outl, title, body, title_size=48, body_size=40)

    text(d, 40, 1800, "Зачем схема + текст вместе?", size=48, bold=True, fill=TITLE)
    text(d, 40, 1880, "Схема даёт карту. Текст рядом объясняет «зачем этот блок».", size=40)
    text(d, 40, 1950, "Так технология запоминается быстрее, чем только сухой список команд.", size=40)

    out = OUT_DIR / "tech-map.png"
    img.save(out, optimize=True)
    print(f"Saved: {out}")
    return out


def main() -> None:
    render_tech_map()
    render_architecture()
    render_architecture_traffic()
    render_cicd()


if __name__ == "__main__":
    main()
