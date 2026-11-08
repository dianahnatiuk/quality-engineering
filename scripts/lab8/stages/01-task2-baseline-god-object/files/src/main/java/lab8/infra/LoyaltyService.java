package lab8.infra;

import java.util.HashMap;
import java.util.Map;

public class LoyaltyService {
    private final Map<String, Integer> points = new HashMap<>();

    public void addPoints(String email, int delta) {
        points.put(email, points.getOrDefault(email, 0) + delta);
    }

    public int points(String email) {
        return points.getOrDefault(email, 0);
    }
}
