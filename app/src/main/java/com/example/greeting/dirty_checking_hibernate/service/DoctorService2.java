package com.example.greeting.dirty_checking_hibernate.service;

import com.example.greeting.dirty_checking_hibernate.entity.Doctor;
import com.example.greeting.dirty_checking_hibernate.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class DoctorService2 {

    private final DoctorRepository doctorRepository;

    public void increaseSalaryPaged() {
        int page = 0;
        int size = 100;

        while (true) {
            Page<Doctor> doctors = increaseSalaryForPage(page, size);
            if (doctors.isEmpty()) {
                break;
            }
            page++;
        }
    }

    @Transactional
    public Page<Doctor> increaseSalaryForPage(int page, int size) {
        Page<Doctor> doctors = doctorRepository.findAll(PageRequest.of(page, size));

        for (Doctor d : doctors.getContent()) {
            d.setSalary(d.getSalary().multiply(BigDecimal.valueOf(1.10)));
        }

        return doctors;
    }
}
