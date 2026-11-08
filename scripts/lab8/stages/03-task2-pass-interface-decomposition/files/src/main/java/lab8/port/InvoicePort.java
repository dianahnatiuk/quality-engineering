package lab8.port;

import lab8.model.Order;

public interface InvoicePort {
    String generate(Order order, double total);
}
