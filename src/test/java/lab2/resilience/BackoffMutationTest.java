package lab2.resilience;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ЛР№7, Завдання 2: краш-тест на неконтрольовану мутацію змінної типу M.
 *
 * Документований графік backoff (див. Javadoc ResilienceConfig): 50 + 100 + 200 = 350 ms.
 * Якщо хтось перезапише delayMs "по дорозі" (мутабельність), графік ламається, і
 * клієнт починає довбати залежність майже без пауз (retry storm).
 *
 * FAIL-коміт: ці тести червоні на коді з мутацією delayMs.
 * PASS-коміт: зелені після переходу на незмінні RetryPolicy / RetryContext.
 */
class BackoffMutationTest {

    private static List<Long> recordedPauses(ResilienceConfig cfg) {
        List<Long> pauses = new ArrayList<>();
        new ResilientPricingClient(
                (id, t) -> { throw new DependencyError("simulated HTTP 503"); },
                cfg, true, pauses::add).getPrice("item-mut");
        return pauses;
    }

    @Test
    void backoffFollowsDocumentedExponentialSchedule() {
        assertEquals(List.of(50L, 100L, 200L), recordedPauses(ResilienceConfig.defaults()));
    }

    @Test
    void totalWaitStaysAtDocumentedWorstCase350ms() {
        long total = recordedPauses(ResilienceConfig.defaults()).stream().mapToLong(Long::longValue).sum();
        assertEquals(350L, total);
    }

    @Test
    void pausesGrowStrictlyUntilCap_noConstantHammering() {
        List<Long> pauses = recordedPauses(new ResilienceConfig(4, 50, 2.0, 400, 1000, 9.99));
        assertEquals(List.of(50L, 100L, 200L, 400L), pauses);
        for (int i = 1; i < pauses.size(); i++) {
            assertTrue(pauses.get(i) > pauses.get(i - 1), "пауза має зростати: " + pauses);
        }
    }
}
