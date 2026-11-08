package lab8.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportCompositionTest {

    private static PremiumReportService premium(AuditedReportService audited) {
        return new PremiumReportService(audited, "ACME");
    }

    @Test void behaviourIsDelegatedThroughTheChain() {
        AuditedReportService audited = new AuditedReportService(new ReportEngine());
        PremiumReportService report = premium(audited);
        String out = report.render();
        assertTrue(out.startsWith("ACME"));
        assertTrue(out.contains("[audited]"));
        assertEquals(java.util.List.of("render"), audited.trail());
        assertTrue(report.export("md").startsWith("```"));
        assertEquals("report.txt", report.filename());
    }

    /** Архітектурна «сигналізація»: повернення до наслідування зламає тест. */
    @Test void noInheritanceBetweenReportClasses() {
        for (Class<?> c : new Class<?>[]{ReportEngine.class, AuditedReportService.class, PremiumReportService.class}) {
            assertEquals(Object.class, c.getSuperclass());
        }
    }
}
