package lab2.resilience;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ЛР№5, Завдання 2: тести з ПЕРЕВІРКАМИ (на відміну від CoverageGamingDemoTest), що вбивають мутантів Pitest
 * у ResilientPricingClient: межа retry, експоненційний backoff, відсутність паузи після останньої спроби,
 * вміст CallResult, режим baseline, elapsedMs, реальний Sleeper за замовчуванням.
 *
 * Тест лежить у пакеті lab2.resilience, щоб читати поля ResilienceConfig так само, як це робить клієнт.
 * Поля CallResult читаються рефлексією ЗА ПОРЯДКОМ оголошення (success, degraded, price, attempts,
 * status, message, elapsedMs) — це порядок аргументів конструктора в ResilientPricingClient.
 */
class ResilientPricingClientBehaviourTest {

    private final ResilienceConfig cfg = ResilienceConfig.defaults();

    // ------------------------------------------------------------------ допоміжні засоби

    private static DependencyError error(String msg) {
        try {
            for (Constructor<?> c : DependencyError.class.getDeclaredConstructors()) {
                Class<?>[] types = c.getParameterTypes();
                Object[] args = new Object[types.length];
                boolean ok = true;
                for (int i = 0; i < types.length; i++) {
                    if (types[i] == String.class) args[i] = msg;
                    else if (!types[i].isPrimitive()) args[i] = null;
                    else ok = false;
                }
                if (ok) {
                    c.setAccessible(true);
                    return (DependencyError) c.newInstance(args);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Не вдалося створити DependencyError: " + e);
        }
        throw new AssertionError("Немає придатного конструктора DependencyError — надішліть файл, підправлю тест");
    }

    /** Значення i-го нестатичного поля результату (порядок = порядок аргументів конструктора). */
    private static Object part(Object result, int index) {
        try {
            List<Field> fields = new ArrayList<>();
            for (Field f : result.getClass().getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) fields.add(f);
            }
            assertEquals(7, fields.size(), "CallResult має 7 полів (success, degraded, price, attempts, status, message, elapsedMs)");
            Field f = fields.get(index);
            f.setAccessible(true);
            return f.get(result);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static boolean success(Object r)   { return (Boolean) part(r, 0); }
    private static boolean degraded(Object r)  { return (Boolean) part(r, 1); }
    private static Double  price(Object r)     { Object v = part(r, 2); return v == null ? null : ((Number) v).doubleValue(); }
    private static int     attempts(Object r)  { return ((Number) part(r, 3)).intValue(); }
    private static int     status(Object r)    { return ((Number) part(r, 4)).intValue(); }
    private static String  message(Object r)   { return String.valueOf(part(r, 5)); }
    private static double  elapsed(Object r)   { return ((Number) part(r, 6)).doubleValue(); }

    private int maxRetries() { return (int) cfg.maxRetries; }

    /** Скільки пауз і яких саме очікуємо між спробами (backoff з обмеженням maxDelayMs). */
    private List<Long> expectedSleeps(int count) {
        List<Long> expected = new ArrayList<>();
        double delay = cfg.baseDelayMs;
        for (int i = 0; i < count; i++) {
            expected.add((long) Math.min(delay, cfg.maxDelayMs));
            delay *= cfg.backoffMultiplier;
        }
        return expected;
    }

    /** Залежність: перші failTimes викликів падають ("fail-1", "fail-2", ...), далі повертає price. */
    private static DependencyCall flaky(int failTimes, double price, AtomicInteger calls, List<Object> timeouts) {
        return (id, timeout) -> {
            timeouts.add(timeout);
            int k = calls.incrementAndGet();
            if (k <= failTimes) {
                throw error("fail-" + k);
            }
            return price;
        };
    }

    // ------------------------------------------------------------------ Resilient: успіх

    @Test
    void firstAttemptSuccess_returnsPriceWithoutRetryOrSleep() {
        AtomicInteger calls = new AtomicInteger();
        List<Object> timeouts = new ArrayList<>();
        List<Long> slept = new ArrayList<>();
        ResilientPricingClient client = new ResilientPricingClient(flaky(0, 12.5, calls, timeouts), cfg, true, slept::add);

        Object r = client.getPrice("item-1");

        assertTrue(success(r));
        assertFalse(degraded(r));
        assertEquals(12.5, price(r), 1e-9);
        assertEquals(1, attempts(r));
        assertEquals(200, status(r));
        assertEquals("OK", message(r));
        assertEquals(1, calls.get());
        assertTrue(slept.isEmpty(), "при успіху з першої спроби паузи не потрібні");
        assertEquals(((Number) timeouts.get(0)).doubleValue(), (double) cfg.requestTimeoutMs, 1e-9,
                "клієнт має передавати timeout із конфігурації");
    }

    @Test
    void recoversAfterTwoFailures_usesBackoffAndIsNotDegraded() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> slept = new ArrayList<>();
        ResilientPricingClient client = new ResilientPricingClient(
                flaky(2, 7.0, calls, new ArrayList<>()), cfg, true, slept::add);

        Object r = client.getPrice("item-2");

        assertTrue(success(r));
        assertFalse(degraded(r));
        assertEquals(7.0, price(r), 1e-9);
        assertEquals(3, attempts(r));
        assertEquals(3, calls.get());
        assertEquals(expectedSleeps(2), slept, "між трьома спробами — рівно дві паузи з наростанням");
        assertEquals("OK", message(r));
    }

    // ------------------------------------------------------------------ Resilient: fallback

    @Test
    void allAttemptsFail_activatesFallbackAfterMaxRetriesPlusOneCalls() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> slept = new ArrayList<>();
        ResilientPricingClient client = new ResilientPricingClient(
                flaky(Integer.MAX_VALUE, 0, calls, new ArrayList<>()), cfg, true, slept::add);

        Object r = client.getPrice("item-3");

        int total = maxRetries() + 1;
        assertEquals(total, calls.get(), "maxRetries повторів + перша спроба");
        assertEquals(total, attempts(r));
        assertTrue(success(r), "fallback — це успішна деградована відповідь");
        assertTrue(degraded(r));
        assertEquals(cfg.fallbackPrice, price(r), 1e-9);
        assertEquals(200, status(r));
        assertTrue(message(r).startsWith("degraded:fallback"), message(r));
        assertTrue(message(r).contains("fail-" + total), "у повідомленні — ОСТАННЯ помилка: " + message(r));
        assertEquals(expectedSleeps(maxRetries()), slept, "після останньої спроби пауза не робиться");
    }

    @Test
    void backoffIsCappedByMaxDelay() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> slept = new ArrayList<>();
        new ResilientPricingClient(flaky(Integer.MAX_VALUE, 0, calls, new ArrayList<>()), cfg, true, slept::add)
                .getPrice("item-4");

        for (Long s : slept) {
            assertTrue(s <= (long) cfg.maxDelayMs, "пауза " + s + " перевищує maxDelayMs");
            assertTrue(s >= (long) Math.min(cfg.baseDelayMs, cfg.maxDelayMs), "пауза менша за baseDelayMs");
        }
    }

