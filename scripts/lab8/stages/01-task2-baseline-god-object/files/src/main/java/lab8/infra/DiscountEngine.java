package lab8.infra;


public class DiscountEngine {
    public double discount(String coupon, double subtotal) {
        if ("SAVE10".equals(coupon)) return subtotal * 0.10;
        if ("HALF".equals(coupon)) return subtotal * 0.50;
        return 0.0;
    }
}
