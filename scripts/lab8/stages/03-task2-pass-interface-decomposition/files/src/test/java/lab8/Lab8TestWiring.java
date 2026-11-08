package lab8;

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
import lab8.service.CheckoutService;
import lab8.service.FulfillmentService;
import lab8.service.PaymentService;
import lab8.service.PricingService;
import lab8.service.StockService;

/** Composition root для тестів: єдине місце, де збираються конкретні реалізації. */
public final class Lab8TestWiring {
    public final InventoryRepository inventory = new InventoryRepository();
    public final AuditLogger audit = new AuditLogger();
    public final EmailSender email = new EmailSender();
    public final LoyaltyService loyalty = new LoyaltyService();
    public final PricingService pricing =
            new PricingService(new PriceCatalog(), new DiscountEngine(), new TaxCalculator(), new ShippingCalculator());
    public final CheckoutService checkout = new CheckoutService(
            new StockService(inventory, audit),
            pricing,
            new PaymentService(new FraudDetector(), new PaymentGateway(), audit, new CurrencyConverter()),
            new FulfillmentService(new InvoiceGenerator(), email, new SmsSender(), loyalty));
}
