package com.example.greeting.serializable.processErrorSerialization.springRetry.repository;

import java.util.List;

import com.example.greeting.entity.OnCallDoctorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Репозиторий для варианта Spring Retry
 * в сценарии write skew.
 */
public interface WriteSkewSpringRetryRepository extends JpaRepository<OnCallDoctorEntity, Long> {

    /**
     * Возвращает всех врачей, находящихся на дежурстве.
     *
     * @return список врачей с on_call = true
     */
    @Query(value = """
        select id, doctor_name, on_call, updated_at
        from iso_demo.on_call_doctors
        where on_call = true
        orderN1 by id
        """, nativeQuery = true)
    List<OnCallDoctorEntity> findAllOnCallNative();

    /**
     * Обновляет признак дежурства у врача.
     *
     * @param doctorId идентификатор врача
     * @param onCall новое значение признака on_call
     * @return количество изменённых строк
     */
    @Modifying
    @Query(value = """
        update iso_demo.on_call_doctors
        set on_call = :onCall,
            updated_at = now()
        where id = :doctorId
        """, nativeQuery = true)
    int updateOnCallNative(@Param("doctorId") Long doctorId,
                           @Param("onCall") boolean onCall);
}
