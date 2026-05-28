package com.example.greeting.dirty_checking_hibernate.repository;

import com.example.greeting.dirty_checking_hibernate.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {
}
