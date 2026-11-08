package lab8.service;

import lab8.model.Order;
import lab8.model.Quote;
import lab8.port.AuditPort;
import lab8.port.CurrencyPolicy;
import lab8.port.FraudCheckPort;
import lab8.port.PaymentPort;
import lab8.port.PaymentProcessor;

import java.util.Objects;

/** Відповідальність №3: списання/повернення коштів з антифрод-перевіркою. */
public class PaymentService implements PaymentProcessor {
    private final FraudCheckPort fraud;
    private final PaymentPort gateway;
    private final AuditPort audit;
    private final CurrencyPolicy currency;

    public PaymentService(FraudCheckPort fraud, PaymentPort gateway, AuditPort audit, CurrencyPolicy currency) {
        this.fraud = Objects.requireNonNull(fraud);
        this.gateway = Objects.requireNonNull(gateway);
        this.audit = Objects.requireNonNull(audit);
        this.currency = Objects.requireNonNull(currency);
    }

    @Override public boolean charge(Order order, Quote quote) {
        if (fraud.isSuspicious(order, quote.total())) {
            audit.log("charge blocked (fraud) " + order.id());
            return false;
        }
        boolean ok = gateway.charge(order.paymentToken(), currency.convert(quote.total(), order.region()));
        audit.log("charge " + (ok ? "accepted " : "declined ") + order.id());
        return ok;
    }

    @Override public boolean refund(Order order, Quote quote) {
        if (fraud.isSuspicious(order, quote.total())) {
            audit.log("refund held for manual review " + order.id());
            return false;
        }
        boolean ok = gateway.refund(order.paymentToken(), currency.convert(quote.total(), order.region()));
        audit.log("refund " + (ok ? "issued " : "failed ") + order.id());
        return ok;
    }
}
