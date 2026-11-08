package lab8.report;

import java.util.ArrayList;
import java.util.List;

/** Рівень 2 ієрархії (DIT=2): перевизначає 6 методів батька. */
public class AuditedReportService extends BaseReportService {
    private final List<String> trail = new ArrayList<>();

    @Override public String title() { return "Audited " + super.title(); }

    @Override public String header() { track("header"); return super.header(); }

    @Override public String body() { track("body"); return super.body(); }

    @Override public String footer() { track("footer"); return super.footer() + " [audited]"; }

    @Override public String format(String text) { return "[A] " + super.format(text); }

    @Override public String render() { track("render"); return super.render(); }

    public void track(String event) { trail.add(event); }

    public List<String> trail() { return List.copyOf(trail); }

    public String auditSummary() { return trail.size() + " events: " + String.join(",", trail); }
}
