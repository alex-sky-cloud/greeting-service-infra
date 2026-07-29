set search_path to reactive_study, public;

create table if not exists orders (
    id bigint generated always as identity primary key,
    user_id bigint not null references users (id) on delete cascade,
    product_name text not null,
    amount numeric(12, 2) not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_orders_user_id on orders (user_id);
