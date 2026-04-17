create schema if not exists iso_demo;

set search_path to iso_demo, public;

drop table if exists on_call_doctors cascade;
drop table if exists order_lines cascade;
drop table if exists accounts cascade;

create table accounts (
                          id            bigint generated always as identity primary key,
                          owner_name    text not null,
                          balance       numeric(14,2) not null,
                          status        text not null default 'ACTIVE',
                          updated_at    timestamptz not null default now()
);

create table order_lines (
                             id            bigint generated always as identity primary key,
                             order_no      bigint not null,
                             product_name  text not null,
                             qty           integer not null,
                             state         text not null default 'NEW',
                             created_at    timestamptz not null default now()
);

create index idx_order_lines_order_no on order_lines(order_no);
create index idx_order_lines_state on order_lines(state);

create table on_call_doctors (
                                 id            bigint generated always as identity primary key,
                                 doctor_name   text not null unique,
                                 on_call       boolean not null,
                                 updated_at    timestamptz not null default now()
);