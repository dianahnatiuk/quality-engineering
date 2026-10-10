package main.java.lab2.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Зовнішня залежність "Pricing Service" (симуляція БД / зовнішнього HTTP API),
 * від якої залежить критична операція GET /api/order/{id} основного сервісу
 * ({@link MainService}).
 *
 * <p>Ендпоінти:
 * <pre>
 *   GET  /price/{id}   -> {"id":"...","price":12.34}
 *   GET  /control       -> поточний режим fault injection
 *   POST /control       -> {"mode":"normal|delay|error500","delayMs":1500}
 * </pre>
 *
 * <p>Штучне обмеження пропускної здатності (семафор {@code DB_POOL_SIZE},
 * аналог пулу з'єднань БД) імітує реальну залежність з обмеженою ємністю:
 * під підвищеним навантаженням запити чекають на слот, latency зростає і
 * SLO навантажувального тесту (p95) порушується — це відтворюваний
 * bottleneck для Завдання №1 (сценарій FAIL).
 */
public final class ExternalService {

    private static final int DB_POOL_SIZE = 4;
    private static final int BASE_PROCESSING_MS = 15;

    private final Semaphore dbPool = new Semaphore(DB_POOL_SIZE, true);
    private final AtomicReference<String> mode = new AtomicReference<>("normal");
    private final AtomicReference<Long> delayMs = new AtomicReference<>(1500L);

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9001;
        new ExternalService().start(port);
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 512);
        server.createContext("/price/", new PriceHandler());
        server.createContext("/control", new ControlHandler());
        // Обмежений пул воркерів (аналог пулу потоків прикладного сервера).
        server.setExecutor(Executors.newFixedThreadPool(64));
        server.start();
        System.out.println("[ExternalService] Pricing Service listening on :" + port +
                " (DB_POOL_SIZE=" + DB_POOL_SIZE + ")");
    }

    private final class PriceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            String itemId = path.substring(path.lastIndexOf('/') + 1);
            try {
                dbPool.acquire();
                try {
                    String currentMode = mode.get();
                    if ("error500".equals(currentMode)) {
                        respond(ex, 500, "{\"error\":\"simulated HTTP 5xx\"}");
                        return;
                    }
                    long sleepFor = "delay".equals(currentMode) ? delayMs.get() : BASE_PROCESSING_MS;
                    Thread.sleep(sleepFor);
                    double price = 19.99 + (Math.abs(itemId.hashCode()) % 50);
                    respond(ex, 200, String.format(java.util.Locale.ROOT, "{\"id\":\"%s\",\"price\":%.2f}", itemId, price));
                } finally {
                    dbPool.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                respond(ex, 500, "{\"error\":\"interrupted\"}");
            }
        }
    }

    private final class ControlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (body.contains("\"mode\"")) {
                    mode.set(extract(body, "mode"));
                }
                if (body.contains("\"delayMs\"")) {
                    delayMs.set(Long.parseLong(extractRaw(body, "delayMs")));
                }
            }
            respond(ex, 200, String.format("{\"mode\":\"%s\",\"delayMs\":%d}", mode.get(), delayMs.get()));
        }
    }

    private static String extract(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        int colon = json.indexOf(':', i);
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        return json.substring(q1 + 1, q2);
    }

    private static String extractRaw(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        int colon = json.indexOf(':', i);
        int end = colon + 1;
        while (end < json.length() && Character.isWhitespace(json.charAt(end))) {
            end++; // пропускаємо пробіли після двокрапки
        }
        StringBuilder sb = new StringBuilder();
        while (end < json.length() && (Character.isDigit(json.charAt(end)))) {
            sb.append(json.charAt(end));
            end++;
        }
        return sb.toString();
    }

    private static void respond(HttpExchange ex, int status, String jsonBody) throws IOException {
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}