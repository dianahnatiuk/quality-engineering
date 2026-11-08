package lab8.port;

public interface MessageChannel {
    void send(String to, String subject, String body);
}
