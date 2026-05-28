package com.example.greeting.dirty_checking_hibernate.controller;


import com.example.greeting.dirty_checking_hibernate.service.DoctorService;
import com.example.greeting.dirty_checking_hibernate.service.DoctorService2;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    private final DoctorService2 doctorService2;

    @PostMapping("/increase-salary")
    public ResponseEntity<String> increaseSalary() {
        doctorService.increaseSalary();
        return ResponseEntity.ok("salary updated");
    }

    @PostMapping("/increase-salary2")
    public ResponseEntity<String> increaseSalary2() {
        doctorService2.increaseSalaryPaged();
        return ResponseEntity.ok("salary updated");
    }
}
