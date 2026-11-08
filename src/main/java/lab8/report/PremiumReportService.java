package lab8.report;

/** Рівень 3 ієрархії (DIT=3): 10 перевизначених методів + 10 доданих => PIM>20. */
public class PremiumReportService extends AuditedReportService {
    private final String watermark;
    private String theme = "light";

    public PremiumReportService(String watermark) { this.watermark = watermark; }

    @Override public String title() { return "Premium " + super.title(); }

    @Override public String header() { return "* " + super.header(); }

    @Override public String body() { return super.body() + separator() + "Summary: " + summary(); }

    @Override public String footer() { return super.footer() + " | " + signature(); }

    @Override public String format(String text) { return super.format(text).toUpperCase(); }

    @Override public String sanitize(String text) { return super.sanitize(text).replace("\t", "    "); }

    @Override public String separator() { return "\n\n"; }

    @Override public int maxWidth() { return 120; }

    @Override public String render() { return watermark + separator() + super.render(); }

    @Override public String export(String type) {
        return "md".equals(type) ? exportMarkdown() : super.export(type);
    }

    public String watermark() { return watermark; }

    public String signature() { return "Signed by " + watermark; }

    public String theme() { return theme; }

    public void setTheme(String theme) { this.theme = theme; }

    public String summary() { return "events=" + trail().size(); }

    public String tableOfContents() { return "1. " + title() + "\n2. Summary"; }

    public int pageCount() { return render().length() / 2000 + 1; }

    public String exportMarkdown() { return "```\n" + render() + "\n```"; }

    public String printPreview() { String r = render(); return r.substring(0, Math.min(r.length(), maxWidth())); }

    public String emailTo(String address) { return "mailto:" + address + "?body=" + render(); }
}
