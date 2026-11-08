package lab8.port;

import lab8.model.Region;

public interface ShippingPolicy {
    double cost(Region region, int items);
}
