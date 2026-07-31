# -*- coding: utf-8 -*-
"""Find and download Helm diagram from Incidenta article."""
from __future__ import annotations

import re
import urllib.request
from pathlib import Path

URL = "https://app.incidenta.tech/article/tools-helm/"
OUT_DIR = Path(r"D:/Project_infra/greeting-service-infra/docs/images/helm")
OUT_FILE = OUT_DIR / "helm-kubernetes-ecosystem.png"

html = urllib.request.urlopen(URL).read().decode("utf-8", "replace")
urls = re.findall(r'(?:src|href)=["\']([^"\']+)["\']', html)
image_urls = []
for u in urls:
    if any(ext in u.lower() for ext in (".png", ".jpg", ".jpeg", ".webp", ".svg")):
        image_urls.append(u)
    elif "image" in u.lower() or "upload" in u.lower() or "static" in u.lower():
        image_urls.append(u)

print("All candidate URLs:")
for u in sorted(set(urls)):
    print(u)

print("\nImage-like URLs:")
for u in sorted(set(image_urls)):
    print(u)

IMAGE_URL = "https://app.incidenta.tech/static/images/articles/0031-tools-helm.png"
OUT_DIR.mkdir(parents=True, exist_ok=True)
urllib.request.urlretrieve(IMAGE_URL, OUT_FILE)
print(f"\nDownloaded: {OUT_FILE} ({OUT_FILE.stat().st_size} bytes)")
