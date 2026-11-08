package lab8.infra;

import lab8.port.PaymentPort;
import java.util.ArrayList;
import java.util.List;

public class PaymentGateway implements PaymentPort {
    private final List<String> charges = new ArrayList<>();

    @Override public boolean charge(String token, double amount) {
        boolean ok = token != null && !token.startsWith("bad") && amount > 0;
        if (ok) charges.add(token + ":" + amount);
        return ok;
    }

    @Override public boolean refund(String token, double amount) {
        return token != null && amount > 0;
    }

    public List<String> charges() {
        return List.copyOf(charges);
    }
}
