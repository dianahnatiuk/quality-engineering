package lab8.infra;

import lab8.port.PriceCatalogPort;
import java.util.HashMap;
import java.util.Map;

public class PriceCatalog implements PriceCatalogPort {
    private final Map<String, Double> prices = new HashMap<>();

    public PriceCatalog() {
        prices.put("A-1", 100.0);
        prices.put("B-2", 250.0);
        prices.put("C-3", 40.0);
    }

    @Override public double priceOf(String sku) {
        return prices.getOrDefault(sku, 0.0);
    }
}
