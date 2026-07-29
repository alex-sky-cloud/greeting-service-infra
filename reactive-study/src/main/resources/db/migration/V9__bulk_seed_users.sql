set search_path to reactive_study, public;

-- Доводим число покупателей до 5 000 (V3 уже создал id 1..3)
insert into users (email, full_name, created_at)
select
    'user' || g || '@load.reactive-study.test',
    'Load User ' || g,
    now() - (random() * interval '730 days')
from generate_series(4, 5000) as g;

create index if not exists idx_users_created_at on users (created_at);
