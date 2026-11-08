package lab8.infra;

import lab8.port.MessageChannel;
import java.util.ArrayList;
import java.util.List;

public class EmailSender implements MessageChannel {
    private final List<String> outbox = new ArrayList<>();

    @Override public void send(String to, String subject, String body) {
        outbox.add(to + "|" + subject + "|" + body);
    }

    public List<String> outbox() {
        return List.copyOf(outbox);
    }
}
