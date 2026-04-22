package com.example.greeting.selectForShareAndSelectForUpdate.repository;

import com.example.greeting.entity.OrderLineEntity;
import com.example.greeting.selectForShareAndSelectForUpdate.service.OrderLineQtyDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SelectForShareRepository extends JpaRepository<OrderLineEntity, Long> {

    /**
     * Возвращает строки заказа с блокировкой SELECT ... FOR SHARE.
     *
     * <p>Гарантии:</p>
     * <ul>
     *   <li>другие транзакции не могут делать UPDATE/DELETE по этим строкам,
     *       пока текущая транзакция не завершится;</li>
     *   <li>другие SELECT ... FOR SHARE / FOR KEY SHARE по тем же строкам допустимы —
     *       shared‑lock совместим с shared‑lock.</li>
     * </ul>
     *
     * <pre>
     * SQL:
     *   SELECT *
     *   FROM iso_demo.order_lines
     *   WHERE order_no = :orderNo
     *   FOR SHARE
     * </pre>
     *
     * @param orderNo номер заказа
     * @return список строк заказа под shared‑блокировкой
     */
    @Query(value = """
            select *
            from iso_demo.order_lines
            where order_no = :orderNo
            for share
            """, nativeQuery = true)
    List<OrderLineEntity> findByOrderNoForShare(@Param("orderNo") Long orderNo);

    List<OrderLineEntity> findByOrderNoOrderByOrderNoAsc(Long orderNo);


    @Query(value = """
            select id, qty
            from iso_demo.order_lines
            where order_no = :orderNo
            for share
            """, nativeQuery = true)
    List<OrderLineQtyDto> findByOrderNoForShareDto(@Param("orderNo") Long orderNo);
}
