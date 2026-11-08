package lab8.report;

/** Контракт звіту: клієнти залежать від інтерфейсу, а не від ієрархії класів. */
public interface ReportService {
    String title();

    String render();

    String export(String type);

    String filename();
}
