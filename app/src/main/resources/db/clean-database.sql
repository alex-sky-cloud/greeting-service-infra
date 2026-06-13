-- =============================================================================
-- Полная очистка БД приложения greeting-service.
--
-- Удаляет схемы iso_demo (включая flyway_schema_history) и shop_demo.
-- Схема public и системные объекты PostgreSQL не удаляются.
--
-- Локальный Docker:
--   docker exec -i local-postgres psql -U app -d app -v ON_ERROR_STOP=1 \
--     < app/src/main/resources/db/clean-database.sql
--
-- Удалённая managed PostgreSQL (Timeweb, greeting_db через SSH-туннель):
--   bash scripts/dev-db-connection/08-clean-remote-database.sh
--   (использует db/clean-database-remote.sql — без процедуры в public)
-- =============================================================================

CREATE OR REPLACE PROCEDURE public.clean_greeting_database()
    LANGUAGE plpgsql
AS $$
BEGIN
    DROP SCHEMA IF EXISTS iso_demo CASCADE;
    DROP SCHEMA IF EXISTS shop_demo CASCADE;

    DROP TABLE IF EXISTS public.test_table CASCADE;
    DROP TABLE IF EXISTS public.doctor CASCADE;
    DROP TABLE IF EXISTS public.flyway_schema_history CASCADE;

    RAISE NOTICE 'clean_greeting_database: iso_demo (включая flyway_schema_history), shop_demo удалены.';
END;
$$;

CALL public.clean_greeting_database();

DROP PROCEDURE IF EXISTS public.clean_greeting_database();
