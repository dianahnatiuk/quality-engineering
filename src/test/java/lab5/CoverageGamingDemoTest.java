package lab5;

import lab2.resilience.DependencyError;
import lab2.resilience.ResilienceConfig;
import lab2.resilience.ResilientPricingClient;
import org.junit.jupiter.api.Test;

/**
 * ЛР№5, Завдання 2 — ДЕМОНСТРАЦІЯ пастки Гудхарта. Це ЗВИЧАЙНО-ПОГАНИЙ тест, навмисно написаний
 * так, як зробила б команда, якій Line Coverage вказали як єдиний KPI: код виконується, але
 * жодного assert немає. Тест не входить у звичайний прогін (виключений у surefire, див. pom-additions.xml).
 *
 * Демонстрація (з кореня проєкту):
 *   mvn clean test -Dtest=CoverageGamingDemoTest jacoco:report
 *       -> Line Coverage lab2.resilience.ResilientPricingClient різко зростає (target/site/jacoco/index.html)
 *   mvn org.pitest:pitest-maven:mutationCoverage -Dpit.tests=lab5.CoverageGamingDemoTest
 *       -> Mutation Score лишається низьким: мутанти виживають, бо тест нічого не перевіряє
 *
 * УВАГА: сигнатури ResilienceConfig.defaults() та new DependencyError(String) взято за змістом проєкту —
 * якщо не компілюється, підправте під ваші реальні конструктори.
 */
class CoverageGamingDemoTest {

    @Test
    void touchesEveryBranchButAssertsNothing() {
        ResilienceConfig cfg = ResilienceConfig.defaults();

        // success path
        new ResilientPricingClient((item, timeoutMs) -> 9.99, cfg, true, ms -> { }).getPrice("1");

        // retry -> fallback path
        new ResilientPricingClient((item, timeoutMs) -> { throw new DependencyError("boom"); },
                cfg, true, ms -> { }).getPrice("2");

        // baseline (resilience OFF): success + failure
        new ResilientPricingClient((item, timeoutMs) -> 1.0, cfg, false, ms -> { }).getPrice("3");
        new ResilientPricingClient((item, timeoutMs) -> { throw new DependencyError("boom"); },
                cfg, false, ms -> { }).getPrice("4");

        // жодного assertEquals / assertTrue / assertThrows -> 100% виконаних рядків, 0 перевірених інваріантів
    }
}
