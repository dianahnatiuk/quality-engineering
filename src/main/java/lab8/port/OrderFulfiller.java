package lab8.port;

import lab8.model.Order;
import lab8.model.Quote;

public interface OrderFulfiller {
    String confirm(Order order, Quote quote);

    String cancel(Order order, Quote quote);
}
