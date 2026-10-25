package lab2.resilience;

/** Результат виконання захищеного виклику зовнішньої залежності. */
public final class CallResult {
    public final boolean success;
    public final boolean degraded;
    public final Double price;
    public final int attempts;
    public final int statusCode;
    public final String message;
    public final double totalTimeMs;

    public CallResult(boolean success, boolean degraded, Double price, int attempts,
                       int statusCode, String message, double totalTimeMs) {
        this.success = success;
        this.degraded = degraded;
        this.price = price;
        this.attempts = attempts;
        this.statusCode = statusCode;
        this.message = message;
        this.totalTimeMs = totalTimeMs;
    }

    @Override
    public String toString() {
        return "CallResult{success=" + success + ", degraded=" + degraded +
                ", price=" + price + ", attempts=" + attempts +
                ", statusCode=" + statusCode + ", message='" + message + "'}";
    }
}
