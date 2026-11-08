package lab8.service;

import lab8.model.Order;
import lab8.model.OrderLine;
import lab8.port.AuditPort;
import lab8.port.InventoryPort;
import lab8.port.StockReserver;

import java.util.List;
import java.util.Objects;

/** Відповідальність №1: резервування складу (SRP). */
public class StockService implements StockReserver {
    private final InventoryPort inventory;
    private final AuditPort audit;

    public StockService(InventoryPort inventory, AuditPort audit) {
        this.inventory = Objects.requireNonNull(inventory);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override public boolean reserve(Order order) {
        List<OrderLine> lines = order.lines();
        for (int i = 0; i < lines.size(); i++) {
            OrderLine line = lines.get(i);
            if (!inventory.reserve(line.sku(), line.qty())) {
                for (int j = 0; j < i; j++) inventory.release(lines.get(j).sku(), lines.get(j).qty());
                audit.log("stock reservation failed for " + order.id());
                return false;
            }
        }
        audit.log("stock reserved for " + order.id());
        return true;
    }

    @Override public void release(Order order) {
        for (OrderLine line : order.lines()) inventory.release(line.sku(), line.qty());
        audit.log("stock released for " + order.id());
    }
}
