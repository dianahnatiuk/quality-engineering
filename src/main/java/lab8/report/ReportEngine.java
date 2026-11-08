package lab8.report;

import java.util.List;

/** Колишній BaseReportService: тепер звичайний клас без предків (DIT=1) і без публічних «гачків» для перевизначення. */
public class ReportEngine implements ReportService {

    @Override public String title() { return "Report"; }

    @Override public String render() { return String.join("\n", lines()); }

    @Override public String export(String type) {
        if ("txt".equals(type)) return render();
        if ("html".equals(type)) return "<pre>" + render() + "</pre>";
        throw new IllegalArgumentException("unsupported export type: " + type);
    }

    @Override public String filename() { return title().toLowerCase().replace(' ', '_') + ".txt"; }

    private List<String> lines() {
        return List.of(
                format("== " + title() + " ==", 80),
                format("(no data)", 80),
                format("-- end of report --", 80));
    }

    private static String format(String text, int width) {
        String clean = text == null ? "" : text.replace("<", "&lt;").replace(">", "&gt;").trim();
        return clean.length() > width ? clean.substring(0, width) : clean;
    }
}
