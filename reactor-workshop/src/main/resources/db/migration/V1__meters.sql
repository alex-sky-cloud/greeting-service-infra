set search_path to reactor_workshop, public;

create table if not exists meters (
    id bigint generated always as identity primary key,
    serial_no text not null unique,
    city text not null,
    installed_at timestamptz not null default now()
);

create index if not exists idx_meters_city on meters (city);

insert into meters (serial_no, city)
select
    'M-' || lpad(g::text, 5, '0'),
    (array['Minsk', 'Grodno', 'Brest', 'Gomel', 'Vitebsk'])[((g - 1) % 5) + 1]
from generate_series(1, 250) as g;
