# -*- coding: utf-8 -*-
"""Render docs/images/helm/helm-flow.png"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(r"D:/Project_infra/greeting-service-infra/docs/images/helm/helm-flow.png")


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


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img = Image.new("RGB", (1180, 700), (248, 250, 252))
    d = ImageDraw.Draw(img)
    d.text((20, 12), "Helm: от chart до release в Kubernetes", fill=(30, 41, 59), font=font(18, True))

    box(d, (20, 50, 280, 200), "Chart (файлы)", [
        "Chart.yaml — метаданные",
        "values.yaml — defaults",
        "values-dev.yaml — dev",
        "templates/ — шаблоны K8s",
    ], fill=(245, 240, 255), outline=(90, 60, 150), tc=(90, 60, 150))

    box(d, (310, 50, 560, 200), "helm template / lint", [
        "Проверка синтаксиса",
        "Рендер YAML без apply",
        "Локальный ПК (Git Bash)",
    ], fill=(255, 248, 230), outline=(200, 100, 20), tc=(200, 100, 20))

    box(d, (590, 50, 860, 200), "helm upgrade --install", [
        "Release: greeting-service",
        "namespace: dev",
        "-f values + values-dev",
        "--set image.tag=...",
    ], fill=(240, 255, 240), outline=(34, 139, 34), tc=(34, 139, 34))

    box(d, (890, 50, 1160, 200), "Release + revision", [
        "История: helm history",
        "Откат: helm rollback",
        "Статус: helm status",
    ], fill=(230, 245, 255), outline=(0, 112, 192), tc=(0, 112, 192))

    arrow(d, (280, 125), (310, 125))
    arrow(d, (560, 125), (590, 125))
    arrow(d, (860, 125), (890, 125))

    d.rounded_rectangle((20, 230, 1160, 520), radius=14, outline=(55, 65, 81), width=2)
    d.text((35, 238), "Объекты в кластере (namespace dev)", fill=(55, 65, 81), font=font(13, True))

    objs = [
        (40, 280, 260, 360, "Deployment", ["greeting-service", "replicas, probes"]),
        (290, 280, 510, 360, "Service", ["ClusterIP :80", "→ pod :8080"]),
        (540, 280, 760, 360, "Ingress", ["host: greeting-dev...", "class: nginx"]),
        (790, 280, 1010, 360, "ServiceAccount", ["greeting-service"]),
        (1040, 280, 1140, 360, "Secret*", ["greeting-service-secret", "*отдельно"]),
    ]
    for x1, y1, x2, y2, title, lines in objs:
        box(d, (x1, y1, x2, y2), title, lines, fill=(255, 255, 255))

    arrow(d, (150, 360), (150, 400), (34, 139, 34))
    arrow(d, (400, 360), (400, 400), (34, 139, 34))
    arrow(d, (650, 360), (650, 400), (34, 139, 34))
    d.text((120, 410), "Pod Spring Boot", fill=(34, 139, 34), font=font(11, True))
    d.text((360, 410), "HTTP :80", fill=(34, 139, 34), font=font(11))
    d.text((600, 410), "внешний host", fill=(34, 139, 34), font=font(11))

    d.text((35, 540), "Цикл жизни:", fill=(30, 41, 59), font=font(12, True))
    d.text(
        (35, 565),
        "lint → template (проверка) → upgrade --install (деплой) → kubectl get pods → history/rollback при ошибке",
        fill=(100, 116, 139),
        font=font(11),
    )
    d.text(
        (35, 590),
        "values-dev.yaml переопределяет image, host, resources для dev без правки templates/",
        fill=(100, 116, 139),
        font=font(11),
    )

    img.save(OUT, "PNG")
    print(f"Saved: {OUT}")


if __name__ == "__main__":
    main()
