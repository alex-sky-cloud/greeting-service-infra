-- V0: учебные схемы приложения.
-- Flyway (create-schemas: true) уже создаёт пустые iso_demo / shop_demo до этой миграции.
-- Здесь задаём AUTHORIZATION CURRENT_USER — этого нет в автоматическом CREATE SCHEMA Flyway.

CREATE SCHEMA IF NOT EXISTS iso_demo AUTHORIZATION CURRENT_USER;
CREATE SCHEMA IF NOT EXISTS shop_demo AUTHORIZATION CURRENT_USER;
