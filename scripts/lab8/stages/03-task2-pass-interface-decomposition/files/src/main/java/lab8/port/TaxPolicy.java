package lab8.port;

import lab8.model.Region;

public interface TaxPolicy {
    double calculate(double taxable, Region region, boolean business);
}
