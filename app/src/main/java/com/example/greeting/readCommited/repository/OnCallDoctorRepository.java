package com.example.greeting.readCommited.repository;

import java.util.List;

import com.example.greeting.entity.OnCallDoctorEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OnCallDoctorRepository extends JpaRepository<OnCallDoctorEntity, Long> {

    /**Получить весь график дежурств*/
    @Query(value = """
            select id, doctor_name, on_call, updated_at
            from iso_demo.on_call_doctors
            order by id
            """, nativeQuery = true)
    List<OnCallDoctorEntity> findAllNative();

    /**Получить график дежурства по идентификатору*/
    @Query(value = """
            select id, doctor_name, on_call, updated_at
            from iso_demo.on_call_doctors
            where id = :id
            """, nativeQuery = true)
    OnCallDoctorEntity findByIdNative(@Param("id") Long id);

    /**Посчитать количество дежурств*/
    @Query(value = """
            select count(*)
            from iso_demo.on_call_doctors
            where on_call = true
            """, nativeQuery = true)
    long countOnCallDoctorsNative();


    /**Обновить график дежурства по идентификатору*/
    @Modifying
    @Query(value = """
            update iso_demo.on_call_doctors
            set on_call   = :onCall,
                updated_at = now()
            where id = :id
            """, nativeQuery = true)
    int updateOnCallNative(@Param("id") Long id, @Param("onCall") boolean onCall);
}
