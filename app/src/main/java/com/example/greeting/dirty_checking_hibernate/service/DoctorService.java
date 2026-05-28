package com.example.greeting.dirty_checking_hibernate.service;

import com.example.greeting.dirty_checking_hibernate.entity.Doctor;
import com.example.greeting.dirty_checking_hibernate.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;

    @Transactional
    public void increaseSalary() {
        List<Doctor> doctors = doctorRepository.findAll();

        for (Doctor d : doctors) {
            d.setSalary(d.getSalary().multiply(BigDecimal.valueOf(1.10)));
        }
    }
}