    // ------------------------------------------------------------------ Baseline (resilience вимкнено)

    @Test
    void disabled_success_singleCallNoRetry() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> slept = new ArrayList<>();
        ResilientPricingClient client = new ResilientPricingClient(
                flaky(0, 3.25, calls, new ArrayList<>()), cfg, false, slept::add);

        Object r = client.getPrice("item-5");

        assertTrue(success(r));
        assertFalse(degraded(r));
        assertEquals(3.25, price(r), 1e-9);
        assertEquals(1, attempts(r));
        assertEquals(200, status(r));
        assertEquals("OK", message(r));
        assertEquals(1, calls.get());
        assertTrue(slept.isEmpty());
    }

    @Test
    void disabled_failure_returnsUnhandled500WithoutRetryOrFallback() {
        AtomicInteger calls = new AtomicInteger();
        List<Long> slept = new ArrayList<>();
        ResilientPricingClient client = new ResilientPricingClient(
                flaky(Integer.MAX_VALUE, 0, calls, new ArrayList<>()), cfg, false, slept::add);

        Object r = client.getPrice("item-6");

        assertFalse(success(r));
        assertFalse(degraded(r));
        assertNull(price(r));
        assertEquals(1, attempts(r));
        assertEquals(500, status(r));
        assertTrue(message(r).startsWith("Unhandled dependency failure"), message(r));
        assertTrue(message(r).contains("fail-1"), message(r));
        assertEquals(1, calls.get(), "без resilience — жодних повторів");
        assertTrue(slept.isEmpty());
    }

    // ------------------------------------------------------------------ час виконання

    @Test
    void elapsedMs_isSmallAndNonNegativeForFastCall() {
        ResilientPricingClient client = new ResilientPricingClient(
                flaky(0, 1.0, new AtomicInteger(), new ArrayList<>()), cfg, true, ms -> { });

        double ms = elapsed(client.getPrice("item-7"));

        assertTrue(ms >= 0.0, "elapsedMs не може бути від'ємним: " + ms);
        assertTrue(ms < 2000.0, "швидкий виклик має займати менше 2 с, а не " + ms + " мс");
    }

    // ------------------------------------------------------------------ реальний Sleeper за замовчуванням

    @Test
    void defaultSleeper_reallyWaitsBetweenAttempts() {
        ResilientPricingClient client = new ResilientPricingClient(
                flaky(Integer.MAX_VALUE, 0, new AtomicInteger(), new ArrayList<>()), cfg, true);

        double expectedTotal = expectedSleeps(maxRetries()).stream().mapToLong(Long::longValue).sum();
        long start = System.nanoTime();
        Object r = client.getPrice("item-8");
        double waitedMs = (System.nanoTime() - start) / 1_000_000.0;

        assertTrue(degraded(r));
        assertTrue(waitedMs >= expectedTotal * 0.9,
                "реальні паузи мають тривати ~" + expectedTotal + " мс, фактично " + waitedMs);
    }

    @Test
    void defaultSleeper_restoresInterruptFlag() {
        ResilientPricingClient client = new ResilientPricingClient(
                flaky(1, 5.0, new AtomicInteger(), new ArrayList<>()), cfg, true);
        try {
            Thread.currentThread().interrupt();
            Object r = client.getPrice("item-9");

            assertTrue(success(r));
            assertTrue(Thread.currentThread().isInterrupted(),
                    "InterruptedException не можна ковтати — прапорець переривання має бути відновлено");
        } finally {
            Thread.interrupted(); // очистити прапорець, щоб не зашкодити іншим тестам
        }
    }
}
