set search_path to reactor_workshop, public;

create table if not exists readings (
    id bigint generated always as identity primary key,
    meter_id bigint not null references meters (id) on delete cascade,
    kwh numeric(12, 3) not null check (kwh >= 0),
    recorded_at timestamptz not null
);

create index if not exists idx_readings_meter_id on readings (meter_id);
create index if not exists idx_readings_recorded_at on readings (recorded_at);

-- 100 000 показаний: поток, на котором limitRate имеет смысл.
insert into readings (meter_id, kwh, recorded_at)
select
    ((g - 1) % 250 + 1)::bigint,
    round((random() * 25 + 0.01)::numeric, 3),
    timestamptz '2026-01-01 00:00:00+00' + ((g - 1) * interval '7 minutes')
from generate_series(1, 100000) as g;
