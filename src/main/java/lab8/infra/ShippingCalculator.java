package lab8.infra;

import lab8.model.Region;
import lab8.port.ShippingPolicy;

public class ShippingCalculator implements ShippingPolicy {
    @Override public double cost(Region region, int items) {
        double base = region == Region.UA ? 5.0 : region == Region.EU ? 15.0 : 25.0;
        return base + items;
    }
}
