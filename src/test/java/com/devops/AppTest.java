package com.devops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Integration-style tests: start the real server on a random port and call it over HTTP. */
class AppTest {

    private static HttpServer server;
    private static String base;
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @BeforeAll
    static void start() throws Exception {
        server = App.createServer(0); // 0 = pick a free port
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create(base + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        HttpResponse<String> r = get("/health");
        assertEquals(200, r.statusCode());
        assertEquals("{\"status\":\"UP\"}", r.body());
    }

    @Test
    void rootReturnsCalculatorUI() throws Exception {
        HttpResponse<String> r = get("/");

        assertEquals(200, r.statusCode());
        assertTrue(r.headers()
                .firstValue("Content-Type")
                .orElse("")
                .contains("text/html"));
        assertTrue(r.body().contains("<html"));
        assertTrue(r.body().contains("Calculator"));
    }

    @Test
    void addEndpointWorks() throws Exception {
        HttpResponse<String> r = get("/add?a=2&b=3");
        assertEquals(200, r.statusCode());
        assertEquals("{\"result\":5}", r.body());
    }

    @Test
    void badInputReturns400() throws Exception {
        assertEquals(400, get("/add?a=x&b=3").statusCode());
        assertEquals(400, get("/divide?a=1&b=0").statusCode());
    }
}
