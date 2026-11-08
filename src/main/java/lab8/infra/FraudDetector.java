package lab8.infra;

import lab8.model.Order;

public class FraudDetector {
    public boolean isSuspicious(Order order, double total) {
        return total > 100_000 || "stolen".equals(order.paymentToken());
    }
}
