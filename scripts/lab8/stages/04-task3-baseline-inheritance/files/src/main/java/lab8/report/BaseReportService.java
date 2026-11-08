package lab8.report;

/** Рівень 1 ієрархії (DIT=1). */
public class BaseReportService {

    public String title() { return "Report"; }

    public String header() { return format("== " + title() + " =="); }

    public String body() { return format("(no data)"); }

    public String footer() { return format("-- end of report --"); }

    public String format(String text) { return sanitize(text).trim(); }

    public String sanitize(String text) {
        return text == null ? "" : text.replace("<", "&lt;").replace(">", "&gt;");
    }

    public String separator() { return "\n"; }

    public int maxWidth() { return 80; }

    public String render() { return header() + separator() + body() + separator() + footer(); }

    public String export(String type) {
        if ("txt".equals(type)) return render();
        if ("html".equals(type)) return "<pre>" + render() + "</pre>";
        throw new IllegalArgumentException("unsupported export type: " + type);
    }

    public String filename() { return title().toLowerCase().replace(' ', '_') + ".txt"; }

    public boolean isEmpty() { return body().isBlank(); }
}
