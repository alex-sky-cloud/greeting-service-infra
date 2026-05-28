create or replace procedure fill_doctors(p_count integer)
    language plpgsql
as $$
declare
    i integer;
begin
    for i in 1..p_count loop
            insert into doctor(first_name, last_name, salary)
            values (
                       'Doctor_' || i,
                       'Lastname_' || i,
                       round((1000 + random() * 1000)::numeric, 2)
                   );
        end loop;
end;
$$;

call fill_doctors(10000);