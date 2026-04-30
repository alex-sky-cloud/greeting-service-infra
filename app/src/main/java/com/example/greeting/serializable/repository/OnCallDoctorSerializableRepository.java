package com.example.greeting.serializable.repository;

import java.util.List;

import com.example.greeting.entity.OnCallDoctorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Репозиторий для сценария write skew с дежурствами врачей
 * на уровне SERIALIZABLE.
 */
public interface OnCallDoctorSerializableRepository extends JpaRepository<OnCallDoctorEntity, Long> {

    /**
     * Возвращает всех врачей, которые сейчас находятся на дежурстве.
     *
     * SQL:
     * <p
     * select id, doctor_name, on_call, updated_at
     * from iso_demo.on_call_doctors
     * where on_call = true
     * order by id
     */
    @Query(value = """
        select id, doctor_name, on_call, updated_at
        from iso_demo.on_call_doctors
        where on_call = true
        order by id
        """, nativeQuery = true)
    List<OnCallDoctorEntity> findAllOnCallNative();

    /**
     * Обновляет флаг дежурства по идентификатору врача.
     *
     * SQL:
     * update iso_demo.on_call_doctors
     * set on_call = :onCall,
     *     updated_at = now()
     * where id = :id
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
