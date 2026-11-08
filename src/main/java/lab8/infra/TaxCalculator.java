package lab8.infra;

import lab8.model.Region;

/** Податок: нова бізнес-вимога — ставка за регіоном, B2B без ПДВ. */
public class TaxCalculator {
    public double calculate(double taxable, Region region, boolean business) {
        if (business) return 0.0;
        double rate = region == Region.UA ? 0.20 : region == Region.EU ? 0.21 : 0.08;
        return taxable * rate;
    }
}
