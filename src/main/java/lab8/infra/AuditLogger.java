package lab8.infra;

import lab8.port.AuditPort;
import java.util.ArrayList;
import java.util.List;

public class AuditLogger implements AuditPort {
    private final List<String> events = new ArrayList<>();

    @Override public void log(String event) {
        events.add(event);
    }

    public List<String> events() {
        return List.copyOf(events);
    }
}
