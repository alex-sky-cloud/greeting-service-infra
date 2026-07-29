set search_path to reactive_study, public;

create table if not exists products (
    id int generated always as identity primary key,
    category_id smallint not null references product_categories (id),
    sku text not null unique,
    name text not null,
    price numeric(12, 2) not null check (price >= 0),
    created_at timestamptz not null default now()
);

create index if not exists idx_products_category_id on products (category_id);

-- 200 товаров для каталога (generate_series)
insert into products (category_id, sku, name, price)
select
    ((g - 1) % 10 + 1)::smallint,
    'SKU-' || lpad(g::text, 5, '0'),
    'Product ' || g,
    round((random() * 5000 + 9.99)::numeric, 2)
from generate_series(1, 200) as g;
