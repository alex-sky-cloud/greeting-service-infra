-- =============================================================
-- Процедура: полная очистка и переинициализация
-- Вызов: CALL shop_demo.shop_reset();
--
-- Удаляет все данные в правильном порядке (с учётом FK),
-- сбрасывает sequences, затем вызывает shop_seed_data().
-- =============================================================

CREATE OR REPLACE PROCEDURE shop_demo.shop_reset()
    LANGUAGE plpgsql AS $$
BEGIN
    TRUNCATE TABLE shop_demo.order_item    RESTART IDENTITY CASCADE;
    TRUNCATE TABLE shop_demo.order         RESTART IDENTITY CASCADE;
    TRUNCATE TABLE shop_demo.product_price RESTART IDENTITY CASCADE;
    TRUNCATE TABLE shop_demo.product       RESTART IDENTITY CASCADE;
    TRUNCATE TABLE shop_demo.customer      RESTART IDENTITY CASCADE;

    RAISE NOTICE 'shop_reset: таблицы очищены, sequences сброшены.';

    CALL shop_demo.shop_seed_data();

    RAISE NOTICE 'shop_reset: среда переинициализирована.';
END;
$$;