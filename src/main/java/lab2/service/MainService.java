package main.java.lab2.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import main.java.lab2.resilience.CallResult;
import main.java.lab2.resilience.DependencyError;
import main.java.lab2.resilience.ResilienceConfig;
import main.java.lab2.resilience.ResilientPricingClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Основний сервіс. Критичний ендпоінт дослідження:
 *
 * <pre>GET /api/order/{id}</pre>
 *
 * <b>Призначення:</b> повернути ціну товару для замовлення, отримуючи її від
 * зовнішньої залежності Pricing Service (http://127.0.0.1:9001). Типова
 * операція "читання з залежністю", чутлива і до Performance Efficiency
 * (Завдання №1), і до Reliability / Resilience (Завдання №2-3) моделі
 * ISO/IEC 25010.
 *
 * <p>Типовий запит: {@code GET /api/order/42}<br>
 * Очікувана відповідь (200): {@code {"orderId":"42","price":31.99,
 * "degraded":false,"attempts":1,"message":"OK"}}
 *
 * <p>Керування для демонстрації (не бізнес-API):
 * <pre>POST /control {"resilience": true|false}</pre>
 * {@code true} — Retry(3, backoff) + Fallback увімкнено (стан "після");
 * {@code false} — базова поведінка без захисту (стан "до").
 */
public final class MainService {

    private static final String EXTERNAL_HOST = "127.0.0.1";
    private static final int EXTERNAL_PORT = 9001;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(500))
            .build();

    private final AtomicBoolean resilienceEnabled = new AtomicBoolean(true);
    private final ResilienceConfig config = ResilienceConfig.defaults();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9000;
        new MainService().start(port);
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 512);
        server.createContext("/api/order/", new OrderHandler());
        server.createContext("/control", new ControlHandler());
        // Обмежений пул воркерів імітує реальний прикладний сервер (наприклад,
        // Tomcat/Undertow) з фіксованою кількістю робочих потоків, а НЕ
        // необмежений thread-per-request, що дозволяє коректно спостерігати
        // чергу/бек-пресію під навантаженням у Завданні №1.
        server.setExecutor(Executors.newFixedThreadPool(64));
        server.start();
        System.out.println("[MainService] Order API listening on :" + port);
    }

    /** Реальний виклик залежності через java.net.http.HttpClient. */
    private double callPricingDependency(String itemId, long timeoutMs) throws DependencyError {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + EXTERNAL_HOST + ":" + EXTERNAL_PORT + "/price/" + itemId))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500) {
                throw new DependencyError("HTTP " + response.statusCode() + " from pricing service");
            }
            return parsePrice(response.body());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new DependencyError("timeout: " + e.getMessage());
        } catch (IOException | InterruptedException e) {
            throw new DependencyError("transport error: " + e.getMessage());
        }
    }

    private static final Pattern PRICE_PATTERN = Pattern.compile("\"price\"\\s*:\\s*([0-9.]+)");

    private static double parsePrice(String json) throws DependencyError {
        Matcher m = PRICE_PATTERN.matcher(json);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        throw new DependencyError("malformed response: " + json);
    }

    private final class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            String orderId = path.substring(path.lastIndexOf('/') + 1);

            boolean enabled = resilienceEnabled.get();
            ResilientPricingClient client = new ResilientPricingClient(
                    MainService.this::callPricingDependency, config, enabled);
            CallResult result = client.getPrice(orderId);

            if (result.success) {
                String json = String.format(java.util.Locale.ROOT,
                        "{\"orderId\":\"%s\",\"price\":%.2f,\"degraded\":%b,\"attempts\":%d,\"message\":\"%s\"}",
                        orderId, result.price, result.degraded, result.attempts, escape(result.message));
                respond(ex, 200, json);
            } else {
                String json = String.format(
                        "{\"orderId\":\"%s\",\"error\":\"%s\",\"attempts\":%d}",
                        orderId, escape(result.message), result.attempts);
                respond(ex, result.statusCode, json);
            }
        }
    }

    private final class ControlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (body.contains("\"resilience\"")) {
                    resilienceEnabled.set(body.contains("true"));
                }
            }
            respond(ex, 200, "{\"resilienceEnabled\":" + resilienceEnabled.get() + "}");
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\"", "'");
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