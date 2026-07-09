-- =============================================================================
-- Очистка БД reactive_demo на том же managed PostgreSQL, что и greeting_db.
--
--   bash scripts/dev-db-connection/03-start-tunnel.sh
--   psql -h 127.0.0.1 -p 15432 -U greeting_user -d reactive_demo \
--     -v ON_ERROR_STOP=1 -f reactive-demo/src/main/resources/db/clean-database-remote.sql
-- =============================================================================

DROP SCHEMA IF EXISTS reactive_demo CASCADE;
