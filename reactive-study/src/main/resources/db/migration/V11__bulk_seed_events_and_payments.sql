set search_path to reactive_study, public;

-- По одному событию и одной попытке платежа на каждый заказ (~100 000 строк в каждой таблице)
insert into order_status_events (order_id, old_status, new_status, occurred_at)
select
    o.id,
    case when o.status = 'pending' then null else 'pending' end,
    o.status,
    o.created_at + interval '1 hour'
from orders o;

insert into payment_attempts (order_id, attempt_no, amount, status, created_at)
select
    o.id,
    1::smallint,
    o.amount,
    case when random() < 0.95 then 'success' else 'failed' end,
    o.created_at + interval '30 minutes'
from orders o;

analyze order_status_events;
analyze payment_attempts;
