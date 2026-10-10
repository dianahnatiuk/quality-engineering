package main.java.lab2.resilience;

/**
 * Абстракція виклику зовнішньої залежності (Pricing Service), незалежна від
 * конкретного транспорту. У продакшн-коді реалізується через
 * {@code java.net.http.HttpClient} (див. {@code MainService}); у тестах
 * підміняється тестовим дублером (mock/fake), що дає змогу детерміновано
 * відтворювати timeout / HTTP 5xx без реального сервера (Завдання №3).
 */
@FunctionalInterface
public interface DependencyCall {
    /**
     * @param itemId    ідентифікатор товару/замовлення
     * @param timeoutMs таймаут виклику в мілісекундах
     * @return ціну товару
     * @throws DependencyError якщо залежність недоступна
     */
    double call(String itemId, long timeoutMs) throws DependencyError;
}
