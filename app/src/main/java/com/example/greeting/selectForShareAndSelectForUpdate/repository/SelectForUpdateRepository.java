package com.example.greeting.selectForShareAndSelectForUpdate.repository;

import com.example.greeting.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
public interface SelectForUpdateRepository extends JpaRepository<AccountEntity, Long> {

    /**
     * Возвращает текущий баланс счёта с блокировкой SELECT ... FOR UPDATE.
     *
     * <p>Это ключевой момент защиты от lost update:</p>
     * <ul>
     *   <li>если другая транзакция уже держит FOR UPDATE на этой строке —
     *       данный SELECT заблокируется и будет ждать;</li>
     *   <li>после снятия блокировки прочитает <strong>актуальный</strong>
     *       (уже зафиксированный) баланс, а не устаревший snapshot.</li>
     * </ul>
     *
     * <pre>
     * SQL:
     *   SELECT balance
     *   FROM iso_demo.accounts
     *   WHERE id = :id
     *   FOR UPDATE
     * </pre>
     *
     * @param id идентификатор счёта
     * @return актуальный баланс после снятия блокировки
     */
    @Query(value = """
            select balance
            from iso_demo.accounts
            where id = :id
            for update
            """, nativeQuery = true)
    BigDecimal findBalanceByIdForUpdate(@Param("id") Long id);

    /**
     * Обновляет баланс счёта на новое значение.
     *
     * <p>Вызывается строго после {@link #findBalanceByIdForUpdate(Long)},
     * поэтому к моменту UPDATE строка уже заблокирована и
     * newBalance рассчитан от актуального значения.</p>
     *
     * <pre>
     * SQL:
     *   UPDATE iso_demo.accounts
     *   SET balance    = :newBalance,
     *       updated_at = now()
     *   WHERE id = :id
     * </pre>
     *
     * @param id         идентификатор счёта
     * @param newBalance новое значение баланса
     */
    @Modifying
    @Query(value = """
            update iso_demo.accounts
            set balance    = :newBalance,
                updated_at = now()
            where id = :id
            """, nativeQuery = true)
    void updateBalance(@Param("id") Long id,
                       @Param("newBalance") BigDecimal newBalance);
}
