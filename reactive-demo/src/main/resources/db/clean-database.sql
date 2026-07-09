-- =============================================================================
-- Полная очистка локальной БД reactive_demo (тот же PostgreSQL, что и app).
--
--   docker exec -i local-postgres psql -U app -d reactive_demo -v ON_ERROR_STOP=1 \
--     < reactive-demo/src/main/resources/db/clean-database.sql
-- =============================================================================

DROP SCHEMA IF EXISTS reactive_demo CASCADE;
