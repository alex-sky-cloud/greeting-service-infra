package com.example.greeting.serializable.controller;

import com.example.greeting.serializable.service.WriteSkewOnCallDoctorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Контроллер для демонстрации аномалии write skew
 * с таблицей on_call_doctors на уровне SERIALIZABLE.
 */
@RestController
@RequestMapping("/api/serializable/write-skew")
@RequiredArgsConstructor
public class WriteSkewOnCallDoctorController {

    private final WriteSkewOnCallDoctorService writeSkewService;

    /**
     * Запускает сценарий write skew для указанного врача.
     *
     * @param doctorId идентификатор врача
     */
    @PostMapping("/off-call")
    public void writeSkewSerializableMain(@RequestParam Long doctorId) {
        writeSkewService.writeSkewSerializableMain(doctorId);
    }
}
