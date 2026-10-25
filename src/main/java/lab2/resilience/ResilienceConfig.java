package lab2.resilience;

/**
 * Параметри механізму стійкості. Винесені в окремий клас (а не "магічні
 * числа" в коді), щоб їх можна було технічно обґрунтувати у звіті та
 * покрити тестами (Завдання №2, №3).
 *
 * <p>Обґрунтування типових значень (наведене також у звіті):
 * <ul>
 *   <li>{@code maxRetries=3} — достатньо для подолання короткочасного
 *       "дрижання" мережі/БД, але не перетворюється на retry storm;</li>
 *   <li>{@code baseDelayMs=50}, {@code backoffMultiplier=2.0},
 *       {@code maxDelayMs=400} — експоненційний backoff з верхньою межею,
 *       щоб сумарний час очікування залишався прогнозованим
 *       (50+100+200=350 ms гірший випадок для 3 повторів) і не збільшував
 *       навантаження на вже перевантажену залежність;</li>
 *   <li>{@code requestTimeoutMs=1000} — критична операція має SLO p95 ≤ 250 ms,
 *       тому очікування довше секунди на одну спробу вже недоцільне;</li>
 *   <li>{@code fallbackPrice} — безпечне консервативне значення "за
 *       замовчуванням", яке дозволяє показати користувачу відповідь замість
 *       відмови (Graceful Degradation), явно позначену прапорцем degraded=true.</li>
 * </ul>
 */
public final class ResilienceConfig {
    public final int maxRetries;
    public final long baseDelayMs;
    public final double backoffMultiplier;
    public final long maxDelayMs;
    public final long requestTimeoutMs;
    public final double fallbackPrice;

    public ResilienceConfig(int maxRetries, long baseDelayMs, double backoffMultiplier,
                             long maxDelayMs, long requestTimeoutMs, double fallbackPrice) {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelayMs = maxDelayMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.fallbackPrice = fallbackPrice;
    }

    /** Значення за замовчуванням, використані в MainService і в звіті. */
    public static ResilienceConfig defaults() {
        return new ResilienceConfig(
                /* maxRetries         */ 3,
                /* baseDelayMs        */ 50,
                /* backoffMultiplier  */ 2.0,
                /* maxDelayMs         */ 400,
                /* requestTimeoutMs   */ 1000,
                /* fallbackPrice      */ 9.99
        );
    }
}
