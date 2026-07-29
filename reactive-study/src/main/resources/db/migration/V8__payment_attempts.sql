set search_path to reactive_study, public;

-- Очередь платежей: concatMap — строго по порядку attempt_no
create table if not exists payment_attempts (
    id bigint generated always as identity primary key,
    order_id bigint not null references orders (id) on delete cascade,
    attempt_no smallint not null check (attempt_no >= 1),
    amount numeric(12, 2) not null check (amount >= 0),
    status text not null default 'pending'
        check (status in ('pending', 'success', 'failed')),
    created_at timestamptz not null default now(),
    unique (order_id, attempt_no)
);

create index if not exists idx_payment_attempts_status_created on payment_attempts (status, created_at);
create index if not exists idx_payment_attempts_order_id on payment_attempts (order_id);
