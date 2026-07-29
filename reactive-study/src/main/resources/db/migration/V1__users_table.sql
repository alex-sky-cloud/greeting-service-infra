set search_path to reactive_study, public;

create table if not exists users (
    id bigint generated always as identity primary key,
    email text not null unique,
    full_name text not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_users_email on users (email);
