set search_path to reactive_study, public;

create table if not exists product_categories (
    id smallint generated always as identity primary key,
    code text not null unique,
    name text not null
);

insert into product_categories (code, name)
values
    ('electronics', 'Электроника'),
    ('home', 'Дом и быт'),
    ('sport', 'Спорт'),
    ('books', 'Книги'),
    ('fashion', 'Одежда'),
    ('food', 'Продукты'),
    ('toys', 'Игрушки'),
    ('auto', 'Авто'),
    ('health', 'Здоровье'),
    ('office', 'Офис');
