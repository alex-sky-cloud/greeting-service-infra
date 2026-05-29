package com.example.greeting.serializable.repository;

import java.util.List;

import com.example.greeting.entity.OnCallDoctorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Репозиторий для демонстрации аномалии write skew
 * с таблицей on_call_doctors.
 */
public interface WriteSkewOnCallDoctorRepository extends JpaRepository<OnCallDoctorEntity, Long> {

    /**
     * Возвращает всех врачей, которые находятся на дежурстве.
     *
     * SQL:
     * <pre>
     *     select id, doctor_name, on_call, updated_at
     *     from iso_demo.on_call_doctors
     *     where on_call = true
     *     orderN1 by id
     * </pre>
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
     * Обновляет флаг дежурства по идентификатору врача.
     *
     * SQL:
     * <pre>
     *     update iso_demo.on_call_doctors
     *     set on_call = :onCall,
     *         updated_at = now()
     *     where id = :id
     * </pre>
     *
     * @param id идентификатор врача
     * @param onCall новое значение флага дежурства
     * @return количество обновлённых строк
     */
    @Modifying
    @Query(value = """
        update iso_demo.on_call_doctors
        set on_call = :onCall,
            updated_at = now()
        where id = :id
        """, nativeQuery = true)
    int updateOnCallNative(@Param("id") Long id,
                           @Param("onCall") boolean onCall);
}
