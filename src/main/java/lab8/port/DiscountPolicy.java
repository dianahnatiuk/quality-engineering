package lab8.port;

public interface DiscountPolicy {
    double discount(String coupon, double subtotal);
}
