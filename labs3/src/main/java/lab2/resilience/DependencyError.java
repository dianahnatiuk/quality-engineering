package lab2.resilience;

/**
 * Піднімається реалізацією {@link DependencyCall}, коли зовнішня залежність
 * недоступна: timeout, HTTP 5xx або помилка з'єднання.
 */
public class DependencyError extends Exception {
    public DependencyError(String message) {
        super(message);
    }

    public DependencyError(String message, Throwable cause) {
        super(message, cause);
    }
}
