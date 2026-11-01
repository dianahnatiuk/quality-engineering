package lab4.service;

import java.time.Instant;
import java.util.*;

public class ConsentEngine {
    public enum Purpose { MARKETING_EMAIL, ANALYTICS }

    public static record ConsentRecord(String userId, Purpose purpose, boolean isGranted, String policyVersion, Instant timestamp) {}

    private final Map<String, Map<Purpose, ConsentRecord>> consentStore = new HashMap<>();

    public void grantConsent(String userId, Purpose purpose, String policyVersion) {
        consentStore.computeIfAbsent(userId, k -> new HashMap<>())
                .put(purpose, new ConsentRecord(userId, purpose, true, policyVersion, Instant.now()));
    }

    public void revokeConsent(String userId, Purpose purpose) {
        if (consentStore.containsKey(userId) && consentStore.get(userId).containsKey(purpose)) {
            ConsentRecord old = consentStore.get(userId).get(purpose);
            consentStore.get(userId).put(purpose, new ConsentRecord(userId, purpose, false, old.policyVersion(), Instant.now()));
        }
    }

    // Policy Gate
    public boolean canExecuteOperation(String userId, Purpose purpose) {
        ConsentRecord record = consentStore.getOrDefault(userId, Collections.emptyMap()).get(purpose);
        return record != null && record.isGranted();
    }
}