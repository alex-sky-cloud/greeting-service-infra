package com.example.greeting.readCommited.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OnCallSummaryRepository extends JpaRepository<OnCallSummaryEntity, Long> {

    /**Получить количество дежурных врачей с блокировкой агрегирующей строки*/
    @Query(value = """
        select on_call_count
        from iso_demo.on_call_summary
        where id = 1
        for update
        """, nativeQuery = true)
    long getOnCallCountForUpdate();

    /**Обновить количество дежурных врачей в агрегирующей строке*/
    @Modifying
    @Query(value = """
        update iso_demo.on_call_summary
        set on_call_count = :newCount,
            updated_at = now()
        where id = 1
        """, nativeQuery = true)
    int updateOnCallCount(@Param("newCount") long newCount);
}