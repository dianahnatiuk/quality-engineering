package lab8.infra;

import lab8.model.Order;
import lab8.port.InvoicePort;

public class InvoiceGenerator implements InvoicePort {
    @Override public String generate(Order order, double total) {
        String prefix = total < 0 ? "CN-" : "INV-";
        return prefix + order.id() + "-" + Math.round(Math.abs(total));
    }
}
