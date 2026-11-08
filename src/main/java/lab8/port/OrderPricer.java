package lab8.port;

import lab8.model.Order;
import lab8.model.Quote;

public interface OrderPricer {
    Quote quote(Order order);
}
