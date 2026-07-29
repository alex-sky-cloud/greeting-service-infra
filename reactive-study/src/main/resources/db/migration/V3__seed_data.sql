set search_path to reactive_study, public;

insert into users (email, full_name)
values
    ('ann@example.com', 'Ann Smith'),
    ('bob@example.com', 'Bob Jones'),
    ('carol@example.com', 'Carol Lee');

insert into orders (user_id, product_name, amount)
values
    (1, 'Laptop', 999.99),
    (1, 'Mouse', 29.99),
    (2, 'Keyboard', 79.00),
    (3, 'Monitor', 349.50);
