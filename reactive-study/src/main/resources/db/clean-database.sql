-- =============================================================================
-- Полная очистка локальной БД reactive_study (контейнер reactive-study-postgres).
--
--   docker exec -i reactive-study-postgres psql -U app -d reactive_study -v ON_ERROR_STOP=1 \
--     < reactive-study/src/main/resources/db/clean-database.sql
-- =============================================================================

DROP SCHEMA IF EXISTS reactive_study CASCADE;
