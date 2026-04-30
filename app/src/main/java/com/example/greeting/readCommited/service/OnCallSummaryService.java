package com.example.greeting.readCommited.service;

import com.example.greeting.readCommited.repository.OnCallDoctorRepository;
import com.example.greeting.readCommited.repository.OnCallSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для безопасного снятия врача с дежурства
 * через агрегирующую строку-инвариант.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnCallSummaryService {

    private final OnCallDoctorRepository onCallDoctorRepository;
    private final OnCallSummaryRepository onСallSummaryRepository;

    /**
     * Снимает врача с дежурства только если агрегирующий счётчик
     * показывает, что после операции останется минимум один дежурный.
     *
     * <p>Сценарий выполнения:</p>
     * <pre>
     * 1. В начале транзакции читается on_call_summary(id = 1) через FOR UPDATE.
     * 2. Если on_call_count <= 1, update не выполняется.
     * 3. Если on_call_count > 1, врач снимается с дежурства.
     * 4. После этого on_call_count уменьшается на 1.
     * </pre>
     *
     * <p>За счёт блокировки одной агрегирующей строки конкурентные транзакции
     * проходят проверку инварианта строго по очереди.</p>
     *
     * @param doctorId идентификатор врача, которого нужно снять с дежурства
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void safeOffWithSummary(Long doctorId) {
        long onCallCount = onСallSummaryRepository.getOnCallCountForUpdate();
        log.info("on_call_count (summary, FOR UPDATE) = {}", onCallCount);

        // breakpoint здесь

        if (onCallCount <= 1) {
            log.info("Нельзя снять врача {}, summary-инвариант", doctorId);
            return;
        }

        onCallDoctorRepository.updateOnCallNative(doctorId, false);

        long newCount = onCallCount - 1;
        onСallSummaryRepository.updateOnCallCount(newCount);

        log.info("Врач {} снят, новый on_call_count = {}", doctorId, newCount);
    }
}
