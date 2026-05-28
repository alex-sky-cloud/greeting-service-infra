package com.example.greeting.projection.repository;

import com.example.greeting.projection.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для работы с товарами.
 *
 * <p>Содержит базовые операции над {@link Product}.
 * Методы с проекциями (каталог, цена, агрегаты по категориям)
 * добавляются в рамках под-тем по проекциям.</p>
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
}
