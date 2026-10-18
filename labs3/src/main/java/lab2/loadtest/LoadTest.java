package lab2.loadtest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Легкий інструмент навантажувального тестування (аналог k6/JMeter/Gatling) на чистому JDK 21
 * (virtual threads). Сам обчислює p50/p90/p95/p99, RPS, Error Rate і виносить вердикт PASS/FAIL.
 *
 * <p>Використання (аргументи: VUs, тривалість у секундах, мітка прогону):
 * <pre>
 *   java -cp target/classes lab2.loadtest.LoadTest 8 5 normal
 *   java -cp target/classes lab2.loadtest.LoadTest 100 5 increased
 * </pre>
 *
 * <p>SLO: p95 &lt;= 250 ms, Error Rate &lt; 1 %.
 *
 * <p>РЕФАКТОРИНГ (Лаб. №3): колишній метод {@code run()} (Cognitive 25, Cyclomatic 11) розділено на
 * три відповідальності — генерація навантаження, підсумок метрик, друк звіту. Публічний API
 * ({@code run}, {@code Result}, {@code main}) та формат виводу не змінилися.
 */
public final class LoadTest {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9000;
    private static final double P95_THRESHOLD_MS = 250.0;
    private static final double ERROR_RATE_THRESHOLD = 0.01;
    /** Пауза між ітераціями одного VU (think-time), ~10 ітерацій/с на кожен VU. */
    private static final long ITERATION_PACING_MS = 100;

    public static void main(String[] args) throws Exception {
        int vus = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int durationS = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        String label = args.length > 2 ? args[2] : "run";
        run(vus, durationS, label);
    }

    /** Виконує прогін, друкує звіт і повертає підсумок. */
    public static Result run(int vus, int durationS, String label) throws InterruptedException {
        Measurements measurements = generateLoad(vus, durationS);
        Result result = summarize(label, vus, measurements);
        printReport(result, durationS);
        return result;
    }

    // ---------------------------------------------------------------- 1. генерація навантаження

    private static Measurements generateLoad(int vus, int durationS) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        Recorder recorder = new Recorder();
        long stopAt = System.currentTimeMillis() + durationS * 1000L;

        long startedAt = System.currentTimeMillis();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int v = 0; v < vus; v++) {
                pool.submit(() -> runVirtualUser(client, recorder, stopAt));
            }
        } // close() очікує завершення всіх віртуальних користувачів
        double totalTimeS = (System.currentTimeMillis() - startedAt) / 1000.0;

        return new Measurements(recorder.latenciesMs, recorder.errors.get(), totalTimeS);
    }

    private static void runVirtualUser(HttpClient client, Recorder recorder, long stopAt) {
        while (System.currentTimeMillis() < stopAt) {
            long startedAt = System.nanoTime();
            boolean ok = sendRequest(client, recorder.nextRequestId());
            double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0;

            recorder.record(elapsedMs, ok);
            pauseForThinkTime(elapsedMs);
        }
    }

    private static boolean sendRequest(HttpClient client, int requestId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + HOST + ":" + PORT + "/api/order/" + requestId))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private static void pauseForThinkTime(double elapsedMs) {
        long remaining = ITERATION_PACING_MS - (long) elapsedMs;
        if (remaining <= 0) {
            return; // guard clause: запит вже тривав довше за інтервал
        }
        try {
            Thread.sleep(remaining);
        } catch (InterruptedException ignored) {
            // повторюємо поведінку оригіналу: перерване очікування не зупиняє тест
        }
    }

    // ---------------------------------------------------------------- 2. підсумок метрик

    static Result summarize(String label, int vus, Measurements measurements) {
        List<Double> sorted = new ArrayList<>(measurements.latenciesMs());
        Collections.sort(sorted);

        int n = sorted.size();
        double errorRate = errorRate(measurements.errorCount(), n);
        double p95 = percentile(sorted, 95);

        return new Result(label, vus, n,
                throughput(n, measurements.totalTimeS()),
                average(sorted),
                percentile(sorted, 50),
                percentile(sorted, 90),
                p95,
                percentile(sorted, 99),
                errorRate,
                meetsSlo(p95, errorRate));
    }

    static boolean meetsSlo(double p95Ms, double errorRate) {
        return p95Ms <= P95_THRESHOLD_MS && errorRate < ERROR_RATE_THRESHOLD;
    }

    static double errorRate(int errors, int total) {
        if (total == 0) {
            return 1.0; // порожній прогін вважається повною відмовою
        }
        return (double) errors / total;
    }

    static double throughput(int requests, double seconds) {
        if (seconds <= 0) {
            return 0;
        }
        return requests / seconds;
    }

    static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    static double percentile(List<Double> sortedData, double p) {
        if (sortedData.isEmpty()) {
            return 0.0;
        }
        double k = (sortedData.size() - 1) * (p / 100.0);
        int lower = (int) k;
        int upper = Math.min(lower + 1, sortedData.size() - 1);
        if (lower == upper) {
            return sortedData.get(lower);
        }
        return sortedData.get(lower) + (sortedData.get(upper) - sortedData.get(lower)) * (k - lower);
    }

    // ---------------------------------------------------------------- 3. звіт

    private static void printReport(Result r, int durationS) {
        System.out.printf("%n===== LOAD TEST: %s (VUs=%d, duration=%ds) =====%n", r.label(), r.vus(), durationS);
        System.out.printf("Requests total : %d%n", r.n());
        System.out.printf("RPS            : %.1f%n", r.rps());
        System.out.printf("avg latency    : %.1f ms%n", r.avg());
        System.out.printf("p50            : %.1f ms%n", r.p50());
        System.out.printf("p90            : %.1f ms%n", r.p90());
        System.out.printf("p95            : %.1f ms   (threshold <= %.0f ms)%n", r.p95(), P95_THRESHOLD_MS);
        System.out.printf("p99            : %.1f ms%n", r.p99());
        System.out.printf("Error Rate     : %.2f %%   (threshold < %.0f %%)%n",
                r.errorRate() * 100, ERROR_RATE_THRESHOLD * 100);
        System.out.printf("RESULT         : %s%n", r.passed() ? "PASS" : "FAIL");
        System.out.println("=".repeat(60));
    }

    // ---------------------------------------------------------------- типи даних

    /** Потокобезпечний збирач вимірювань (Parameter Object замість п'яти розрізнених змінних). */
    private static final class Recorder {
        final List<Double> latenciesMs = new CopyOnWriteArrayList<>();
        final AtomicInteger errors = new AtomicInteger();
        private final AtomicInteger requestIds = new AtomicInteger();

        int nextRequestId() {
            return requestIds.incrementAndGet();
        }

        void record(double elapsedMs, boolean ok) {
            latenciesMs.add(elapsedMs);
            if (!ok) {
                errors.incrementAndGet();
            }
        }
    }

    /** Сирі результати одного прогону. */
    record Measurements(List<Double> latenciesMs, int errorCount, double totalTimeS) { }

    public record Result(String label, int vus, int n, double rps, double avg,
                         double p50, double p90, double p95, double p99,
                         double errorRate, boolean passed) { }
}
