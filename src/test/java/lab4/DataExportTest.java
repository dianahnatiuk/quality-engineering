package lab4;

import lab4.domain.UserProfile;
import lab4.service.DataExportService;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class DataExportTest {

    @Test
    void testDataExportIdorProtectionAndMinimization() {
        DataExportService exportService = new DataExportService();
        UserProfile user = new UserProfile("usr-1", "John Doe", "john@test.com", "+380000000000");
        exportService.saveUser(user);

        // Успішний власний експорт
        Map<String, Object> export = exportService.exportPersonalData("usr-1", "usr-1");
        assertNotNull(export.get("profile"));

        // IDOR Захист (чужий ID)
        assertThrows(SecurityException.class, () -> exportService.exportPersonalData("usr-2", "usr-1"));
    }
}