create table if not exists doctor (
                                      id bigserial primary key,
                                      first_name varchar(100) not null,
                                      last_name varchar(100) not null,
                                      salary numeric(19, 2) not null
);