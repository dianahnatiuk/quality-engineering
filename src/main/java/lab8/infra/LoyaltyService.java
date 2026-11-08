package lab8.infra;

import lab8.port.LoyaltyPort;
import java.util.HashMap;
import java.util.Map;

public class LoyaltyService implements LoyaltyPort {
    private final Map<String, Integer> points = new HashMap<>();

    @Override public void addPoints(String email, int delta) {
        points.put(email, points.getOrDefault(email, 0) + delta);
    }

    public int points(String email) {
        return points.getOrDefault(email, 0);
    }
}
