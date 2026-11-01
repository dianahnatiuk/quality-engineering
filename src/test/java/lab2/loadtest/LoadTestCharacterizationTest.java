package lab2.loadtest;

import lab2.service.ExternalService;
import lab2.service.MainService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Характеризаційні (захисні) тести для LoadTest.run(...) — Лаб. №3, Завдання 1.
 * Фіксують ПОТОЧНУ зовнішню поведінку методу до рефакторингу через його публічний API.
 * Після рефакторингу ці самі тести без змін мають залишитися зеленими.
 *
 * Увага: тести використовують порти 9000/9001 — зупиніть ExternalService/MainService у IDE перед запуском.
 */
class LoadTestCharacterizationTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static ExternalService external;
    private static MainService main;

    @BeforeAll
    static void startBackend() throws Exception {
        external = new ExternalService();
        external.start(9001);
        main = new MainService();
        main.start(9000);
        Thread.sleep(300);
    }

    @AfterAll
    static void stopBackend() {
        main.stop();
        external.stop();
    }

    private static void post(String url, String body) throws Exception {
        HTTP.send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthyBackend_givesPassVerdictAndConsistentMetrics() throws Exception {
        LoadTest.Result r = LoadTest.run(2, 2, "characterization-normal");

        assertTrue(r.n() > 0);
        assertEquals(0.0, r.errorRate(), 1e-9);
        assertTrue(r.rps() > 0);
        assertTrue(r.p50() <= r.p90() && r.p90() <= r.p95() && r.p95() <= r.p99(),
                "перцентилі мають бути монотонними");
        assertTrue(r.p95() <= 250.0);
        assertTrue(r.passed());
    }

    @Test
    void failingBackendWithoutResilience_givesFailVerdict() throws Exception {
        post("http://127.0.0.1:9000/control", "{\"resilience\": false}");
        post("http://127.0.0.1:9001/control", "{\"mode\": \"error500\"}");
        try {
            LoadTest.Result r = LoadTest.run(2, 1, "characterization-errors");

            assertTrue(r.n() > 0);
            assertEquals(1.0, r.errorRate(), 1e-9, "кожен запит завершується HTTP 500");
            assertFalse(r.passed());
        } finally {
            post("http://127.0.0.1:9001/control", "{\"mode\": \"normal\"}");
            post("http://127.0.0.1:9000/control", "{\"resilience\": true}");
        }
    }

    @Test
    void zeroVirtualUsers_isTreatedAsTotalFailure() throws Exception {
        LoadTest.Result r = LoadTest.run(0, 1, "characterization-boundary");

        assertEquals(0, r.n());
        assertEquals(1.0, r.errorRate(), 1e-9);
        assertEquals(0.0, r.p95(), 1e-9);
        assertFalse(r.passed());
    }
}
