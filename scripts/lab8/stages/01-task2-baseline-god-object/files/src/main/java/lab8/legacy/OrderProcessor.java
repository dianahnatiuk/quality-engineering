package lab8.legacy;

import lab8.infra.AuditLogger;
import lab8.infra.CurrencyConverter;
import lab8.infra.DiscountEngine;
import lab8.infra.EmailSender;
import lab8.infra.FraudDetector;
import lab8.infra.InventoryRepository;
import lab8.infra.InvoiceGenerator;
import lab8.infra.LoyaltyService;
import lab8.infra.PaymentGateway;
import lab8.infra.PriceCatalog;
import lab8.infra.ShippingCalculator;
import lab8.infra.SmsSender;
import lab8.infra.TaxCalculator;
import lab8.model.Order;
import lab8.model.OrderLine;
import lab8.model.OrderResult;
import lab8.model.Quote;

/**
 * GOD OBJECT (антипатерн): склад, ціноутворення, антифрод, оплата, інвойси, нотифікації,
 * лояльність і аудит — в одному класі. Усі залежності створюються через new (жорстка «привареність»).
 */
public class OrderProcessor {
    private final InventoryRepository inventory = new InventoryRepository();
    private final PriceCatalog catalog = new PriceCatalog();
    private final DiscountEngine discounts = new DiscountEngine();
    private final TaxCalculator tax = new TaxCalculator();
    private final ShippingCalculator shipping = new ShippingCalculator();
    private final CurrencyConverter currency = new CurrencyConverter();
    private final PaymentGateway gateway = new PaymentGateway();
    private final FraudDetector fraud = new FraudDetector();
    private final EmailSender email = new EmailSender();
    private final SmsSender sms = new SmsSender();
    private final AuditLogger audit = new AuditLogger();
    private final InvoiceGenerator invoices = new InvoiceGenerator();
    private final LoyaltyService loyalty = new LoyaltyService();

    public OrderResult placeOrder(Order order) {
        Quote quote = quote(order);
        if (!reserveStock(order)) {
            return OrderResult.failed("out of stock");
        }
        if (!chargeCustomer(order, quote)) {
            releaseStock(order);
            return OrderResult.failed("payment declined");
        }
        String invoiceId = invoices.generate(order, quote.total());
        notifyCustomer(order, "Order confirmed: " + invoiceId);
        loyalty.addPoints(order.email(), (int) (quote.total() / 10));
        audit.log("order placed " + order.id());
        return OrderResult.ok(quote, invoiceId);
    }

    public OrderResult cancelOrder(Order order) {
        Quote quote = quote(order);
        releaseStock(order);
        double taxRefund = tax.calculate(quote.subtotal() - quote.discount());
        if (!refundCustomer(order, quote)) {
            return OrderResult.failed("refund held");
        }
        String creditNote = invoices.generate(order, -quote.total());
        notifyCustomer(order, "Order cancelled: " + creditNote + " (tax refunded " + taxRefund + ")");
        loyalty.addPoints(order.email(), -(int) (quote.total() / 10));
        audit.log("order cancelled " + order.id());
        return OrderResult.ok(quote, creditNote);
    }

    public Quote estimate(Order order) {
        return quote(order);
    }

    public double taxPreview(Order order) {
        double subtotal = subtotal(order);
        return tax.calculate(subtotal - discounts.discount(order.coupon(), subtotal));
    }

    private double subtotal(Order order) {
        double subtotal = 0;
        for (OrderLine line : order.lines()) subtotal += catalog.priceOf(line.sku()) * line.qty();
        return subtotal;
    }

    private Quote quote(Order order) {
        double subtotal = subtotal(order);
        double discount = discounts.discount(order.coupon(), subtotal);
        double taxAmount = tax.calculate(subtotal - discount);
        double ship = shipping.cost(order.region(), order.itemCount());
        return new Quote(subtotal, discount, taxAmount, ship, subtotal - discount + taxAmount + ship);
    }

    private boolean reserveStock(Order order) {
        java.util.List<OrderLine> lines = order.lines();
        for (int i = 0; i < lines.size(); i++) {
            OrderLine line = lines.get(i);
            if (!inventory.reserve(line.sku(), line.qty())) {
                for (int j = 0; j < i; j++) inventory.release(lines.get(j).sku(), lines.get(j).qty());
                audit.log("stock reservation failed for " + order.id());
                return false;
            }
        }
        audit.log("stock reserved for " + order.id());
        return true;
    }

    private void releaseStock(Order order) {
        for (OrderLine line : order.lines()) inventory.release(line.sku(), line.qty());
        audit.log("stock released for " + order.id());
    }

    private boolean chargeCustomer(Order order, Quote quote) {
        if (fraud.isSuspicious(order, quote.total())) {
            audit.log("charge blocked (fraud) " + order.id());
            return false;
        }
        boolean ok = gateway.charge(order.paymentToken(), currency.convert(quote.total(), order.region()));
        audit.log("charge " + (ok ? "accepted " : "declined ") + order.id());
        return ok;
    }

    private boolean refundCustomer(Order order, Quote quote) {
        if (fraud.isSuspicious(order, quote.total())) {
            audit.log("refund held for manual review " + order.id());
            return false;
        }
        boolean ok = gateway.refund(order.paymentToken(), currency.convert(quote.total(), order.region()));
        audit.log("refund " + (ok ? "issued " : "failed ") + order.id());
        return ok;
    }

    private void notifyCustomer(Order order, String text) {
        email.send(order.email(), "Order " + order.id(), text);
        sms.send(order.phone(), "Order " + order.id(), text);
    }
}
