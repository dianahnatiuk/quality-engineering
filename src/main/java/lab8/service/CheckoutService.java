package lab8.service;

import lab8.model.Order;
import lab8.model.OrderResult;
import lab8.model.Quote;
import lab8.port.OrderFulfiller;
import lab8.port.OrderPricer;
import lab8.port.PaymentProcessor;
import lab8.port.StockReserver;

import java.util.Objects;

/** Тонкий оркестратор: знає лише ІНТЕРФЕЙСИ чотирьох сервісів (DIP), залежності — через конструктор (DI). */
public class CheckoutService {
    private final StockReserver stock;
    private final OrderPricer pricer;
    private final PaymentProcessor payments;
    private final OrderFulfiller fulfiller;

    public CheckoutService(StockReserver stock, OrderPricer pricer, PaymentProcessor payments, OrderFulfiller fulfiller) {
        this.stock = Objects.requireNonNull(stock);
        this.pricer = Objects.requireNonNull(pricer);
        this.payments = Objects.requireNonNull(payments);
        this.fulfiller = Objects.requireNonNull(fulfiller);
    }

    public OrderResult placeOrder(Order order) {
        Quote quote = pricer.quote(order);
        if (!stock.reserve(order)) return OrderResult.failed("out of stock");
        if (!payments.charge(order, quote)) {
            stock.release(order);
            return OrderResult.failed("payment declined");
        }
        return OrderResult.ok(quote, fulfiller.confirm(order, quote));
    }

    public OrderResult cancelOrder(Order order) {
        Quote quote = pricer.quote(order);
        stock.release(order);
        if (!payments.refund(order, quote)) return OrderResult.failed("refund held");
        return OrderResult.ok(quote, fulfiller.cancel(order, quote));
    }
}
