package lab8.service;

import lab8.model.Order;
import lab8.model.OrderLine;
import lab8.model.Quote;
import lab8.port.DiscountPolicy;
import lab8.port.OrderPricer;
import lab8.port.PriceCatalogPort;
import lab8.port.ShippingPolicy;
import lab8.port.TaxPolicy;

import java.util.Objects;

/** Відповідальність №2: ціноутворення (каталог, знижки, податок, доставка). */
public class PricingService implements OrderPricer {
    private final PriceCatalogPort catalog;
    private final DiscountPolicy discounts;
    private final TaxPolicy tax;
    private final ShippingPolicy shipping;

    public PricingService(PriceCatalogPort catalog, DiscountPolicy discounts, TaxPolicy tax, ShippingPolicy shipping) {
        this.catalog = Objects.requireNonNull(catalog);
        this.discounts = Objects.requireNonNull(discounts);
        this.tax = Objects.requireNonNull(tax);
        this.shipping = Objects.requireNonNull(shipping);
    }

    @Override public Quote quote(Order order) {
        double subtotal = 0;
        for (OrderLine line : order.lines()) subtotal += catalog.priceOf(line.sku()) * line.qty();
        double discount = discounts.discount(order.coupon(), subtotal);
        double taxable = subtotal - discount;
        double taxAmount = tax.calculate(taxable, order.region(), order.business());
        double ship = shipping.cost(order.region(), order.itemCount());
        return new Quote(subtotal, discount, taxAmount, ship, taxable + taxAmount + ship);
    }
}
