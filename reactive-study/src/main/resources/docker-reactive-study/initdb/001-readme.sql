-- Init при первом старте контейнера (пустой volume).
-- База reactive_study уже создаётся через POSTGRES_DB в docker-compose.
-- Схему и таблицы накатывает Flyway при старте ReactiveStudyApplication.

SELECT 1;
