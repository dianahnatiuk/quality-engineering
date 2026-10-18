package lab2.resilience;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Автоматизований resilience-тест (Лаб. №2, Завдання 3) у форматі JUnit 5.
 * Зовнішня залежність підміняється лямбдою (test double) — без мережі і без реальних пауз.
 */
class ResilientPricingClientTest {

    private static ResilientPricingClient client(DependencyCall dependency, ResilienceConfig cfg) {
        return new ResilientPricingClient(dependency, cfg, true, millis -> { });
    }

    @Test
    void healthyDependency_doesNotActivateFallback() {
        AtomicInteger calls = new AtomicInteger();
        CallResult r = client((id, t) -> { calls.incrementAndGet(); return 42.50; },
                ResilienceConfig.defaults()).getPrice("item-1");

        assertTrue(r.success);
        assertFalse(r.degraded);
        assertEquals(42.50, r.price, 1e-9);
        assertEquals(1, r.attempts);
        assertEquals(1, calls.get());
    }

    @Test
    void failingDependency_activatesFallbackAfterExhaustedRetries() {
        ResilienceConfig cfg = ResilienceConfig.defaults();
        AtomicInteger calls = new AtomicInteger();
        CallResult r = client((id, t) -> { calls.incrementAndGet(); throw new DependencyError("simulated HTTP 500"); },
                cfg).getPrice("item-2");

        assertTrue(r.success, "graceful degradation, а не виняток");
        assertTrue(r.degraded);
        assertEquals(cfg.fallbackPrice, r.price, 1e-9);
        assertEquals(200, r.statusCode);
        assertEquals(cfg.maxRetries + 1, r.attempts);
        assertEquals(cfg.maxRetries + 1, calls.get());
        assertTrue(r.message.contains("simulated HTTP 500"));
    }

    @Test
    void retryCountIsBounded_noRetryStorm() {
        ResilienceConfig cfg = new ResilienceConfig(2, 10, 2.0, 100, 500, 1.0);
        AtomicInteger calls = new AtomicInteger();
        CallResult r = client((id, t) -> { calls.incrementAndGet(); throw new DependencyError("timeout"); },
                cfg).getPrice("item-3");

        assertEquals(3, r.attempts);
        assertEquals(3, calls.get());
    }

    @Test
    void withoutResilience_failureIsPropagatedAsHttp500() {
        ResilientPricingClient c = new ResilientPricingClient(
                (id, t) -> { throw new DependencyError("down"); }, ResilienceConfig.defaults(), false);
        CallResult r = c.getPrice("item-4");

        assertFalse(r.success);
        assertEquals(500, r.statusCode);
        assertEquals(1, r.attempts);
    }
}
