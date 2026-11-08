package lab8.infra;

import java.util.ArrayList;
import java.util.List;

public class EmailSender {
    private final List<String> outbox = new ArrayList<>();

    public void send(String to, String subject, String body) {
        outbox.add(to + "|" + subject + "|" + body);
    }

    public List<String> outbox() {
        return List.copyOf(outbox);
    }
}
