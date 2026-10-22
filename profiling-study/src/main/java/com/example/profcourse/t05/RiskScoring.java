package com.example.profcourse.t05;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Скоринг кредитной заявки: нормализация анкеты, моделирование дефолта, итоговый балл.
 * Почти всё время CPU уходит в {@link #simulate(long, int)}.
 */
public class RiskScoring {

    public int score(long applicationId, int income, int debt) {
        double ratio = normalize(income, debt);
        double defaultShare = simulate(applicationId, 200_000);
        return (int) Math.round((1 - defaultShare) * 600 + ratio * 250);
    }

    double normalize(int income, int debt) {
        return income <= 0 ? 0 : Math.max(0, 1 - (double) debt / income);
    }

    /** Монте-Карло: доля сценариев, в которых клиент не платит. */
    double simulate(long seed, int scenarios) {
        long x = seed * 0x9E3779B97F4A7C15L + 1;
        int defaults = 0;
        for (int i = 0; i < scenarios; i++) {
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
            if ((x & 1023) < 37) {
                defaults++;
            }
        }
        return (double) defaults / scenarios;
    }
}
