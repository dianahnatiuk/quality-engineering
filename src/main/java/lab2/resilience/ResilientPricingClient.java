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

    /**
     * ЛР№7 (PASS): жодної мутабельної змінної. Стан спроби живе в незмінних
     * Value Objects {@link RetryPolicy} / {@link RetryContext}; замість циклу з
     * лічильником - рекурсія з новим контекстом (глибина обмежена maxRetries+1).
     */
    private CallResult executeResilient(String itemId) {
        return attempt(itemId, RetryContext.begin(RetryPolicy.from(cfg)));
    }

    private CallResult attempt(final String itemId, final RetryContext ctx) {
        try {
            return success(itemId, call.call(itemId, cfg.requestTimeoutMs), ctx);
        } catch (DependencyError e) {
            return failed(itemId, ctx, e);
        }
    }

    private CallResult failed(final String itemId, final RetryContext ctx, final DependencyError e) {
        log.warn("Attempt " + ctx.number() + "/" + ctx.maxAttempts() +
                " failed for " + itemId + ": " + e.getMessage());
        return ctx.canRetry() ? retry(itemId, ctx) : fallback(itemId, ctx, e.getMessage());
    }

    private CallResult retry(final String itemId, final RetryContext ctx) {
        sleeper.sleep(ctx.backoffMs());
        return attempt(itemId, ctx.next());
    }

    private CallResult success(final String itemId, final double price, final RetryContext ctx) {
        if (ctx.isRetry()) {
            log.info("Dependency recovered for " + itemId + " after " + ctx.number() + " attempt(s)");
        }
        return new CallResult(true, false, price, ctx.number(), 200, "OK", ctx.elapsedMs());
    }

    private CallResult fallback(final String itemId, final RetryContext ctx, final String lastError) {
        log.error("FALLBACK activated for " + itemId + " after " + ctx.number() +
                " attempts. Last error: " + lastError);
        return new CallResult(true, true, cfg.fallbackPrice, ctx.number(), 200,
                "degraded:fallback (last_error=" + lastError + ")", ctx.elapsedMs());
    }

    private static double elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }
}