package main.java.lab2.resilience;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Механізм стійкості (Resilience Engineering) для критичної операції
 * "отримати ціну товару", яка залежить від зовнішнього Pricing Service.
 *
 * <p>Реалізовано два патерни:
 * <ol>
 *   <li><b>Retry</b> з обмеженою кількістю спроб та експоненційним backoff —
 *       захист від тимчасових збоїв (timeout / 5xx);</li>
 *   <li><b>Fallback</b> із безпечною деградованою відповіддю (кешована ціна),
 *       якщо всі спроби retry вичерпано.</li>
 * </ol>
 *
 * <p>Логіка не залежить від транспорту (отримує {@link DependencyCall}
 * ззовні), тому покривається детермінованим unit-тестом без піднімання
 * реального HTTP-сервера — див. {@code ResilienceTest} (Завдання №3).
 */
public final class ResilientPricingClient {

    private static final Logger log = Logger.getLogger(ResilientPricingClient.class.getName());

    private final DependencyCall call;
    private final ResilienceConfig cfg;
    private final boolean enabled;
    private final Sleeper sleeper;

    /** Дозволяє підмінити реальний Thread.sleep у тестах (щоб тести були швидкими). */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis);
    }

    public ResilientPricingClient(DependencyCall call, ResilienceConfig cfg, boolean enabled) {
        this(call, cfg, enabled, millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public ResilientPricingClient(DependencyCall call, ResilienceConfig cfg, boolean enabled, Sleeper sleeper) {
        this.call = call;
        this.cfg = cfg;
        this.enabled = enabled;
        this.sleeper = sleeper;
    }

    public CallResult getPrice(String itemId) {
        long start = System.nanoTime();

        if (!enabled) {
            // ---- BASELINE поведінка (без Retry/Fallback) — стан "до" ----
            try {
                double price = call.call(itemId, cfg.requestTimeoutMs);
                return new CallResult(true, false, price, 1, 200, "OK", elapsedMs(start));
            } catch (DependencyError e) {
                log.log(Level.SEVERE, "UNHANDLED dependency failure for " + itemId, e);
                return new CallResult(false, false, null, 1, 500,
                        "Unhandled dependency failure: " + e.getMessage(), elapsedMs(start));
            }
        }

        // ---- Resilient поведінка: Retry -> Fallback — стан "після" ----
        int attempts = 0;
        String lastError = null;
        double delayMs = cfg.baseDelayMs;

        while (attempts <= cfg.maxRetries) {
            attempts++;
            try {
                double price = call.call(itemId, cfg.requestTimeoutMs);
                if (attempts > 1) {
                    log.info("Dependency recovered for " + itemId + " after " + attempts + " attempt(s)");
                }
                return new CallResult(true, false, price, attempts, 200, "OK", elapsedMs(start));
            } catch (DependencyError e) {
                lastError = e.getMessage();
                log.warning("Attempt " + attempts + "/" + (cfg.maxRetries + 1) +
                        " failed for " + itemId + ": " + lastError);
                if (attempts <= cfg.maxRetries) {
                    sleeper.sleep((long) Math.min(delayMs, cfg.maxDelayMs));
                    delayMs *= cfg.backoffMultiplier; // exponential backoff
                }
            }
        }

        // Всі спроби вичерпано -> Fallback
        log.severe("FALLBACK activated for " + itemId + " after " + attempts +
                " attempts. Last error: " + lastError);
        return new CallResult(
                true,
                true,
                cfg.fallbackPrice,
                attempts,
                200,
                "degraded:fallback (last_error=" + lastError + ")",
                elapsedMs(start)
        );
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }
}
