package lab4;

import lab4.logging.PiiSanitizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SecureLoggingTest {

    @Test
    void testSecureLoggingSanitizesPIIAndSecrets() {
        String logInput = "User test.user@example.com logged in with phone +380971234567 and payload {\"password\":\"superSecret123\"}";
        String sanitized = PiiSanitizer.sanitize(logInput);

        assertFalse(sanitized.contains("test.user@example.com"));
        assertFalse(sanitized.contains("+380971234567"));
        assertFalse(sanitized.contains("superSecret123"));
        assertTrue(sanitized.contains("[REDACTED]"));
    }
}