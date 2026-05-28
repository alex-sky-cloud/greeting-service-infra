-- =============================================================
-- 03_usage.sql  —  примеры вызовов
-- =============================================================

-- Воспроизвести условие задачи: userId=1, 100 заказов × 5 строк = 601 запрос
call iso_demo.reinit(
        p_user_count      => 1,
        p_orders_per_user => 100,
        p_items_per_order => 5,
        p_product_count   => 50
     );

-- Нагрузочный вариант: 1000 заказов × 5 строк = 6001 запрос
call iso_demo.reinit(
        p_user_count      => 1,
        p_orders_per_user => 1000,
        p_items_per_order => 5,
        p_product_count   => 50
     );

-- Только очистить
call iso_demo.truncate_all();

-- Проверить объём
select
    (select count(*) from iso_demo.products)    as products,
    (select count(*) from iso_demo.orders)       as orders,
    (select count(*) from iso_demo.order_items)  as order_items;