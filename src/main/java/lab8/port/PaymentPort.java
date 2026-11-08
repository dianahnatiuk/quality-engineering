package lab8.port;

public interface PaymentPort {
    boolean charge(String token, double amount);

    boolean refund(String token, double amount);
}
