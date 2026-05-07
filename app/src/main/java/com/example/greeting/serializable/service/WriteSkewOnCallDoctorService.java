package com.example.greeting.serializable.service;

import java.util.List;

import com.example.greeting.entity.OnCallDoctorEntity;
import com.example.greeting.serializable.repository.WriteSkewOnCallDoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для демонстрации аномалии write skew
 * с таблицей on_call_doctors на уровне изоляции SERIALIZABLE.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WriteSkewOnCallDoctorService {

    private final WriteSkewOnCallDoctorRepository doctorRepository;

    /**
     * Снимает врача с дежурства, если на момент чтения
     * дежурных врачей больше одного.
     * <p>
     * Сценарий выполнения:
     * <ol>
     *     <li>Читает список врачей с on_call = true.</li>
     *     <li>Определяет количество дежурных врачей.</li>
     *     <li>Если дежурных больше одного, снимает указанного врача с дежурства.</li>
     * </ol>
     * При конкурирующем запуске двух транзакций с таким алгоритмом
     * на уровне SERIALIZABLE одна из них должна быть откатана
     * с ошибкой сериализации.
     *
     * @param doctorId идентификатор врача, которого нужно снять с дежурства
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void writeSkewSerializableMain(Long doctorId) {
        List<OnCallDoctorEntity> onCallDoctors = doctorRepository.findAllOnCallNative();
        int onCallCount = onCallDoctors.size();

        log.info("Первое чтение on_call: doctorId={}, onCallCount={}", doctorId, onCallCount);

        // breakpoint здесь

        if (onCallCount <= 1) {
            log.info("Нельзя снять врача {}: должен остаться хотя бы один on_call", doctorId);
            return;
        }

        doctorRepository.updateOnCallNative(doctorId, false);

        log.info("Врач {} снят с дежурства", doctorId);
    }
}
