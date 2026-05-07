package com.example.greeting.serializable.processErrorSerialization.failFast.service;

import java.util.List;

import com.example.greeting.entity.OnCallDoctorEntity;
import com.example.greeting.serializable.processErrorSerialization.exception.WriteSkewFailFastSerializationException;
import com.example.greeting.serializable.processErrorSerialization.failFast.repository.WriteSkewFailFastRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для варианта fail fast
 * без автоматического повтора транзакции.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WriteSkewFailFastService {

    private final WriteSkewFailFastRepository doctorRepository;

    /**
     * Выполняет одну транзакцию SERIALIZABLE и при ошибке сериализации
     * преобразует её в прикладное исключение.
     *
     * @param doctorId идентификатор врача
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void offCallFailFast(Long doctorId) {
            List<OnCallDoctorEntity> onCallDoctors = doctorRepository.findAllOnCallNative();
            int onCallCount = onCallDoctors.size();

            log.info("Первое чтение on_call: doctorId={}, onCallCount={}", doctorId, onCallCount);

            if (onCallCount <= 1) {
                log.info("Нельзя снять врача {}: должен остаться хотя бы один on_call", doctorId);
                return;
            }

            doctorRepository.updateOnCallNative(doctorId, false);

            log.info("Врач {} снят с дежурства", doctorId);
        }
}