package lab8.report;

import java.util.List;

/** Рівень 1 ієрархії. ЗМІНА ДЛЯ КРАШ-ТЕСТУ: render() тепер повертає List<String> (рядки звіту). */
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

    public List<String> render() { return List.of(header(), body(), footer()); }

    public String export(String type) {
        String text = String.join(separator(), render());
        if ("txt".equals(type)) return text;
        if ("html".equals(type)) return "<pre>" + text + "</pre>";
        throw new IllegalArgumentException("unsupported export type: " + type);
    }

    public String filename() { return title().toLowerCase().replace(' ', '_') + ".txt"; }

    public boolean isEmpty() { return body().isBlank(); }
}
