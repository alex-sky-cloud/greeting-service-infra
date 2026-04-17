package com.example.greeting.readcommited.repository;

import java.util.List;

import com.example.greeting.entity.OrderLineEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrderLineRepository extends JpaRepository<OrderLineEntity, Long> {

    /**Поиск заказов по списку номеров заказов*/
    @Query(value = """
            select id, order_no, product_name, qty, state, created_at
            from iso_demo.order_lines
            where order_no = :orderNo
            order by id
            """, nativeQuery = true)
    List<OrderLineEntity> findAllByOrderNoNative(@Param("orderNo") Long orderNo);

    /**Подсчитать количество номеров заказов по списку номеров заказов*/
    @Query(value = """
            select count(*)
            from iso_demo.order_lines
            where order_no = :orderNo
            """, nativeQuery = true)
    long countByOrderNoNative(@Param("orderNo") Long orderNo);

    /**
     * Возвращает общее количество штук (qty) по всем строкам заказа с заданным номером.
     *
     * Пример SQL-запроса:
     * <pre>
     * SELECT COALESCE(SUM(qty), 0)
     * FROM iso_demo.order_lines
     * WHERE order_no = :orderNo;
     * </pre>
     *
     * COALESCE(expr1, expr2) — возвращает первый аргумент, который НЕ равен NULL.
     * <li>В данном случае:</li>
     *   <- SUM(qty) вернёт NULL, если для указанного order_no нет ни одной строки;
     * - COALESCE(SUM(qty), 0) заменяет этот NULL на 0.
     *
     * Поэтому метод всегда возвращает число (0, если строк нет),
     * и в Java-коде не нужно отдельно обрабатывать NULL из SUM().
     */
    @Query(value = """
            select coalesce(sum(qty), 0)
            from iso_demo.order_lines
            where order_no = :orderNo
            """, nativeQuery = true)
    long sumQtyByOrderNoNative(@Param("orderNo") Long orderNo);

    /**Создать новый заказ.*/
    @Modifying
    @Query(value = """
            insert into iso_demo.order_lines (order_no, product_name, qty, state)
            values (:orderNo, :productName, :qty, :state)
            """, nativeQuery = true)
    int insertNative(@Param("orderNo") Long orderNo,
                     @Param("productName") String productName,
                     @Param("qty") Integer qty,
                     @Param("state") String state);

    /**Удалить заказ по идентификатору*/
    @Modifying
    @Query(value = """
            delete from iso_demo.order_lines
            where id = :id
            """, nativeQuery = true)
    int deleteByIdNative(@Param("id") Long id);
}
