package com.example.greeting.readCommited.repository;

import java.math.BigDecimal;

import com.example.greeting.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    /**Найти account по идентификатору*/
    @Query(value = """
            select id, owner_name, balance, status, updated_at
            from iso_demo.accounts
            where id = :id
            """, nativeQuery = true)
    AccountEntity findByIdNative(@Param("id") Long id);

    /**Найти `баланс` некоторого `account` по `идентификатору`*/
    @Query(value = """
            select balance
            from iso_demo.accounts
            where id = :id
            """, nativeQuery = true)
    BigDecimal findBalanceByIdNative(@Param("id") Long id);

    /**
     * Обновить `баланс` некоторого `account` по `идентификатору`
     */
    @Modifying
    @Query(value = """
            update iso_demo.accounts
            set balance = :balance,
                updated_at = now()
            where id = :id
            """, nativeQuery = true)
    void updateBalanceByIdNative(@Param("id") Long id, @Param("balance") BigDecimal balance);

    /**
     * Добавить `баланс` некоторого `account` по `идентификатору`
     */
    @Modifying
    @Query(value = """
            update iso_demo.accounts
            set balance = balance + :delta,
                updated_at = now()
            where id = :id
            """, nativeQuery = true)
    void addDeltaToBalanceNative(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
