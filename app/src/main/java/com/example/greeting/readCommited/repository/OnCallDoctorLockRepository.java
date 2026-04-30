package com.example.greeting.readCommited.repository;

import java.util.List;

import com.example.greeting.entity.OnCallDoctorEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/**
 * Репозиторий для работы с графиком дежурств с использованием
 * SELECT ... FOR UPDATE.
 */
public interface OnCallDoctorLockRepository extends JpaRepository<OnCallDoctorEntity, Long> {

    /**
     * Возвращает всех врачей, которые сейчас находятся на дежурстве,
     * и блокирует эти строки через SELECT ... FOR UPDATE.
     *
     * <p>SQL:</p>
     * <pre>
     * select id, doctor_name, on_call, updated_at
     * from iso_demo.on_call_doctors
     * where on_call = true
     * for update
     * </pre>
     *
     * <p>Пока текущая транзакция не завершится, другая транзакция не сможет
     * получить эти же строки под FOR UPDATE и принять конкурентное решение
     * по тому же инварианту.</p>
     *
     * @return список текущих дежурных врачей под блокировкой
     */
    @Query(value = """
        select id, doctor_name, on_call, updated_at
        from iso_demo.on_call_doctors
        where on_call = true
        for update
        """, nativeQuery = true)
    List<OnCallDoctorEntity> findAllOnCallForUpdate();

    /**
     * Обновляет флаг дежурства по идентификатору врача.
     *
     * <p>SQL:</p>
     * <pre>
     * update iso_demo.on_call_doctors
     * set on_call = :onCall,
     *     updated_at = now()
     * where id = :id
     * </pre>
     *
     * @param id идентификатор врача
     * @param onCall новое значение признака дежурства
     * @return количество обновлённых строк
     */
    @Modifying
    @Query(value = """
        update iso_demo.on_call_doctors
        set on_call = :onCall,
            updated_at = now()
        where id = :id
        """, nativeQuery = true)
    int updateOnCallNative(@Param("id") Long id, @Param("onCall") boolean onCall);
}