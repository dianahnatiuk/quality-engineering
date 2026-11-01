package lab2.loadtest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Модульні тести для логіки, виділеної з LoadTest.run() під час рефакторингу (чисті функції без мережі). */
class LoadTestUnitTest {

    private static List<Double> oneToHundred() {
        List<Double> data = new ArrayList<>();
        for (int i = 1; i <= 100; i++) data.add((double) i);
        return data;
    }

    @Test
    void percentile_interpolatesBetweenNeighbours() {
        List<Double> data = oneToHundred();
        assertEquals(50.5, LoadTest.percentile(data, 50), 1e-9);
        assertEquals(95.05, LoadTest.percentile(data, 95), 1e-9);
        assertEquals(99.01, LoadTest.percentile(data, 99), 1e-9);
        assertEquals(100.0, LoadTest.percentile(data, 100), 1e-9);
    }

    @Test
    void percentile_ofEmptyData_isZero() {
        assertEquals(0.0, LoadTest.percentile(List.of(), 95), 1e-9);
    }

    @Test
    void errorRate_handlesEmptyAndNormalRuns() {
        assertEquals(1.0, LoadTest.errorRate(0, 0), 1e-9);
        assertEquals(0.25, LoadTest.errorRate(1, 4), 1e-9);
        assertEquals(0.0, LoadTest.errorRate(0, 10), 1e-9);
    }

    @Test
    void throughput_isRequestsPerSecond() {
        assertEquals(25.0, LoadTest.throughput(100, 4.0), 1e-9);
        assertEquals(0.0, LoadTest.throughput(5, 0.0), 1e-9);
    }

    @Test
    void meetsSlo_usesInclusiveP95AndStrictErrorRate() {
        assertTrue(LoadTest.meetsSlo(250.0, 0.0099));
        assertFalse(LoadTest.meetsSlo(250.1, 0.0));
        assertFalse(LoadTest.meetsSlo(100.0, 0.01));
    }

    @Test
    void summarize_computesAllMetricsAndVerdict() {
        LoadTest.Measurements ok = new LoadTest.Measurements(List.of(40.0, 10.0, 30.0, 20.0), 0, 2.0);
        LoadTest.Result r = LoadTest.summarize("unit", 3, ok);

        assertEquals(4, r.n());
        assertEquals(2.0, r.rps(), 1e-9);
        assertEquals(25.0, r.avg(), 1e-9);
        assertEquals(25.0, r.p50(), 1e-9);
        assertTrue(r.passed());

        LoadTest.Measurements withErrors = new LoadTest.Measurements(List.of(10.0, 20.0, 30.0, 40.0), 1, 2.0);
        assertFalse(LoadTest.summarize("unit", 3, withErrors).passed());
    }
}
