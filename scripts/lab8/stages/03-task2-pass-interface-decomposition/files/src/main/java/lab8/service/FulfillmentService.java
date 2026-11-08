package lab8.service;

import lab8.model.Order;
import lab8.model.Quote;
import lab8.port.InvoicePort;
import lab8.port.LoyaltyPort;
import lab8.port.MessageChannel;
import lab8.port.OrderFulfiller;

import java.util.Objects;

/** Відповідальність №4: інвойс/кредит-нота, сповіщення, бали лояльності. */
public class FulfillmentService implements OrderFulfiller {
    private final InvoicePort invoices;
    private final MessageChannel email;
    private final MessageChannel sms;
    private final LoyaltyPort loyalty;

    public FulfillmentService(InvoicePort invoices, MessageChannel email, MessageChannel sms, LoyaltyPort loyalty) {
        this.invoices = Objects.requireNonNull(invoices);
        this.email = Objects.requireNonNull(email);
        this.sms = Objects.requireNonNull(sms);
        this.loyalty = Objects.requireNonNull(loyalty);
    }

    @Override public String confirm(Order order, Quote quote) {
        String invoiceId = invoices.generate(order, quote.total());
        email.send(order.email(), "Order " + order.id(), "Order confirmed: " + invoiceId);
        sms.send(order.phone(), "Order " + order.id(), "Order confirmed: " + invoiceId);
        loyalty.addPoints(order.email(), points(quote));
        return invoiceId;
    }

    @Override public String cancel(Order order, Quote quote) {
        String creditNote = invoices.generate(order, -quote.total());
        email.send(order.email(), "Order " + order.id(), "Order cancelled: " + creditNote);
        sms.send(order.phone(), "Order " + order.id(), "Order cancelled: " + creditNote);
        loyalty.addPoints(order.email(), -points(quote));
        return creditNote;
    }

    private static int points(Quote quote) {
        return (int) (quote.total() / 10);
    }
}
