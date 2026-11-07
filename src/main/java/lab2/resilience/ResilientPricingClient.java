package lab2.resilience;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.function.Function;

/**
 * Рефакторинг для Лабораторної №6: Використання Lookup Table (Map-based dispatch)
 * замість розгалужених умовних операторів для вибору стратегії виконання.
 */
public final class ResilientPricingClient {

    private static final Logger log = LogManager.getLogger(ResilientPricingClient.class);

    private final DependencyCall call;
    private final ResilienceConfig cfg;
    private final boolean enabled;
    private final Sleeper sleeper;

    // Lookup Table для вибору стратегії залежно від прапорця enabled
    private final Map<Boolean, Function<String, CallResult>> strategyTable;



    /** Дозволяє підмінити реальний Thread.sleep у тестах. */
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

        // Ініціалізація таблиці пошуку (Lookup Table)
        this.strategyTable = Map.of(
                false, this::executeBaseline,
                true, this::executeResilient
        );
    }

    public CallResult getPrice(String itemId) {
        // Виклик стратегії через Lookup Table замість розгалужень
        Function<String, CallResult> strategy = strategyTable.getOrDefault(enabled, this::executeBaseline);
        return strategy.apply(itemId);
    }

    private CallResult executeBaseline(String itemId) {
        long start = System.nanoTime();
        try {
            double price = call.call(itemId, cfg.requestTimeoutMs);
            return new CallResult(true, false, price, 1, 200, "OK", elapsedMs(start));
        } catch (DependencyError e) {
            log.error("UNHANDLED dependency failure for " + itemId, e);
            return new CallResult(false, false, null, 1, 500,
                    "Unhandled dependency failure: " + e.getMessage(), elapsedMs(start));
        }
    }

    private CallResult executeResilient(String itemId) {
        long start = System.nanoTime();
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
                log.warn("Attempt " + attempts + "/" + (cfg.maxRetries + 1) +
                        " failed for " + itemId + ": " + lastError);
                if (attempts <= cfg.maxRetries) {
                    // ЛР№7 SIMULATED DEFECT: "хотфікс SLO" - скоротити паузу вдвічі, але прямим
                    // ПЕРЕЗАПИСОМ змінної типу M (delayMs), а не локальною копією.
                    delayMs = delayMs * 0.5;
                    sleeper.sleep((long) Math.min(delayMs, cfg.maxDelayMs));
                    delayMs *= cfg.backoffMultiplier;
                }
            }
        }

        log.error("FALLBACK activated for " + itemId + " after " + attempts +
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