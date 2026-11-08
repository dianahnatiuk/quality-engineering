package lab8.model;

public record OrderResult(boolean success, String message, Quote quote, String invoiceId) {

    public static OrderResult ok(Quote quote, String invoiceId) {
        return new OrderResult(true, "OK", quote, invoiceId);
    }

    public static OrderResult failed(String message) {
        return new OrderResult(false, message, null, null);
    }
}
