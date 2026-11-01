package lab4.service;

import lab4.domain.UserProfile;
import java.util.*;

public class DataExportService {
    private final Map<String, UserProfile> userDb = new HashMap<>();

    public void saveUser(UserProfile profile) { userDb.put(profile.getId(), profile); }

    public Map<String, Object> exportPersonalData(String authenticatedSubjectId, String targetSubjectId) {
        // Контроль доступу (Захист від IDOR)
        if (!authenticatedSubjectId.equals(targetSubjectId)) {
            throw new SecurityException("Access Denied: Subject mismatch");
        }

        UserProfile user = userDb.get(targetSubjectId);
        if (user == null) {
            throw new NoSuchElementException("User not found");
        }

        // Агрегація даних із застосуванням мінімізації (БЕЗ паролів та секретів)
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportMetadata", Map.of("version", "1.0", "timestamp", System.currentTimeMillis()));
        export.put("subjectId", user.getId());

        Map<String, Object> profileData = new HashMap<>();
        profileData.put("fullName", user.getFullName());
        profileData.put("email", user.getEmail());
        profileData.put("phone", user.getPhone());

        export.put("profile", profileData);
        export.put("settings", user.getSettings());
        export.put("consents", user.getConsents());

        return export;
    }
}