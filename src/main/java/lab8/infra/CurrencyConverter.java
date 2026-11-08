package lab8.infra;

import lab8.model.Region;
import lab8.port.CurrencyPolicy;

public class CurrencyConverter implements CurrencyPolicy {
    @Override public double convert(double amount, Region region) {
        return region == Region.EU ? amount * 0.92 : amount;
    }
}
