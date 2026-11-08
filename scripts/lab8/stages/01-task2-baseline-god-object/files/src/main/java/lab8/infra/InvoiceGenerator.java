package lab8.infra;

import lab8.model.Order;

public class InvoiceGenerator {
    public String generate(Order order, double total) {
        String prefix = total < 0 ? "CN-" : "INV-";
        return prefix + order.id() + "-" + Math.round(Math.abs(total));
    }
}
