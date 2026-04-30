package com.example.greeting.serializable.repository;

import java.util.List;

import com.example.greeting.entity.OrderLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Репозиторий для демонстрации поведения phantom read
 * на уровне изоляции SERIALIZABLE.
 */
public interface SerializableOrderLineRepository extends JpaRepository<OrderLineEntity, Long> {

    /**
     * Возвращает все строки заказа по номеру заказа.
     *
     * SQL:
     * <pre>{@code
     * select id, order_no, product_name, qty, state, created_at
     * from iso_demo.order_lines
     * where order_no = :orderNo
     * order by id
     * }</pre>
     */
    @Query(value = """
    select id, order_no, product_name, qty, state, created_at
    from iso_demo.order_lines
    where order_no = :orderNo
    order by id
    """, nativeQuery = true)
    List<OrderLineEntity> findAllByOrderNoNative(@Param("orderNo") Long orderNo);
    /**
     * Подсчитывает количество строк заказа по номеру заказа.
     *
     * <pre>
     *   {@code
     *     SQL:
     * select count(*)
     * from iso_demo.order_lines
     * where order_no = :orderNo
     * }
     * </pre>
     */
    @Query(value = """
        select count(*)
        from iso_demo.order_lines
        where order_no = :orderNo
        """, nativeQuery = true)
    long countByOrderNoNative(@Param("orderNo") Long orderNo);

    /**
     * Суммирует qty по номеру заказа.
     *
     * <pre>
     *  {@code    SQL:
     * select coalesce(sum(qty), 0)
     * from iso_demo.order_lines
     * where order_no = :orderNo
     * }
     * </pre>
     */
    @Query(value = """
        select coalesce(sum(qty), 0)
        from iso_demo.order_lines
        where order_no = :orderNo
        """, nativeQuery = true)
    long sumQtyByOrderNoNative(@Param("orderNo") Long orderNo);

    /**
     * Вставляет новую строку заказа.
     *
     * <pre>
     *     {@code
     * SQL:
     * insert into iso_demo.order_lines (order_no, product_name, qty, state)
     * values (:orderNo, :productName, :qty, :state)
     * }
     * </pre>
     */
    @Modifying
    @Query(value = """
        insert into iso_demo.order_lines (order_no, product_name, qty, state)
        values (:orderNo, :productName, :qty, :state)
        """, nativeQuery = true)
    void insertNative(
            @Param("orderNo") Long orderNo,
            @Param("productName") String productName,
            @Param("qty") Integer qty,
            @Param("state") String state
    );

    @Modifying
    @Query(value = """
    delete from iso_demo.order_lines
    where order_no = :orderNo
      and product_name = :productName
    """, nativeQuery = true)
    void deleteByOrderNoAndProductNameNative(
            @Param("orderNo") Long orderNo,
            @Param("productName") String productName
    );
}
