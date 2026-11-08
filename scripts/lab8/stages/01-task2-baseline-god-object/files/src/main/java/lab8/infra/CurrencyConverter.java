package lab8.infra;

import lab8.model.Region;

public class CurrencyConverter {
    public double convert(double amount, Region region) {
        return region == Region.EU ? amount * 0.92 : amount;
    }
}
