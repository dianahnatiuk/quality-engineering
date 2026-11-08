package lab8.report;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Фіксує поведінку ієрархії Base -> Audited -> Premium до експерименту. */
class ReportHierarchyCharacterizationTest {

    @Test void premiumRendersThroughWholeChain() {
        PremiumReportService report = new PremiumReportService("ACME");
        String out = report.render();
        assertTrue(out.startsWith("ACME"));
        assertTrue(out.contains("PREMIUM AUDITED REPORT"));
        assertTrue(out.contains("[audited]"));
        assertTrue(report.trail().contains("render"));
    }

    @Test void exportsMarkdownAndHtml() {
        PremiumReportService report = new PremiumReportService("ACME");
        assertTrue(report.export("md").startsWith("```"));
        assertTrue(report.export("html").startsWith("<pre>"));
        assertEquals("premium_audited_report.txt", report.filename());
    }
}
