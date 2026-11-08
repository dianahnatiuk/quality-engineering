package lab8.service;

import lab8.model.Order;
import lab8.model.OrderLine;
import lab8.model.Quote;
import lab8.model.Region;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Демонстрація переваги DI: PricingService тестується на лямбда-заглушках портів, без реальної інфраструктури. */
class PricingServiceTest {

    @Test void quoteUsesOnlyContracts() {
        PricingService pricing = new PricingService(
                sku -> 100.0,
                (coupon, subtotal) -> 0.0,
                (taxable, region, business) -> taxable * 0.1,
                (region, items) -> 5.0);
        Order order = new Order("o", "e", "p", Region.EU, false, List.of(new OrderLine("X", 2)), null, "t");
        Quote q = pricing.quote(order);
        assertEquals(200.0, q.subtotal(), 1e-9);
        assertEquals(20.0, q.tax(), 1e-9);
        assertEquals(225.0, q.total(), 1e-9);
    }

    @Test void businessCustomersPayNoTaxWithRealPolicy() {
        PricingService pricing = new PricingService(
                sku -> 100.0, (c, s) -> 0.0, new lab8.infra.TaxCalculator(), (r, n) -> 0.0);
        Order b2b = new Order("o", "e", "p", Region.UA, true, List.of(new OrderLine("X", 1)), null, "t");
        assertEquals(0.0, pricing.quote(b2b).tax(), 1e-9);
    }
}
