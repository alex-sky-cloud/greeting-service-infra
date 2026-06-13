-- =============================================================================
-- Очистка удалённой БД (managed PostgreSQL в Timeweb, база greeting_db).
--
-- Пользователь greeting_user не имеет прав на schema public — только DROP
-- учебных схем. flyway_schema_history лежит в iso_demo (см. application.yml).
--
-- Запуск из Git Bash (корень репозитория):
--   bash scripts/dev-db-connection/08-clean-remote-database.sh
--
-- Или вручную через туннель:
--   bash scripts/dev-db-connection/03-start-tunnel.sh
--   psql -h 127.0.0.1 -p 15432 -U greeting_user -d greeting_db \
--     -v ON_ERROR_STOP=1 -f app/src/main/resources/db/clean-database-remote.sql
-- =============================================================================

DROP SCHEMA IF EXISTS iso_demo CASCADE;
DROP SCHEMA IF EXISTS shop_demo CASCADE;
