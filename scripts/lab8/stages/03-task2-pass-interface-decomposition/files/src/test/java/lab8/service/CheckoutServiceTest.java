package lab8.service;

import lab8.Lab8TestWiring;
import lab8.model.Order;
import lab8.model.OrderLine;
import lab8.model.OrderResult;
import lab8.model.Region;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Та сама поведінка, що й у characterization-тесті God Object, але через DI-граф. */
class CheckoutServiceTest {

    private static Order order(String sku, int qty, String coupon, String token) {
        return new Order("o1", "a@b.c", "+380000", Region.UA, false, List.of(new OrderLine(sku, qty)), coupon, token);
    }

    @Test void placesValidOrder() {
        Lab8TestWiring w = new Lab8TestWiring();
        OrderResult r = w.checkout.placeOrder(order("A-1", 2, null, "tok"));
        assertTrue(r.success());
        assertEquals(247.0, r.quote().total(), 1e-9);
        assertEquals("INV-o1-247", r.invoiceId());
        assertEquals(98, w.inventory.available("A-1"));
        assertEquals(24, w.loyalty.points("a@b.c"));
    }

    @Test void outOfStockTouchesNothing() {
        Lab8TestWiring w = new Lab8TestWiring();
        OrderResult r = w.checkout.placeOrder(order("C-3", 6, null, "tok"));
        assertFalse(r.success());
        assertEquals("out of stock", r.message());
        assertEquals(5, w.inventory.available("C-3"));
    }

    @Test void failedPaymentReleasesStock() {
        Lab8TestWiring w = new Lab8TestWiring();
        assertFalse(w.checkout.placeOrder(order("A-1", 3, null, "bad-card")).success());
        assertFalse(w.checkout.placeOrder(order("A-1", 3, null, "stolen")).success());
        assertEquals(100, w.inventory.available("A-1"));
    }

    @Test void cancelIssuesCreditNote() {
        OrderResult r = new Lab8TestWiring().checkout.cancelOrder(order("A-1", 2, null, "tok"));
        assertTrue(r.success());
        assertEquals("CN-o1-247", r.invoiceId());
    }
}
