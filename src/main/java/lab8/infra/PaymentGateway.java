package lab8.infra;

import java.util.ArrayList;
import java.util.List;

public class PaymentGateway {
    private final List<String> charges = new ArrayList<>();

    public boolean charge(String token, double amount) {
        boolean ok = token != null && !token.startsWith("bad") && amount > 0;
        if (ok) charges.add(token + ":" + amount);
        return ok;
    }

    public boolean refund(String token, double amount) {
        return token != null && amount > 0;
    }

    public List<String> charges() {
        return List.copyOf(charges);
    }
}
