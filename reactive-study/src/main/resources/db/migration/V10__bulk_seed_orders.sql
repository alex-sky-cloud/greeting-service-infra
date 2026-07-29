set search_path to reactive_study, public;

-- Очищаем демо-заказы V3 и наполняем 100 000 заказов
truncate table payment_attempts, order_status_events, orders restart identity cascade;

insert into orders (user_id, product_id, product_name, amount, status, created_at, updated_at)
select
    (1 + floor(random() * 5000))::bigint,
    (1 + floor(random() * 200))::int,
    'Bulk order #' || g,
    round((random() * 2000 + 5)::numeric, 2),
    (array['pending', 'processing', 'shipped', 'delivered', 'cancelled'])[1 + floor(random() * 5)::int],
    now() - (random() * interval '365 days'),
    now() - (random() * interval '30 days')
from generate_series(1, 100000) as g;

analyze orders;
