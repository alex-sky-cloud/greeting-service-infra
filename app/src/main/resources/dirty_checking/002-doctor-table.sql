set search_path to iso_demo, public;

drop table if exists doctor cascade;

create table doctor (
                        id bigint generated always as identity primary key,
                        first_name text not null,
                        last_name text not null,
                        salary numeric(19,2) not null
);