package lab2.resilience;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** ЛР№7: перевірка незмінності та поведінки Value Objects + регресія сценаріїв повторів. */
class RetryValueObjectsTest {

    private static final RetryPolicy POLICY = new RetryPolicy(3, 50, 2.0, 400);

    @Test
    void delayScheduleIsExponentialAndCapped() {
        RetryPolicy p = new RetryPolicy(5, 50, 2.0, 400);
        assertEquals(50L, p.delayAfter(1));
        assertEquals(100L, p.delayAfter(2));
        assertEquals(200L, p.delayAfter(3));
        assertEquals(400L, p.delayAfter(4));
        assertEquals(400L, p.delayAfter(5));
    }

    @Test
    void retryAllowedOnlyWhileAttemptsDoNotExceedMaxRetries() {
        assertTrue(POLICY.allowsRetryAfter(1));
        assertTrue(POLICY.allowsRetryAfter(3));
        assertFalse(POLICY.allowsRetryAfter(4));
    }

    @Test
    void next_returnsNewObject_andLeavesOriginalUntouched() {
        RetryContext first = RetryContext.begin(POLICY);
        RetryContext second = first.next();

        assertEquals(1, first.number());
        assertEquals(2, second.number());
        assertNotSame(first, second);
        assertEquals(first.startNanos(), second.startNanos());
        assertFalse(first.isRetry());
        assertTrue(second.isRetry());
        assertEquals(4, first.maxAttempts());
        assertTrue(first.elapsedMs() >= 0.0);
    }

    @Test
    void valueObjectsAreComparedByValue() {
        assertEquals(new RetryPolicy(3, 50, 2.0, 400), POLICY);
        assertEquals(POLICY, RetryPolicy.from(ResilienceConfig.defaults()));
    }

    @Test
    void allFieldsOfValueObjectsAreFinal() {
        for (Class<?> type : new Class<?>[]{RetryPolicy.class, RetryContext.class}) {
            for (Field f : type.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    assertTrue(Modifier.isFinal(f.getModifiers()), type.getSimpleName() + "." + f.getName());
                }
            }
        }
    }

    @Test
    void recoversAfterTransientFailure_reportsTwoAttempts_notDegraded() {
        AtomicInteger calls = new AtomicInteger();
        ResilientPricingClient client = new ResilientPricingClient((id, t) -> {
            if (calls.incrementAndGet() == 1) {
                throw new DependencyError("blip");
            }
            return 17.25;
        }, ResilienceConfig.defaults(), true, millis -> { });

        CallResult r = client.getPrice("item-5");

        assertTrue(r.success);
        assertFalse(r.degraded);
        assertEquals(17.25, r.price, 1e-9);
        assertEquals(2, r.attempts);
    }
}
