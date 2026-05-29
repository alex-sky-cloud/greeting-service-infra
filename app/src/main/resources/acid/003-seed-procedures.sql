create schema if not exists iso_demo;

set search_path to iso_demo, public;

create or replace procedure reset_demo_data()
    language plpgsql
as $$
begin
    truncate table on_call_summary;
    truncate table on_call_doctors restart identity;
    truncate table order_lines restart identity;
    truncate table accounts restart identity;

    insert into accounts (owner_name, balance, status)
    values
        ('Alice', 1000.00, 'ACTIVE'),
        ('Bob',   1000.00, 'ACTIVE'),
        ('Carol', 1000.00, 'ACTIVE');

    insert into on_call_doctors (doctor_name, on_call)
    values
        ('Dr. Alice', true),
        ('Dr. Bob',   true);

    insert into on_call_summary (id, on_call_count, updated_at)
    values (1, 2, now());

    insert into order_lines (order_no, product_name, qty, state)
    values
        (1001, 'Keyboard', 1, 'NEW'),
        (1001, 'Mouse',    2, 'NEW'),
        (1002, 'Monitor',  1, 'NEW');
end;
$$;

create or replace procedure seed_order_lines(
    p_order_count integer,
    p_lines_per_order integer
)
    language plpgsql
as $$
begin
    insert into order_lines (order_no, product_name, qty, state)
    select
        order_no,
        'Product-' || line_no,
        1 + (random() * 4)::int,
        case
            when random() < 0.7 then 'NEW'
            when random() < 0.9 then 'PROCESSING'
            else 'DONE'
            end
    from generate_series(1, p_order_count) as order_no
             cross join generate_series(1, p_lines_per_order) as line_no;
end;
$$;

create or replace procedure seed_accounts(p_count integer)
    language plpgsql
as $$
begin
    insert into accounts (owner_name, balance, status)
    select
        'User-' || gs,
        (100 + (random() * 9000))::numeric(14,2),
        case
            when random() < 0.85 then 'ACTIVE'
            else 'BLOCKED'
            end
    from generate_series(1, p_count) as gs;
end;
$$;

create or replace procedure seed_on_call_doctors(p_count integer)
    language plpgsql
as $$
declare
    v_on_call_count integer;
begin
    truncate table on_call_summary;
    truncate table on_call_doctors restart identity;

    insert into on_call_doctors (doctor_name, on_call)
    select
        'Doctor-' || gs,
        gs <= greatest(1, least(2, p_count))
    from generate_series(1, p_count) as gs;

    select count(*)
    into v_on_call_count
    from on_call_doctors
    where on_call = true;

    insert into on_call_summary (id, on_call_count, updated_at)
    values (1, v_on_call_count, now());
end;
$$;