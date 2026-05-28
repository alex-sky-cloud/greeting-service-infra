package com.example.greeting.projection.repository;

import com.example.greeting.projection.entity.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для работы с историей цен товаров.
 *
 * <p>Используется при демонстрации проекций с JOIN-запросами
 * к актуальной цене и агрегацией по ценовым периодам.</p>
 */
@Repository
public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {
}
