package com.example.greeting.projection.repository;

import com.example.greeting.n_plus_1.entity.OrderItemN1;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для работы со строками заказов.
 *
 * <p>Используется при демонстрации проекций с агрегацией по товарам
 * и скалярных проекций (сумма позиций, общее количество).</p>
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemN1, Long> {
}
