package lab8.port;

import lab8.model.Region;

public interface CurrencyPolicy {
    double convert(double amount, Region region);
}
