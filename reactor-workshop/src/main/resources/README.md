# Ресурсы reactor-workshop

Домен: `meters` (250) + `readings` (100 000). Не магазин из reactive-study.

1. docker-reactor-workshop: `cp .env.example .env && docker compose up -d`
2. Если уже накатывали старые V1 users/orders — `DROP SCHEMA reactor_workshop CASCADE`, затем bootRun `local`.
3. Flyway V0–V2. Первый прогон V2 может занять около минуты.
