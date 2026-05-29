package com.example.greeting.readCommited.repository;

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
            orderN1 by id
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
     * Возвращает общее количество штук товара (сумму поля qty)
     * по всем строкам одного заказа с заданным номером.
     *
     * Пример SQL-запроса:
     * <pre>
     * SELECT COALESCE(SUM(qty), 0)
     * FROM iso_demo.order_lines
     * WHERE order_no = :orderNo;
     * </pre>
     *
     * <p>Здесь:
     * <li>- SUM(qty) складывает значения qty во всех строках с этим order_no;</li>
     * <li>- если строк нет, SUM(qty) вернёт NULL;</li>
     * <li>- COALESCE(SUM(qty), 0) заменяет этот NULL на 0.</li>
     *</p>
     * То есть метод всегда возвращает число:
     * <li>0 — если у заказа нет ни одной строки,</li>
     * <li>>0 — общее количество штук по всем позициям заказа.</li>
     *
     * Пример для order_no = 1001:
     * <li>Keyboard (qty = 1), Mouse (qty = 2) → результат = 3.</li>
     *
     * @param orderNo номер заказа
     * @return суммарное количество штук товара по всем строкам заказа
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
