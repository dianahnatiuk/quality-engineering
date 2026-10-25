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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Легкий інструмент навантажувального тестування (аналог k6/JMeter/Gatling),
 * реалізований на чистому JDK 21 (virtual threads), без зовнішніх
 * залежностей — важливо, оскільки середовище виконання може не мати
 * доступу до мережі для встановлення k6/JMeter. Запускається однією
 * командою, не потребує ручного підрахунку результатів: сам обчислює
 * p50/p90/p95/p99, RPS, Error Rate і виносить вердикт PASS/FAIL.
 */

public final class LoadTest {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9000;
    private static final double P95_THRESHOLD_MS = 250.0;
    private static final double ERROR_RATE_THRESHOLD = 0.01;
    /** Пауза між ітераціями одного VU (think-time), як у k6/JMeter, щоб
     * модель навантаження відповідала реалістичному профілю запитів, а не
     * "hammer as fast as possible". ~10 ітерацій/с на кожен VU. */
    private static final long ITERATION_PACING_MS = 100;

    public static void main(String[] args) throws Exception {
        int vus = args.length > 0 ? Integer.parseInt(args[0]) : 8;
        int durationS = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        String label = args.length > 2 ? args[2] : "run";
        run(vus, durationS, label);
    }

    public static Result run(int vus, int durationS, String label) throws InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        List<Double> latencies = new CopyOnWriteArrayList<>();
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger requestId = new AtomicInteger(0);

        long stopAt = System.currentTimeMillis() + durationS * 1000L;
        CountDownLatch latch = new CountDownLatch(vus);

        long t0 = System.currentTimeMillis();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int v = 0; v < vus; v++) {
                pool.submit(() -> {
                    try {
                        while (System.currentTimeMillis() < stopAt) {
                            int id = requestId.incrementAndGet();
                            long start = System.nanoTime();
                            boolean ok;
                            try {
                                HttpRequest req = HttpRequest.newBuilder()
                                        .uri(URI.create("http://" + HOST + ":" + PORT + "/api/order/" + id))
                                        .timeout(Duration.ofSeconds(3))
                                        .GET().build();
                                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                                ok = resp.statusCode() < 400;
                            } catch (Exception e) {
                                ok = false;
                            }
                            double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
                            latencies.add(elapsedMs);
                            if (!ok) errorCount.incrementAndGet();
                            long remaining = ITERATION_PACING_MS - (long) elapsedMs;
                            if (remaining > 0) {
                                try { Thread.sleep(remaining); } catch (InterruptedException ignored) {}
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        }
        double totalTimeS = (System.currentTimeMillis() - t0) / 1000.0;

        List<Double> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        int n = sorted.size();
        double p50 = percentile(sorted, 50);
        double p90 = percentile(sorted, 90);
        double p95 = percentile(sorted, 95);
        double p99 = percentile(sorted, 99);
        double avg = sorted.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double errorRate = n == 0 ? 1.0 : (double) errorCount.get() / n;
        double rps = totalTimeS > 0 ? n / totalTimeS : 0;
        boolean passed = p95 <= P95_THRESHOLD_MS && errorRate < ERROR_RATE_THRESHOLD;

        System.out.printf("%n===== LOAD TEST: %s (VUs=%d, duration=%ds) =====%n", label, vus, durationS);
        System.out.printf("Requests total : %d%n", n);
        System.out.printf("RPS            : %.1f%n", rps);
        System.out.printf("avg latency    : %.1f ms%n", avg);
        System.out.printf("p50            : %.1f ms%n", p50);
        System.out.printf("p90            : %.1f ms%n", p90);
        System.out.printf("p95            : %.1f ms   (threshold <= %.0f ms)%n", p95, P95_THRESHOLD_MS);
        System.out.printf("p99            : %.1f ms%n", p99);
        System.out.printf("Error Rate     : %.2f %%   (threshold < %.0f %%)%n", errorRate * 100, ERROR_RATE_THRESHOLD * 100);
        System.out.printf("RESULT         : %s%n", passed ? "PASS" : "FAIL");
        System.out.println("=".repeat(60));

        return new Result(label, vus, n, rps, avg, p50, p90, p95, p99, errorRate, passed);
    }

    private static double percentile(List<Double> sortedData, double p) {
        if (sortedData.isEmpty()) return 0.0;
        double k = (sortedData.size() - 1) * (p / 100.0);
        int f = (int) k, c = Math.min(f + 1, sortedData.size() - 1);
        if (f == c) return sortedData.get(f);
        return sortedData.get(f) + (sortedData.get(c) - sortedData.get(f)) * (k - f);
    }

    public record Result(String label, int vus, int n, double rps, double avg,
                          double p50, double p90, double p95, double p99,
                          double errorRate, boolean passed) {}
}
