package com.devops;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal HTTP service using only the JDK (no frameworks), so the build stays fast
 * and the CI/CD pipeline is the star of the project.
 *
 * Endpoints:
 *   GET /            -> greeting + version
 *   GET /health      -> {"status":"UP"} (used by Docker HEALTHCHECK and Ansible)
 *   GET /add?a=1&b=2 -> {"result":3}
 *   GET /multiply?a=3&b=4
 *   GET /subtract?a=8&b=3 -> {"result":5}
 *   GET /divide?a=10&b=4
 */
public class App {

    static final String VERSION = System.getenv().getOrDefault("APP_VERSION", "1.0.0");
    private static final Calculator CALC = new Calculator();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = createServer(port);
        server.start();
        System.out.println("devops-demo " + VERSION + " listening on port " + port);
    }

    static HttpServer createServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", ex -> respond(ex, 200,
                "{\"app\":\"devops-demo\",\"version\":\"" + VERSION
                        + "\",\"message\":\"CI/CD deployment updated: subtraction is now available!\"}"));

        server.createContext("/health", ex -> respond(ex, 200, "{\"status\":\"UP\"}"));
        server.createContext("/add", ex -> calc(ex, "add"));
        server.createContext("/multiply", ex -> calc(ex, "multiply"));
        server.createContext("/subtract", ex -> calc(ex, "subtract"));
        server.createContext("/divide", ex -> calc(ex, "divide"));

        return server;
    }

    private static void calc(HttpExchange ex, String op) throws IOException {
        try {
            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            int a = Integer.parseInt(q.get("a"));
            int b = Integer.parseInt(q.get("b"));

            String result;

            switch (op) {
                case "add":
                    result = String.valueOf(CALC.add(a, b));
                    break;
                case "multiply":
                    result = String.valueOf(CALC.multiply(a, b));
                    break;
                case "subtract":
                    result = String.valueOf(CALC.subtract(a, b));
                    break;
                default:
                    result = String.valueOf(CALC.divide(a, b));
                    break;
            }

            respond(ex, 200, "{\"result\":" + result + "}");

        } catch (NumberFormatException | NullPointerException e) {
            respond(ex, 400, "{\"error\":\"query params a and b must be integers\"}");
        } catch (IllegalArgumentException e) {
            respond(ex, 400, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();

        if (raw == null || raw.isEmpty()) {
            return map;
        }

        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);

            if (kv.length == 2) {
                map.put(kv[0], kv[1]);
            }
        }

        return map;
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}