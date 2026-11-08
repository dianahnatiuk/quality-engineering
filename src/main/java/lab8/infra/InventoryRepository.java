package lab8.infra;

import java.util.HashMap;
import java.util.Map;

/** In-memory склад. */
public class InventoryRepository {
    private final Map<String, Integer> stock = new HashMap<>();

    public InventoryRepository() {
        stock.put("A-1", 100);
        stock.put("B-2", 50);
        stock.put("C-3", 5);
    }

    public boolean reserve(String sku, int qty) {
        int have = stock.getOrDefault(sku, 0);
        if (have < qty) return false;
        stock.put(sku, have - qty);
        return true;
    }

    public void release(String sku, int qty) {
        stock.put(sku, stock.getOrDefault(sku, 0) + qty);
    }

    public int available(String sku) {
        return stock.getOrDefault(sku, 0);
    }
}
