package lab2.resilience;

/**
 * Value Object (ЛР№7): незмінний контекст поточної спроби. Замінює три змінні-"мутанти"
 * старого методу: лічильник {@code attempts}, таймер {@code start} і (через політику)
 * {@code delayMs}. Перехід до наступної спроби не змінює об'єкт, а створює НОВИЙ.
 */
public record RetryContext(RetryPolicy policy, int number, long startNanos) {

    public static RetryContext begin(RetryPolicy policy) {
        return new RetryContext(policy, 1, System.nanoTime());
    }

    /** Нова спроба = новий об'єкт; поточний залишається без змін. */
    public RetryContext next() {
        return new RetryContext(policy, number + 1, startNanos);
    }

    public boolean canRetry() {
        return policy.allowsRetryAfter(number);
    }

    public boolean isRetry() {
        return number > 1;
    }

    public long backoffMs() {
        return policy.delayAfter(number);
    }

    public int maxAttempts() {
        return policy.maxRetries() + 1;
    }

    public double elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }
}
