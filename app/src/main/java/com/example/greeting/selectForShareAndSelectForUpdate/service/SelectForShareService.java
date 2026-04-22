package com.example.greeting.selectForShareAndSelectForUpdate.service;

import com.example.greeting.entity.OrderLineEntity;
import com.example.greeting.selectForShareAndSelectForUpdate.repository.SelectForShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SelectForShareService {

    private final SelectForShareRepository selectForShareRepository;

    /**
     * Считает суммарное количество (qty) по строкам заказа
     * и показывает разницу между чтением через Entity и через DTO.
     *
     * <p>Что было обнаружено:</p>
     * <ul>
     *   <li>Повторное чтение тех же строк как {@code OrderLineEntity} внутри одной транзакции
     *       может вернуть уже загруженные <b>managed-сущности</b> из <b>persistence contextv</b>.</li>
     *   <li>Из-за этого после конкурентного COMMIT в psql приложение продолжало видеть
     *       старые значения qty при повторном чтении через Entity.</li>
     * </ul>
     *
     * <p>Почему так происходит:</p>
     * <ul>
     *   <li>Hibernate держит <b>managed-entity</b> в <b>first-level cache</b>
     *       (persistence context) в рамках текущей транзакции.</li>
     *   <li>Когда повторный запрос снова мапится в ту же Entity с тем же id,
     *       Hibernate может вернуть уже существующий объект, а не новое состояние строки из БД.</li>
     * </ul>
     *
     * <p>Как решили:</p>
     * <ul>
     *   <li>Для повторного чтения используем DTO-проекцию, а не Entity.</li>
     *   <li>DTO не становится managed-сущностью и не пере-используется из <b>persistence context</b>.</li>
     *   <li>Поэтому DTO-запрос показывает фактическое состояние строк после COMMIT конкурентной транзакции.</li>
     * </ul>
     *
     * <p>Итог:</p>
     * <ul>
     *   <li>{@code firstSum} — сумма из первого чтения.</li>
     *   <li>{@code secondSumDto} — корректная сумма после конкурентного UPDATE + COMMIT.</li>
     *   <li>{@code secondSum} — повторное чтение через Entity, которое может показать старые значения
     *       из persistence context.</li>
     * </ul>
     *
     * @param orderNo номер заказа
     * @return сумма qty, рассчитанная по DTO как по актуальному состоянию строк
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BigDecimal calculateOrderQty(Long orderNo) {

        // Первое чтение тех же строк как Entity.
        List<OrderLineEntity> amount =
                selectForShareRepository.findByOrderNoOrderByOrderNoAsc(orderNo);

        // Считаем сумму qty по Entity из первого чтения.
        BigDecimal firstSum = amount.stream()
                .map(OrderLineEntity::getQty)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("firstSum = {}", firstSum);

        // breakpoint здесь: пока поток остановлен, делаем UPDATE + COMMIT в psql
        //в этот момент параллельная транзакция внесла изменения в базу данных и расчет ниже должен это показать
        // однако для этого, нужно обойти закэшированные значения

        // Повторно читаем те же строки, но уже в DTO-проекцию.
        List<OrderLineQtyDto> linesDto =
                selectForShareRepository.findByOrderNoForShareDto(orderNo);

        // Считаем сумму qty по DTO, минуя managed-Entity в persistence context.
        BigDecimal secondSumDto = linesDto.stream()
                .map(OrderLineQtyDto::getQty)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("secondSumDto = {}", secondSumDto);

        // Повторно читаем те же строки снова как Entity.
        List<OrderLineEntity> lines =
                selectForShareRepository.findByOrderNoForShare(orderNo);

        // Считаем сумму qty по Entity из второго чтения и здесь будут значения, которые были получены
        // в findByOrderNoOrderByOrderNoAsc, потому что они были закэшированы
        BigDecimal secondSum = lines.stream()
                .map(OrderLineEntity::getQty)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("secondSum = {}", secondSum);

        // Возвращаем именно DTO-результат как актуальное состояние после COMMIT.
        return secondSumDto;
    }
}
