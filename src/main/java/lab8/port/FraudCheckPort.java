package lab8.port;

import lab8.model.Order;

public interface FraudCheckPort {
    boolean isSuspicious(Order order, double total);
}
