package lab8.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Було: extends BaseReportService. Стало: приватне поле-делегат, що передається через конструктор (DI). */
public class AuditedReportService implements ReportService {
    private final ReportService delegate;
    private final List<String> trail = new ArrayList<>();

    public AuditedReportService(ReportService delegate) { this.delegate = Objects.requireNonNull(delegate); }

    @Override public String title() { return "Audited " + delegate.title(); }

    @Override public String render() { track("render"); return delegate.render() + " [audited]"; }

    @Override public String export(String type) { track("export:" + type); return delegate.export(type); }

    @Override public String filename() { return delegate.filename(); }

    public void track(String event) { trail.add(event); }

    public List<String> trail() { return List.copyOf(trail); }

    public String auditSummary() { return trail.size() + " events: " + String.join(",", trail); }
}
