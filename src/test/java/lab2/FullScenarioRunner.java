package lab2;

import lab2.loadtest.LoadTest;
import lab2.service.ExternalService;
import lab2.service.MainService;

import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Запускає ВЕСЬ сценарій лабораторної в одному процесі (для відтворюваної верифікації). */
public final class FullScenarioRunner {
    static final HttpClient C = HttpClient.newHttpClient();

    static String call(String method, String url, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url));
        if (method.equals("POST")) b.POST(HttpRequest.BodyPublishers.ofString(body));
        else b.GET();
        long t = System.nanoTime();
        HttpResponse<String> r = C.send(b.build(), HttpResponse.BodyHandlers.ofString());
        double ms = (System.nanoTime() - t) / 1e6;
        return String.format("HTTP %d (%.0f ms) %s", r.statusCode(), ms, r.body());
    }

    public static void main(String[] a) throws Exception {
        java.util.logging.Logger.getLogger("resilience").setLevel(java.util.logging.Level.OFF);
        new ExternalService().start(9001);
        new MainService().start(9000);
        Thread.sleep(500);
        String M = "http://127.0.0.1:9000", E = "http://127.0.0.1:9001";

        System.out.println("##### TASK 1: LOAD TESTS #####");
        LoadTest.run(8, 5, "normal load (8 VUs)");
        LoadTest.run(100, 4, "increased load (100 VUs)");

        System.out.println("\n##### TASK 2: FAULT INJECTION #####");
        System.out.println("0 sanity           : " + call("GET", M + "/api/order/1", null));
        System.out.println("1 resilience=false : " + call("POST", M + "/control", "{\"resilience\": false}"));
        System.out.println("2 mode=error500    : " + call("POST", E + "/control", "{\"mode\": \"error500\"}"));
        System.out.println("3 BEFORE           : " + call("GET", M + "/api/order/1", null));
        System.out.println("4 resilience=true  : " + call("POST", M + "/control", "{\"resilience\": true}"));
        System.out.println("5 AFTER (5xx)      : " + call("GET", M + "/api/order/1", null));
        System.out.println("6 mode=delay 1500  : " + call("POST", E + "/control", "{\"mode\": \"delay\", \"delayMs\": 1500}"));
        System.out.println("7 AFTER (timeout)  : " + call("GET", M + "/api/order/2", null));
        System.out.println("8 mode=normal      : " + call("POST", E + "/control", "{\"mode\": \"normal\"}"));
        System.out.println("9 recovered        : " + call("GET", M + "/api/order/3", null));
        System.exit(0);
    }
}
