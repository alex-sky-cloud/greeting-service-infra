# Часть 2M — CI/CD вручную (без Terraform)

Word-гайд по тому же контуру, что «Часть 2» (CI/CD и развёртывание), но:

- любой VPS-провайдер;
- **без Terraform** и без managed Ingress облака;
- только **GitLab** (Bitbucket не используется);
- **Traefik** — отдельный кластер на своих VPS;
- **S3** через MinIO;
- команды для **Windows / macOS / Linux**.

## Файл результата

`greeting-service-guide - Часть 2M - CI-CD вручную (GitLab k3s Traefik).docx`

## Пересборка

```bash

cd /d/Project_infra/greeting-service-infra/docs/guide-deploy-manual
python render_diagrams.py
python gen_chast2_manual_docx.py
```

На Windows (PowerShell):

```powershell

Set-Location D:\Project_infra\greeting-service-infra\docs\guide-deploy-manual
python render_diagrams.py
python gen_chast2_manual_docx.py
```

## Схемы

PNG в `images/` рисуются **крупным шрифтом** (тело ~40–48 px на холсте ~1800 px), чтобы в Word на ширине ~6.7\" текст читался без лупы.

| Файл | Смысл |
|---|---|
| `tech-map.png` | Экскурсия: аналогии по технологиям |
| `architecture-manual.png` | Обзор архитектуры сверху вниз |
| `architecture-traffic.png` | Путь HTTP-запроса |
| `cicd-manual.png` | Pipeline сверху вниз |

В Word у каждой схемы есть сопроводительный разбор (не только картинка).

