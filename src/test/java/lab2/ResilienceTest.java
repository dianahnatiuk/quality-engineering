package test.java.lab2;

import main.java.lab2.resilience.CallResult;
import main.java.lab2.resilience.DependencyError;
import main.java.lab2.resilience.ResilienceConfig;
import main.java.lab2.resilience.ResilientPricingClient;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Автоматизований тест механізму стійкості (Завдання №3).
 *
 * <p>Тестовим дублером (test double) виступає лямбда {@code fakeDependency},
 * яка детерміновано (без реальної мережі, без {@code Thread.sleep} у
 * продакшн-сенсі — затримки самого Retry прискорені через
 * {@link ResilientPricingClient.Sleeper}) відтворює або нормальну
 * відповідь, або HTTP 5xx / timeout, залежно від сценарію. Це — "mock/stub"
 * зовнішньої залежності, який вимагається завданням.
 *
 * <p>Запуск (без будь-яких зовнішніх залежностей типу JUnit):
 * <pre>
 *   javac -d out $(find src/main src/test -name "*.java")
 *   java -cp out lab2.ResilienceTest
 * </pre>
 * Код завершення процесу: 0 — усі перевірки пройдено, 1 — є провал.
 * У реальному IntelliJ-проєкті цей самий клас так само легко переписати
 * як звичайний {@code @Test}-метод JUnit 5, підключивши залежність через
 * Gradle/Maven (див. README) — семантика перевірок не зміниться.
 */
public final class ResilienceTest {

    private static int failures = 0;

    public static void main(String[] args) {
        java.util.logging.Logger.getLogger("main.java.lab2.resilience").setLevel(java.util.logging.Level.OFF);
        System.out.println("=== ResilienceTest: automated verification of Retry + Fallback ===");

        testPositiveCase_NoFallbackWhenDependencyHealthy();
        testFaultInjection_FallbackActivatesAfterExhaustedRetries();
        testRetryCountIsBounded_NoRetryStorm();
        testDeterminism_ThreeConsecutiveRuns();

        System.out.println();
        if (failures == 0) {
            System.out.println("RESULT: ALL TESTS PASSED (0 failures)");
            System.exit(0);
        } else {
            System.out.println("RESULT: " + failures + " TEST(S) FAILED");
            System.exit(1);
        }
    }

    /** Позитивний сценарій: залежність здорова -> fallback НЕ активується. */
    private static void testPositiveCase_NoFallbackWhenDependencyHealthy() {
        String name = "testPositiveCase_NoFallbackWhenDependencyHealthy";
        AtomicInteger callCount = new AtomicInteger(0);
        var client = new ResilientPricingClient(
                (itemId, timeoutMs) -> {
                    callCount.incrementAndGet();
                    return 42.50; // здорова залежність завжди повертає ціну
                },
                ResilienceConfig.defaults(),
                true,
                millis -> {} // не спимо в тестах
        );

        CallResult result = client.getPrice("item-1");

        check(name, "success == true", result.success);
        check(name, "degraded == false", !result.degraded);
        check(name, "price == 42.50", result.price != null && result.price == 42.50);
        check(name, "attempts == 1 (без зайвих retry)", result.attempts == 1);
        check(name, "залежність викликана рівно 1 раз", callCount.get() == 1);
    }

    /** Fault Injection: залежність завжди відмовляє (HTTP 5xx) -> після
     *  вичерпання retry активується Fallback з очікуваним статусом і тілом. */
    private static void testFaultInjection_FallbackActivatesAfterExhaustedRetries() {
        String name = "testFaultInjection_FallbackActivatesAfterExhaustedRetries";
        ResilienceConfig cfg = ResilienceConfig.defaults(); // maxRetries = 3
        AtomicInteger callCount = new AtomicInteger(0);

        var client = new ResilientPricingClient(
                (itemId, timeoutMs) -> {
                    callCount.incrementAndGet();
                    throw new DependencyError("simulated HTTP 500");
                },
                cfg,
                true,
                millis -> {} // прискорюємо backoff-затримки в тесті
        );

        CallResult result = client.getPrice("item-2");

        check(name, "success == true (graceful degradation, НЕ exception)", result.success);
        check(name, "degraded == true", result.degraded);
        check(name, "price == fallbackPrice (" + cfg.fallbackPrice + ")",
                result.price != null && result.price == cfg.fallbackPrice);
        check(name, "statusCode == 200 (контрольований статус)", result.statusCode == 200);
        check(name, "attempts == maxRetries+1 (" + (cfg.maxRetries + 1) + ")",
                result.attempts == cfg.maxRetries + 1);
        check(name, "залежність викликана рівно maxRetries+1 разів",
                callCount.get() == cfg.maxRetries + 1);
        check(name, "діагностичне повідомлення містить причину відмови",
                result.message != null && result.message.contains("simulated HTTP 500"));
    }

    /** Запобігання retry storm: кількість спроб СУВОРО обмежена конфігурацією,
     *  навіть якщо залежність ніколи не відновлюється. */
    private static void testRetryCountIsBounded_NoRetryStorm() {
        String name = "testRetryCountIsBounded_NoRetryStorm";
        ResilienceConfig cfg = new ResilienceConfig(2, 10, 2.0, 100, 500, 1.0);
        AtomicInteger callCount = new AtomicInteger(0);

        var client = new ResilientPricingClient(
                (itemId, timeoutMs) -> {
                    callCount.incrementAndGet();
                    throw new DependencyError("timeout");
                },
                cfg,
                true,
                millis -> {}
        );

        CallResult result = client.getPrice("item-3");

        check(name, "attempts == maxRetries+1 == 3 (не більше)", result.attempts == 3);
        check(name, "залежність не викликається понад ліміт", callCount.get() == 3);
    }

    /** Детермінованість: той самий сценарій відмови відтворюється тричі
     *  поспіль зі стабільним результатом (не залежить від реальної мережі
     *  чи випадкових затримок). */
    private static void testDeterminism_ThreeConsecutiveRuns() {
        String name = "testDeterminism_ThreeConsecutiveRuns";
        ResilienceConfig cfg = ResilienceConfig.defaults();

        for (int run = 1; run <= 3; run++) {
            AtomicInteger callCount = new AtomicInteger(0);
            var client = new ResilientPricingClient(
                    (itemId, timeoutMs) -> {
                        callCount.incrementAndGet();
                        throw new DependencyError("simulated timeout");
                    },
                    cfg, true, millis -> {}
            );
            CallResult result = client.getPrice("item-determinism");
            check(name + " [run " + run + "]", "degraded == true", result.degraded);
            check(name + " [run " + run + "]", "attempts == " + (cfg.maxRetries + 1),
                    result.attempts == cfg.maxRetries + 1);
        }
    }

    private static void check(String testName, String assertion, boolean condition) {
        if (condition) {
            System.out.println("  [OK]   " + testName + " :: " + assertion);
        } else {
            System.out.println("  [FAIL] " + testName + " :: " + assertion);
            failures++;
        }
    }
}
