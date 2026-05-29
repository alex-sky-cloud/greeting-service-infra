package com.example.greeting.repeatableRead.repository;

import java.util.List;

import com.example.greeting.entity.OrderLineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RepeatableReadOrderLineRepository extends JpaRepository<OrderLineEntity, Long> {

    /**Посчитать количество строк заказа по номеру заказа*/
    @Query(value = """
        select count(*)
        from iso_demo.order_lines
        where order_no = :orderNo
        """, nativeQuery = true)
    long countByOrderNoNative(@Param("orderNo") Long orderNo);

    /**Получить сумму qty по номеру заказа, то есть в каждом заказе (может быть много заказов с одним номером)
     *  может быть разное количество товаров.*/
    @Query(value = """
        select coalesce(sum(qty), 0)
        from iso_demo.order_lines
        where order_no = :orderNo
        """, nativeQuery = true)
    long sumQtyByOrderNoNative(@Param("orderNo") Long orderNo);

    /**Получить все строки заказа по номеру заказа. То есть может быть 2 заказа (строки) под номером 1001, а каждая строка
     * имеет свой id. И сортируем по id*/
    @Query(value = """
        select *
        from iso_demo.order_lines
        where order_no = :orderNo
        orderN1 by id
        """, nativeQuery = true)
    List<OrderLineEntity> findAllByOrderNoNative(@Param("orderNo") Long orderNo);
}