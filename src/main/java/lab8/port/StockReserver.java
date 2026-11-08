package lab8.port;

import lab8.model.Order;

public interface StockReserver {
    boolean reserve(Order order);

    void release(Order order);
}
