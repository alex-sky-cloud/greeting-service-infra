# -*- coding: utf-8 -*-
"""Render docs/images/gitlab/gitlab-ci-pipeline.png"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(r"D:/Project_infra/greeting-service-infra/docs/images/gitlab/gitlab-ci-pipeline.png")


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
    w, h = 1100, 520
    img = Image.new("RGB", (w, h), (248, 250, 252))
    d = ImageDraw.Draw(img)
    d.text((24, 14), "GitLab CI/CD: greeting-service-infra (shell executor на devtools)", fill=(15, 23, 42), font=font(16, True))

    box(d, (24, 52, 190, 150), "1. Git push", ["Локальный ПК", "git push gitlab develop"])
    box(d, (220, 52, 430, 170), "2. stage: build", ["build-and-test", "./gradlew test bootJar", "artifact: greeting-service.jar"], fill=(239, 246, 255))
    box(d, (460, 52, 700, 190), "3. stage: docker", ["build-and-push-docker", "docker build app/", "docker push :CI_COMMIT_SHORT_SHA"], fill=(236, 253, 245))
    box(d, (730, 52, 980, 190), "4. stage: deploy", ["deploy-dev (develop)", "helm upgrade --install", "namespace dev"], fill=(255, 251, 235))

    arrow(d, (190, 100), (220, 100))
    arrow(d, (430, 110), (460, 110))
    arrow(d, (700, 120), (730, 120))

    box(
        d,
        (24, 230, 520, 360),
        "GitLab Runner (devtools)",
        [
            "Executor: shell (Раздел 11a)",
            "Тег: self-hosted",
            "Docker / Gradle / helm / kubectl на хосте",
            "Без docker-in-docker",
        ],
        fill=(243, 244, 246),
    )
    box(
        d,
        (560, 230, 980, 360),
        "Результат в кластере",
        [
            "Registry: DEVTOOLS_IP:5000/greeting-service:<sha>",
            "Helm release: greeting-service",
            "curl http://greeting-dev.cloud-terra.online/api/greeting",
        ],
        fill=(243, 244, 246),
    )

    d.text(
        (24, 400),
        "develop → auto deploy-dev | main → deploy-prod when: manual | feature/* → только build",
        fill=(55, 65, 81),
        font=font(11),
    )
    img.save(OUT)
    print(f"Saved: {OUT}")


if __name__ == "__main__":
    main()
