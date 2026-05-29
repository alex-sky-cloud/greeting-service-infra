set search_path to iso_demo, public;

drop table if exists orders cascade;

create table orders (
                        id         bigint generated always as identity primary key,
                        user_id    bigint      not null,
                        status     text        not null,
                        created_at timestamptz not null
);