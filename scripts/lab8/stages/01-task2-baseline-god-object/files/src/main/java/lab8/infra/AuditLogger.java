package lab8.infra;

import java.util.ArrayList;
import java.util.List;

public class AuditLogger {
    private final List<String> events = new ArrayList<>();

    public void log(String event) {
        events.add(event);
    }

    public List<String> events() {
        return List.copyOf(events);
    }
}
