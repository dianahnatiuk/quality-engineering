package lab4;

import lab4.service.ConsentEngine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ConsentEngineTest {

    @Test
    void testConsentPolicyGateWorkflow() {
        ConsentEngine engine = new ConsentEngine();
        String userId = "user-777";

        // Initial: DENY
        assertFalse(engine.canExecuteOperation(userId, ConsentEngine.Purpose.MARKETING_EMAIL));

        // GRANT -> ALLOW
        engine.grantConsent(userId, ConsentEngine.Purpose.MARKETING_EMAIL, "v1.0");
        assertTrue(engine.canExecuteOperation(userId, ConsentEngine.Purpose.MARKETING_EMAIL));

        // REVOKE -> DENY
        engine.revokeConsent(userId, ConsentEngine.Purpose.MARKETING_EMAIL);
        assertFalse(engine.canExecuteOperation(userId, ConsentEngine.Purpose.MARKETING_EMAIL));
    }
}