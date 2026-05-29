-- =============================================================
-- 02_procedures(N+1).sql  —  процедуры заполнения и очистки
-- =============================================================

-- -------------------------------------------------------------
-- Очистка: удаляет все данные в правильном порядке (FK)
-- -------------------------------------------------------------
create or replace procedure iso_demo.truncate_all()
    language plpgsql
as
$$
begin
    truncate iso_demo.order_items restart identity cascade;
    truncate iso_demo.order      restart identity cascade;
    truncate iso_demo.products    restart identity cascade;
end;
$$;

-- -------------------------------------------------------------
-- Заполнение:
--   p_user_count      — сколько уникальных user_id
--   p_orders_per_user — заказов на пользователя (итого: p_user_count * p_orders_per_user)
--   p_items_per_order — строк в каждом заказе
--   p_product_count   — размер справочника товаров
--
-- Для воспроизведения примера из условия:
--   100 заказов × 5 строк = 601 запрос при одном HTTP-вызове
--   вызов: call iso_demo.seed(1, 100, 5, 50);
--
-- Для нагрузочного варианта (1000 заказов):
--   вызов: call iso_demo.seed(1, 1000, 5, 50);
-- -------------------------------------------------------------
create or replace procedure iso_demo.seed(
    p_user_count      int default 5,
    p_orders_per_user int default 20,
    p_items_per_order int default 5,
    p_product_count   int default 50
)
    language plpgsql
as
$$
declare
    v_product_ids bigint[];
    v_order_id    bigint;
    v_user_id     bigint;
    v_product_id  bigint;
    i             int;
    j             int;
    k             int;
begin
    -- 1. товары
    for i in 1..p_product_count
        loop
            insert into iso_demo.products (name, price)
            values (
                       'Product-' || i,
                       round((random() * 9900 + 100)::numeric, 2)
                   );
        end loop;

    select array_agg(id) into v_product_ids from iso_demo.products;

    -- 2. заказы и строки
    for j in 1..p_user_count
        loop
            v_user_id := j;
            for i in 1..p_orders_per_user
                loop
                    insert into iso_demo.order (user_id, status, created_at)
                    values (
                               v_user_id,
                               case (random() * 2)::int
                                   when 0 then 'NEW'
                                   when 1 then 'PROCESSING'
                                   else 'DONE'
                                   end,
                               now() - (random() * interval '90 days')
                           )
                    returning id into v_order_id;

                    for k in 1..p_items_per_order
                        loop
                            v_product_id := v_product_ids[
                                1 + (random() * (array_length(v_product_ids, 1) - 1))::int
                                ];
                            insert into iso_demo.order_items (order_id, product_id, qty)
                            values (
                                       v_order_id,
                                       v_product_id,
                                       (random() * 9 + 1)::int
                                   );
                        end loop;
                end loop;
        end loop;
end;
$$;

-- -------------------------------------------------------------
-- Переинициализация: очистка + заполнение с теми же параметрами
-- -------------------------------------------------------------
create or replace procedure iso_demo.reinit(
    p_user_count      int default 5,
    p_orders_per_user int default 20,
    p_items_per_order int default 5,
    p_product_count   int default 50
)
    language plpgsql
as
$$
begin
    call iso_demo.truncate_all();
    call iso_demo.seed(p_user_count, p_orders_per_user, p_items_per_order, p_product_count);
end;
$$;