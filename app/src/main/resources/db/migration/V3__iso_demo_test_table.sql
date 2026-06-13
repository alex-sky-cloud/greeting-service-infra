set search_path to iso_demo, public;

create table if not exists test_table
(
    id bigserial primary key,
    name text not null,
    created_at timestamptz not null default now()
);

insert into test_table(name)
values ('hello');
