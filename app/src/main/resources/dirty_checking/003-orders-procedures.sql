set search_path to iso_demo, public;

create or replace procedure reset_orders_demo_data()
    language plpgsql
as $$
begin
    truncate table orders restart identity;

    insert into orders (user_id, status, created_at)
    values
        (101, 'NEW',       now() - interval '3 day'),
        (102, 'PAID',      now() - interval '2 day'),
        (103, 'CANCELLED', now() - interval '1 day');
end;
$$;

create or replace procedure seed_orders(p_count integer)
    language plpgsql
as $$
begin
    truncate table orders restart identity;

    insert into orders (user_id, status, created_at)
    select
        1000 + gs                                           as user_id,
        case
            when random() < 0.50 then 'NEW'
            when random() < 0.80 then 'PAID'
            when random() < 0.95 then 'SHIPPED'
            else 'CANCELLED'
            end                                                 as status,
        now() - ((random() * 30)::int || ' day')::interval as created_at
    from generate_series(1, p_count) as gs;
end;
$$;