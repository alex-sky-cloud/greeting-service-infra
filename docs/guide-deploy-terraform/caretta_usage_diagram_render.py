# -*- coding: utf-8 -*-
"""Render docs/images/traffic-visibility/caretta-radar-grafana.png"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(r"D:/Project_infra/greeting-service-infra")
OUT = ROOT / "docs/images/traffic-visibility/caretta-radar-grafana.png"


def font(size: int, bold: bool = False):
    for p in (
        ("C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
        ("C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf"),
    ):
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def box(draw, xy, title, lines, fill=(255, 255, 255), outline=(55, 65, 81), tc=(0, 112, 192)):
    draw.rounded_rectangle(xy, radius=10, fill=fill, outline=outline, width=2)
    x1, y1, _, _ = xy
    y = y1 + 10
    draw.text((x1 + 12, y), title, fill=tc, font=font(14, True))
    y += 22
    for ln in lines:
        draw.text((x1 + 12, y), ln, fill=(30, 41, 59), font=font(12))
        y += 18


def arrow(draw, a, b, color=(71, 85, 105)):
    draw.line([a, b], fill=color, width=2)
    ex, ey = b
    draw.polygon([(ex, ey), (ex - 10, ey - 5), (ex - 10, ey + 5)], fill=color)


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img = Image.new("RGB", (1180, 720), (248, 250, 252))
    d = ImageDraw.Draw(img)
    d.text((20, 12), "Как использовать Caretta при установке через Helm (namespace dev)", fill=(30, 41, 59), font=font(18, True))
    d.text((20, 42), "Radar Traffic — не работает с этим chart | Grafana из Caretta — рабочий путь", fill=(100, 116, 139), font=font(12))

    box(d, (20, 80, 260, 200), "Radar (локально)", ["kubectl radar", "Topology → Resources ✅", "Traffic ❌ (нет operator)"])
    box(d, (20, 230, 260, 360), "Git Bash + kubectl", ["KUBECONFIG=...yaml", "port-forward Grafana", "curl → трафик для карты"])

    d.rounded_rectangle((300, 70, 1140, 680), radius=14, outline=(0, 112, 192), width=3)
    d.text((320, 78), "Kubernetes, namespace dev — release caretta (helm)", fill=(0, 112, 192), font=font(13, True))

    box(d, (320, 110, 560, 210), "caretta DaemonSet", ["eBPF на worker", "метрики /metrics :7117"], fill=(240, 255, 240), outline=(34, 139, 34), tc=(34, 139, 34))
    box(d, (580, 110, 820, 210), "caretta-vm", ["Victoria Metrics", "caretta_links_observed"], fill=(245, 240, 255), outline=(90, 60, 150), tc=(90, 60, 150))
    box(d, (840, 110, 1110, 210), "caretta-grafana", ["Grafana (в chart!)", "ClusterIP :80", "дашборд Node Graph"], fill=(255, 248, 230), outline=(200, 100, 20), tc=(200, 100, 20))

    box(d, (320, 250, 560, 350), "greeting-service", ["ваше приложение", "трафик HTTP/JDBC"], fill=(240, 255, 240), outline=(34, 139, 34), tc=(34, 139, 34))

    arrow(d, (440, 210), (440, 250), (34, 139, 34))
    arrow(d, (560, 160), (580, 160), (90, 60, 150))
    arrow(d, (820, 160), (840, 160), (200, 100, 20))

    d.text((320, 380), "✅ Рабочий путь:", fill=(34, 139, 34), font=font(14, True))
    steps = [
        "1. kubectl port-forward -n dev svc/caretta-grafana 3000:80",
        "2. Браузер http://localhost:3000 (admin + пароль из Secret caretta-grafana)",
        "3. Дашборд Caretta — карта связей client → server",
        "4. curl к greeting-service — появятся линии на карте",
    ]
    y = 408
    for s in steps:
        d.text((340, y), s, fill=(30, 41, 59), font=font(12))
        y += 22

    d.text((320, 510), "❌ Radar → Traffic:", fill=(180, 40, 40), font=font(14, True))
    d.text(
        (340, 538),
        "Radar ищет Caretta operator + CRD. Chart groundcover/caretta их не создаёт.",
        fill=(30, 41, 59),
        font=font(12),
    )
    d.text(
        (340, 562),
        "Поэтому кнопка «Install caretta» в Radar остаётся, хотя pod caretta-* уже Running.",
        fill=(30, 41, 59),
        font=font(12),
    )

    arrow(d, (260, 295), (320, 160), (0, 112, 192))
    d.text((265, 270), "port-forward", fill=(0, 112, 192), font=font(11, True))
    arrow(d, (975, 210), (1050, 80), (200, 100, 20))
    d.text((1060, 55), "Браузер\nlocalhost:3000", fill=(200, 100, 20), font=font(11))

    img.save(OUT, "PNG")
    print(f"Saved: {OUT}")


if __name__ == "__main__":
    main()
