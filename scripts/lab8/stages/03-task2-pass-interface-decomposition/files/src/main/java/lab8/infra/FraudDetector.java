package lab8.infra;

import lab8.model.Order;
import lab8.port.FraudCheckPort;

public class FraudDetector implements FraudCheckPort {
    @Override public boolean isSuspicious(Order order, double total) {
        return total > 100_000 || "stolen".equals(order.paymentToken());
    }
}
