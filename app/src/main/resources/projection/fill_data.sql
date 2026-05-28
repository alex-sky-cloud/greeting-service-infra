-- =============================================================
-- Процедура: заполнение учебными данными
-- Вызов: CALL shop_demo.shop_seed_data();
-- =============================================================

CREATE OR REPLACE PROCEDURE shop_demo.shop_seed_data()
    LANGUAGE plpgsql AS $$
DECLARE
    v_customer_id  BIGINT;
    v_product_id   BIGINT;
    v_order_id     BIGINT;
    v_price_old    NUMERIC(12,2);
    v_price_new    NUMERIC(12,2);
    v_statuses     TEXT[]  := ARRAY['NEW','CONFIRMED','SHIPPED','DELIVERED','CANCELLED'];
    v_categories   TEXT[]  := ARRAY['Electronics','Clothing','Books'];
    v_items_count  INT;
    i              INT;
    j              INT;
BEGIN
    -- --------------------------------------------------------
    -- 1. Покупатели (10 штук)
    -- --------------------------------------------------------
    FOR i IN 1..10 LOOP
            INSERT INTO shop_demo.customer (email, first_name, last_name, active, created_at)
            VALUES (
                       'customer' || i || '@shop.example',
                       'FirstName' || i,
                       'LastName'  || i,
                       (i % 5 != 0),
                       NOW() - (i || ' days')::INTERVAL
                   );
        END LOOP;

    -- --------------------------------------------------------
    -- 2. Товары (30 штук, 3 категории по 10)
    -- --------------------------------------------------------
    FOR i IN 1..30 LOOP
            INSERT INTO shop_demo.product (sku, name, description, category, active, created_at)
            VALUES (
                       'SKU-' || LPAD(i::TEXT, 4, '0'),
                       v_categories[((i-1) % 3) + 1] || ' Product ' || i,
                       'Description for product ' || i,
                       v_categories[((i-1) % 3) + 1],
                       (i % 7 != 0),
                       NOW() - (i * 2 || ' days')::INTERVAL
                   )
            RETURNING id INTO v_product_id;

            v_price_old := (50 + (i * 3))::NUMERIC;
            v_price_new := v_price_old * 1.15;

            INSERT INTO shop_demo.product_price (product_id, amount, valid_from, active)
            VALUES (v_product_id, v_price_old, NOW() - '30 days'::INTERVAL, FALSE);

            INSERT INTO shop_demo.product_price (product_id, amount, valid_from, active)
            VALUES (v_product_id, v_price_new, NOW() - '1 day'::INTERVAL,  TRUE);
        END LOOP;

    -- --------------------------------------------------------
    -- 3. Заказы (50 штук) и строки заказов
    -- --------------------------------------------------------
    FOR i IN 1..50 LOOP
            SELECT id INTO v_customer_id
            FROM shop_demo.customer
            ORDER BY id
            LIMIT 1 OFFSET ((i - 1) % 10);

            INSERT INTO shop_demo.order (customer_id, status, created_at, updated_at)
            VALUES (
                       v_customer_id,
                       v_statuses[((i-1) % 5) + 1],
                       NOW() - (i || ' hours')::INTERVAL,
                       NOW() - (i || ' minutes')::INTERVAL
                   )
            RETURNING id INTO v_order_id;

            v_items_count := (i % 5) + 1;

            FOR j IN 1..v_items_count LOOP
                    SELECT p.id, pp.amount
                    INTO v_product_id, v_price_new
                    FROM shop_demo.product p
                             JOIN shop_demo.product_price pp
                                  ON pp.product_id = p.id AND pp.active = TRUE
                    ORDER BY p.id
                    LIMIT 1 OFFSET ((i + j) % 30);

                    INSERT INTO shop_demo.order_item (order_id, product_id, quantity, unit_price)
                    VALUES (
                               v_order_id,
                               v_product_id,
                               (j % 3) + 1,
                               v_price_new
                           );
                END LOOP;
        END LOOP;

    RAISE NOTICE 'shop_seed_data: данные успешно загружены.';
END;
$$;