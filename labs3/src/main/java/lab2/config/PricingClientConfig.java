package lab2.config;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Безпечна конфігурація клієнта Pricing Service.
 *
 * <p>Ключ доступу НІКОЛИ не зберігається в коді чи репозиторії: він читається зі змінної середовища
 * {@value #ENV_API_KEY}. Локально значення задається у файлі {@code .env} (він у {@code .gitignore}),
 * а в репозиторії лежить лише шаблон {@code .env.example} без реальних значень.
 */
public final class PricingClientConfig {

    public static final String ENV_API_KEY = "PRICING_API_KEY";

    private PricingClientConfig() { }

    /** Ключ із реального середовища процесу. */
    public static Optional<String> apiKey() {
        return apiKey(System::getenv);
    }

    /** Те саме, але з підміною джерела змінних — для детермінованих тестів. */
    static Optional<String> apiKey(UnaryOperator<String> env) {
        String value = env.apply(ENV_API_KEY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
