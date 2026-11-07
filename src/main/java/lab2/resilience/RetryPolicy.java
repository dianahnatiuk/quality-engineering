package lab2.resilience;

/**
 * Value Object (ЛР№7): незмінна політика повторів. Згруповує чотири пов'язані
 * параметри (maxRetries, baseDelayMs, backoffMultiplier, maxDelayMs), які раніше
 * були розкидані по методу як окремі змінні типів C/P.
 *
 * <p>Клас є {@code record}: усі поля автоматично {@code private final}, немає
 * сеттерів, рівність за значенням. Обчислення "яка пауза перед наступною
 * спробою" тепер чиста функція: однакові аргументи дають однаковий результат і
 * не змінюють жодного стану (на відміну від старого {@code delayMs *= multiplier}).
 */
public record RetryPolicy(int maxRetries, long baseDelayMs, double backoffMultiplier, long maxDelayMs) {

    public static RetryPolicy from(ResilienceConfig cfg) {
        return new RetryPolicy(cfg.maxRetries, cfg.baseDelayMs, cfg.backoffMultiplier, cfg.maxDelayMs);
    }

    /** Чи дозволено ще одну спробу після {@code attemptsMade} невдалих. */
    public boolean allowsRetryAfter(int attemptsMade) {
        return attemptsMade <= maxRetries;
    }

    /** Пауза після {@code attemptsMade}-ї невдалої спроби: base * multiplier^(n-1), але не більше maxDelayMs. */
    public long delayAfter(int attemptsMade) {
        return (long) Math.min(baseDelayMs * Math.pow(backoffMultiplier, attemptsMade - 1), maxDelayMs);
    }
}
