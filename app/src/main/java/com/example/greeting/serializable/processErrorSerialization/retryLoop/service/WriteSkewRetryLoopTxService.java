package com.example.greeting.serializable.processErrorSerialization.retryLoop.service;

import java.util.List;

import com.example.greeting.entity.OnCallDoctorEntity;
import com.example.greeting.serializable.processErrorSerialization.retryLoop.repository.WriteSkewRetryLoopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Транзакционный сервис одной попытки
 * для варианта retry loop.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WriteSkewRetryLoopTxService {

    private final WriteSkewRetryLoopRepository doctorRepository;

    /**
     * Выполняет одну попытку снятия врача с дежурства
     * в отдельной транзакции SERIALIZABLE.
     *
     * @param doctorId идентификатор врача
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void offCallOnce(Long doctorId) {
        List<OnCallDoctorEntity> onCallDoctors = doctorRepository.findAllOnCallNative();
        int onCallCount = onCallDoctors.size();

        log.info("Попытка SERIALIZABLE: doctorId={}, onCallCount={}", doctorId, onCallCount);

        if (onCallCount <= 1) {
            log.info("Нельзя снять врача {}: должен остаться хотя бы один on_call", doctorId);
            return;
        }

        doctorRepository.updateOnCallNative(doctorId, false);

        log.info("Врач {} снят с дежурства", doctorId);
    }
}
