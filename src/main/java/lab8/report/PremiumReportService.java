package lab8.report;

import java.util.Objects;

/** Було: extends AuditedReportService (DIT=3, 10 override). Стало: композиція + делегування (DIT=1, 0 override). */
public class PremiumReportService implements ReportService {
    private final ReportService delegate;
    private final String watermark;
    private String theme = "light";

    public PremiumReportService(ReportService delegate, String watermark) {
        this.delegate = Objects.requireNonNull(delegate);
        this.watermark = watermark;
    }

    @Override public String title() { return "Premium " + delegate.title(); }

    @Override public String render() { return watermark + "\n\n" + delegate.render() + " | " + signature(); }

    @Override public String export(String type) {
        return "md".equals(type) ? exportMarkdown() : delegate.export(type);
    }

    @Override public String filename() { return delegate.filename(); }

    public String watermark() { return watermark; }

    public String signature() { return "Signed by " + watermark; }

    public String theme() { return theme; }

    public void setTheme(String theme) { this.theme = theme; }

    public String tableOfContents() { return "1. " + title() + "\n2. Summary"; }

    public String exportMarkdown() { return "```\n" + render() + "\n```"; }
}
