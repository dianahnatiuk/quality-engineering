package lab4.service;

import lab4.domain.Order;
import lab4.domain.UserProfile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AnonymizationService {
    private final Map<String, UserProfile> userDb;
    private final List<Order> orderDb;

    public AnonymizationService(Map<String, UserProfile> userDb, List<Order> orderDb) {
        this.userDb = userDb;
        this.orderDb = orderDb;
    }

    public synchronized void executeErasureWorkflow(String actorId, String targetUserId) {
        if (!actorId.equals(targetUserId)) {
            throw new SecurityException("Forbidden: Cannot anonymize another user");
        }

        UserProfile user = userDb.get(targetUserId);
        if (user == null || user.isAnonymized()) {
            return; // Ідемпотентність
        }

        // 1. ANONYMIZE PII у головній таблиці
        String anonId = "anon-" + UUID.randomUUID().toString().substring(0, 8);
        user.setFullName("ANONYMIZED_USER");
        user.setEmail(anonId + "@deleted.local");
        user.setPhone("+000000000000");
        user.getSettings().clear();
        user.setAnonymized(true);

        // 2. RETAIN/LINK: Оновлення посилань в транзакційних записах (Order зберігається для фінансової звітності)
        orderDb.stream()
                .filter(o -> o.getUserId().equals(targetUserId))
                .forEach(o -> o.setCustomerNote("Order retained under Tax Policy. PII anonymized."));
    }
}