package lab8.legacy;

import lab8.model.Order;
import lab8.model.OrderLine;
import lab8.model.OrderResult;
import lab8.model.Region;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Фіксує поведінку God Object до рефакторингу (characterization test). */
class OrderProcessorCharacterizationTest {

    private static Order order(String sku, int qty, String coupon, String token) {
        return new Order("o1", "a@b.c", "+380000", Region.UA, false, List.of(new OrderLine(sku, qty)), coupon, token);
    }

    @Test void placesValidOrder() {
        OrderResult r = new OrderProcessor().placeOrder(order("A-1", 2, null, "tok"));
        assertTrue(r.success());
        assertEquals(247.0, r.quote().total(), 1e-9);
        assertEquals("INV-o1-247", r.invoiceId());
    }

    @Test void appliesCoupon() {
        assertEquals(223.0, new OrderProcessor().estimate(order("A-1", 2, "SAVE10", "tok")).total(), 1e-9);
    }

    @Test void rejectsOutOfStock() {
        OrderResult r = new OrderProcessor().placeOrder(order("C-3", 6, null, "tok"));
        assertFalse(r.success());
        assertEquals("out of stock", r.message());
    }

    @Test void rejectsFraudAndBadPayment() {
        assertFalse(new OrderProcessor().placeOrder(order("A-1", 1, null, "stolen")).success());
        assertFalse(new OrderProcessor().placeOrder(order("A-1", 1, null, "bad-card")).success());
    }

    @Test void cancelsOrder() {
        OrderResult r = new OrderProcessor().cancelOrder(order("A-1", 2, null, "tok"));
        assertTrue(r.success());
        assertEquals("CN-o1-247", r.invoiceId());
    }
}
