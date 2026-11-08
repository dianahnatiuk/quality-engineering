package lab8.infra;

import lab8.model.Region;

public class ShippingCalculator {
    public double cost(Region region, int items) {
        double base = region == Region.UA ? 5.0 : region == Region.EU ? 15.0 : 25.0;
        return base + items;
    }
}
