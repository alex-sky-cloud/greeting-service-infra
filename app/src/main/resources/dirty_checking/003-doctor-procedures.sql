set search_path to iso_demo, public;

create or replace procedure reset_doctor_demo_data()
    language plpgsql
as $$
begin
    truncate table doctor restart identity;

    insert into doctor (first_name, last_name, salary)
    values
        ('Ivan', 'Petrov', 3500.00),
        ('Petr', 'Sidorov', 4200.00),
        ('Anna', 'Ivanova', 3900.00);
end;
$$;

create or replace procedure seed_doctors(p_count integer)
    language plpgsql
as $$
begin
    truncate table doctor restart identity;

    insert into doctor (first_name, last_name, salary)
    select
        'DoctorFirstName-' || gs,
        'DoctorLastName-' || gs,
        (2500 + (random() * 7500))::numeric(19,2)
    from generate_series(1, p_count) as gs;
end;
$$;