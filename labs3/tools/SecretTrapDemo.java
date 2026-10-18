import java.io.IOException;
import java.nio.file.*;

/**
 * Secrets Trap: безпечний краш-тест для Gitleaks / pre-commit hook (Лабораторна №3, Завдання 3).
 *
 *   java tools/SecretTrapDemo.java create   - створює тестовий файл із СИНТЕТИЧНИМ hardcoded-секретом
 *   java tools/SecretTrapDemo.java fix      - усуває порушення: файл читає значення зі змінної середовища,
 *                                             а локальне значення потрапляє лише у .env (він у .gitignore)
 *
 * Значення "lab3-FAKE-..." вигадане: воно не дає доступу до жодного сервісу. Воно збирається з трьох частин
 * під час виконання, тому в самому коді інструмента готового "секрету" немає.
 */
public class SecretTrapDemo {

    private static final Path TARGET = Paths.get("src/main/java/lab2/config/LegacyPricingSettings.java");
    private static final Path DOT_ENV = Paths.get(".env");
    private static final String A = "lab3-FAKE-";
    private static final String B1 = "9f8e7d6c5b4a";
    private static final String B2 = "39281706f5e4d3c2b1a0";

    public static void main(String[] args) throws IOException {
        String cmd = args.length > 0 ? args[0] : "";
        switch (cmd) {
            case "create" -> create();
            case "fix" -> fix();
            default -> System.out.println("Usage: java tools/SecretTrapDemo.java create|fix");
        }
    }

    private static void create() throws IOException {
        String source = """
                package lab2.config;

                /** ДЕМО (небезпечно): ключ вшито прямо в код. */
                public final class LegacyPricingSettings {

                    private static final String PRICING_API_KEY = "%s";

                    private LegacyPricingSettings() { }

                    public static String apiKey() {
                        return PRICING_API_KEY;
                    }
                }
                """.formatted(A + B1 + B2);
        Files.createDirectories(TARGET.getParent());
        Files.writeString(TARGET, source);
        System.out.println("Створено тестовий файл з синтетичним секретом: " + TARGET);
        System.out.println("Далі:  git add " + TARGET.toString().replace('\\', '/'));
        System.out.println("       git commit -m \"demo: legacy settings\"   -> очікується FAIL (коміт заблоковано)");
    }

    private static void fix() throws IOException {
        String source = """
                package lab2.config;

                import java.util.Optional;

                /** Безпечна версія: значення береться зі змінної середовища PRICING_API_KEY, у коді секрету немає. */
                public final class LegacyPricingSettings {

                    private LegacyPricingSettings() { }

                    public static Optional<String> apiKey() {
                        return PricingClientConfig.apiKey();
                    }
                }
                """;
        Files.writeString(TARGET, source);
        Files.writeString(DOT_ENV, PricingKeyLine());
        System.out.println("Секрет прибрано з коду: " + TARGET);
        System.out.println("Локальне значення записано у .env (файл у .gitignore, до репозиторію не потрапить).");
        System.out.println("Далі:  git add -A   і   git commit -m \"fix: read pricing key from environment\"   -> очікується PASS");
    }

    private static String PricingKeyLine() {
        return "PRICING_API_KEY=" + A + B1 + B2 + System.lineSeparator();
    }
}
