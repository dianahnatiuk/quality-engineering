package lab8.port;

import lab8.model.Order;
import lab8.model.Quote;

public interface PaymentProcessor {
    boolean charge(Order order, Quote quote);

    boolean refund(Order order, Quote quote);
}
