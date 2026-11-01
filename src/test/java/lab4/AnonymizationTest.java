package lab4;

import lab4.domain.Order;
import lab4.domain.UserProfile;
import lab4.service.AnonymizationService;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class AnonymizationTest {

    @Test
    void testErasureWorkflowAnonymizesUserAndRetainsOrders() {
        Map<String, UserProfile> userDb = new HashMap<>();
        List<Order> orderDb = new ArrayList<>();

        String userId = "usr-999";
        userDb.put(userId, new UserProfile(userId, "Alice Smith", "alice@example.com", "+380111111111"));
        orderDb.add(new Order("ord-1", userId, 150.0));

        AnonymizationService service = new AnonymizationService(userDb, orderDb);

        // Виконання анонімізації
        service.executeErasureWorkflow(userId, userId);

        UserProfile anonymizedUser = userDb.get(userId);
        assertTrue(anonymizedUser.isAnonymized());
        assertEquals("ANONYMIZED_USER", anonymizedUser.getFullName());
        assertTrue(anonymizedUser.getEmail().endsWith("@deleted.local"));

        // Перевірка збереження замовлення
        assertEquals(1, orderDb.size());
        assertTrue(orderDb.get(0).getCustomerNote().contains("Tax Policy"));
    }
}