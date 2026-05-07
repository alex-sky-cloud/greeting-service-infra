-- =============================================================
-- 01_schema.sql  —  новая схема под сущности Order / OrderItem / Product
-- =============================================================
```sql

create schema if not exists iso_demo;

-- справочник товаров
create table if not exists iso_demo.products
(
id         bigint generated always as identity primary key,
name       text           not null unique,
price      numeric(14, 2) not null,
created_at timestamptz    not null default now()
);

-- заказы
create table if not exists iso_demo.orders
(
id         bigint generated always as identity primary key,
user_id    bigint      not null,
status     text        not null default 'NEW',
created_at timestamptz not null default now()
);

create index if not exists idx_orders_user_id on iso_demo.orders (user_id);
create index if not exists idx_orders_status  on iso_demo.orders (status);

-- строки заказов — именно order_items, как в сущности OrderItem
create table if not exists iso_demo.order_items
(
id         bigint generated always as identity primary key,
order_id   bigint  not null references iso_demo.orders(id),
product_id bigint  not null references iso_demo.products(id),
qty        integer not null,
created_at timestamptz not null default now()
);

create index if not exists idx_order_items_order_id   on iso_demo.order_items (order_id);
create index if not exists idx_order_items_product_id on iso_demo.order_items (product_id);


```