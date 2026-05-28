-- =============================================================
-- Учебная схема: интернет-магазин
-- Все объекты изолированы в схеме shop_demo
-- =============================================================

CREATE SCHEMA IF NOT EXISTS shop_demo;

-- Устанавливаем схему по умолчанию для текущей сессии
SET search_path TO shop_demo, public;

-- -------------------------------------------------------------

CREATE TABLE shop_demo.customer (
                                    id         BIGSERIAL    PRIMARY KEY,
                                    email      VARCHAR(255) NOT NULL UNIQUE,
                                    first_name VARCHAR(100) NOT NULL,
                                    last_name  VARCHAR(100) NOT NULL,
                                    active     BOOLEAN      NOT NULL DEFAULT TRUE,
                                    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE shop_demo.product (
                                   id          BIGSERIAL    PRIMARY KEY,
                                   sku         VARCHAR(100) NOT NULL UNIQUE,
                                   name        VARCHAR(255) NOT NULL,
                                   description TEXT,
                                   category    VARCHAR(100) NOT NULL,
                                   active      BOOLEAN      NOT NULL DEFAULT TRUE,
                                   created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- История цен: актуальная цена — строка с max(valid_from) при active = true
CREATE TABLE shop_demo.product_price (
                                         id         BIGSERIAL      PRIMARY KEY,
                                         product_id BIGINT         NOT NULL REFERENCES shop_demo.product(id),
                                         amount     NUMERIC(12, 2) NOT NULL,
                                         valid_from TIMESTAMP      NOT NULL DEFAULT NOW(),
                                         active     BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE TABLE shop_demo.order (
                                 id          BIGSERIAL   PRIMARY KEY,
                                 customer_id BIGINT      NOT NULL REFERENCES shop_demo.customer(id),
                                 status      VARCHAR(50) NOT NULL DEFAULT 'NEW',
    -- статусы: NEW | CONFIRMED | SHIPPED | DELIVERED | CANCELLED
                                 created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
                                 updated_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE shop_demo.order_item (
                                      id         BIGSERIAL      PRIMARY KEY,
                                      order_id   BIGINT         NOT NULL REFERENCES shop_demo.order(id),
                                      product_id BIGINT         NOT NULL REFERENCES shop_demo.product(id),
                                      quantity   INT            NOT NULL CHECK (quantity > 0),
                                      unit_price NUMERIC(12, 2) NOT NULL  -- цена, зафиксированная на момент заказа
);

-- Индексы
CREATE INDEX idx_product_price_product_id ON shop_demo.product_price(product_id);
CREATE INDEX idx_order_customer_id        ON shop_demo.order(customer_id);
CREATE INDEX idx_order_item_order_id      ON shop_demo.order_item(order_id);
CREATE INDEX idx_order_item_product_id    ON shop_demo.order_item(product_id);
CREATE INDEX idx_order_status             ON shop_demo.order(status);