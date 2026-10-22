package com.example.vtcourse.t01;

import com.example.vtcourse.common.CallStats;
import com.example.vtcourse.common.Slow;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Шлюз SMS. Отправка одного сообщения блокирует поток на 200 мс.
 * Номер в формате {@code +7…} принимается, любой другой отклоняется.
 */
public class SmsGateway {

    private final CallStats stats = new CallStats();

    /** @return {@code true}, если сообщение принято. */
    public boolean send(String phone) throws InterruptedException {
        Slow.call(stats, 200);
        return phone.startsWith("+7");
    }

    public CallStats stats() {
        return stats;
    }
}
