package com.example.greeting.projection.repository;

import com.example.greeting.projection.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для работы с заказами.
 *
 * <p>Основной репозиторий для демонстрации всех видов проекций:
 * интерфейсных, DTO, динамических и скалярных. Методы добавляются
 * по мере разбора каждого вида.</p>
 *
 * @implNote Таблица {@code shop_demo.order} содержит зарезервированное
 *           слово SQL. Hibernate экранирует имя таблицы автоматически
 *           на основе {@code @Table(name = "order")} в сущности.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
}
