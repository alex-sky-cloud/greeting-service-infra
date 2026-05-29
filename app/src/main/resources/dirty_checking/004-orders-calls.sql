set search_path to iso_demo, public;

call reset_orders_demo_data();

-- или для массового наполнения:
-- call seed_orders(100);