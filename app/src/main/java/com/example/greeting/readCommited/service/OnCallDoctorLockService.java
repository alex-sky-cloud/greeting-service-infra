package com.example.greeting.readCommited.service;

import java.util.List;

import com.example.greeting.entity.OnCallDoctorEntity;
import com.example.greeting.readCommited.repository.OnCallDoctorLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для безопасного изменения графика дежурств
 * без возникновения write skew на READ COMMITTED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnCallDoctorLockService {

    private final OnCallDoctorLockRepository lockRepository;

    /**
     * Снимает врача с дежурства только если после проверки инварианта
     * остаётся минимум один дежурный врач.
     *
     * <p>Сценарий выполнения:</p>
     * <pre>
     * 1. Метод читает всех текущих on_call = true через SELECT ... FOR UPDATE.
     * 2. Если таких врачей <= 1, update не выполняется.
     * 3. Если таких врачей > 1, у указанного врача ставится on_call = false.
     * </pre>
     *
     * <p>За счёт FOR UPDATE конкурентные вызовы этого метода выполняют
     * проверку инварианта последовательно, а не параллельно.</p>
     *
     * @param doctorId идентификатор врача, которого нужно снять с дежурства
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void safeOffCall(Long doctorId) {
        List<OnCallDoctorEntity> onCallList =
                lockRepository.findAllOnCallForUpdate();

        int onCallCount = onCallList.size();
        log.info("onCallCount (FOR UPDATE) = {}", onCallCount);

        // breakpoint здесь

        if (onCallCount <= 1) {
            log.info("Нельзя снять врача {}, останется 0 on_call", doctorId);
            return;
        }

        lockRepository.updateOnCallNative(doctorId, false);
        log.info("Врач {} снят с дежурства", doctorId);
    }
}