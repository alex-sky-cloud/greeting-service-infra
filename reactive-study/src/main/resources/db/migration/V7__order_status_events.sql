set search_path to reactive_study, public;

-- Журнал смены статусов: поток событий для merge / Flux-лабораторий
create table if not exists order_status_events (
    id bigint generated always as identity primary key,
    order_id bigint not null references orders (id) on delete cascade,
    old_status text,
    new_status text not null
        check (new_status in ('pending', 'processing', 'shipped', 'delivered', 'cancelled')),
    occurred_at timestamptz not null default now()
);

create index if not exists idx_order_status_events_order_id on order_status_events (order_id);
create index if not exists idx_order_status_events_occurred_at on order_status_events (occurred_at);
