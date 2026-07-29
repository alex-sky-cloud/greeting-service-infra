set search_path to reactive_study, public;

-- Расширение orders под домен «Reactive Shop»
alter table orders
    add column if not exists product_id int references products (id),
    add column if not exists status text not null default 'pending'
        check (status in ('pending', 'processing', 'shipped', 'delivered', 'cancelled')),
    add column if not exists updated_at timestamptz not null default now();

-- Привязка демо-заказов V3 к первым товарам
update orders set product_id = 1 where id = 1 and product_id is null;
update orders set product_id = 2 where id = 2 and product_id is null;
update orders set product_id = 3 where id = 3 and product_id is null;
update orders set product_id = 4 where id = 4 and product_id is null;

update orders set status = 'delivered' where status = 'pending' and id <= 4;

create index if not exists idx_orders_status_created_at on orders (status, created_at);
create index if not exists idx_orders_created_at on orders (created_at);
create index if not exists idx_orders_product_id on orders (product_id);
