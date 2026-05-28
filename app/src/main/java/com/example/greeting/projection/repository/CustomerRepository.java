package com.example.greeting.projection.repository;

import com.example.greeting.projection.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для работы с покупателями.
 *
 * <p>Базовые CRUD-операции унаследованы от {@link JpaRepository}.
 * Методы с проекциями добавляются по мере разбора каждого вида проекции.</p>
 *
 * @implNote Spring Data при старте приложения генерирует реализацию этого
 *           интерфейса и регистрирует её в контексте как бин.
 *           Поверх этого бина Spring AOP создаёт прокси-объект.
 *           <p>Когда {@code ShopService} вызывает
 *           {@code customerRepository.findAll()}, вызов идёт в прокси,
 *           а не напрямую в реализацию. Прокси проверяет, нужно ли
 *           открыть транзакцию, применяет другие перехватчики и только
 *           потом передаёт вызов реальному методу репозитория.
 *           Именно поэтому {@code @Transactional} на методах репозитория
 *           работает корректно при вызове из сервиса.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
