package com.devops;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class App {

    static final String VERSION =
            System.getenv().getOrDefault("APP_VERSION", "1.0.0");

    private static final Calculator CALC = new Calculator();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(
                System.getenv().getOrDefault("PORT", "8080"));

        HttpServer server = createServer(port);
        server.start();

        System.out.println(
                "devops-demo " + VERSION + " listening on port " + port);
    }

    static HttpServer createServer(int port) throws IOException {
        HttpServer server =
                HttpServer.create(new InetSocketAddress(port), 0);

        // Frontend served directly by the Java backend
        server.createContext("/", ex -> {
            if ("/".equals(ex.getRequestURI().getPath())) {
                respondHtml(ex, 200, FRONTEND);
            } else {
                respond(ex, 404, "{\"error\":\"Not found\"}");
            }
        });

        // Existing API endpoints
        server.createContext("/health",
                ex -> respond(ex, 200, "{\"status\":\"UP\"}"));

        server.createContext("/add", ex -> calc(ex, "add"));
        server.createContext("/multiply", ex -> calc(ex, "multiply"));
        server.createContext("/subtract", ex -> calc(ex, "subtract"));
        server.createContext("/divide", ex -> calc(ex, "divide"));

        return server;
    }

    private static void calc(HttpExchange ex, String op)
            throws IOException {

        try {
            Map<String, String> q =
                    parseQuery(ex.getRequestURI().getRawQuery());

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
            respond(ex, 400,
                    "{\"error\":\"query params a and b must be integers\"}");

        } catch (IllegalArgumentException e) {
            respond(ex, 400,
                    "{\"error\":\"" + e.getMessage() + "\"}");
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

    private static void respond(
            HttpExchange ex, int status, String body)
            throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");

        ex.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void respondHtml(
            HttpExchange ex, int status, String html)
            throws IOException {

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set(
                "Content-Type", "text/html; charset=UTF-8");

        ex.sendResponseHeaders(status, bytes.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static final String FRONTEND = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>DevOps Calculator</title>

          <style>
            * {
              box-sizing: border-box;
            }

            body {
              margin: 0;
              min-height: 100vh;
              display: grid;
              place-items: center;
              padding: 24px;
              font-family: Arial, sans-serif;
              color: #f8fafc;
              background: linear-gradient(135deg, #101827, #172554);
            }

            .calculator {
              width: 100%;
              max-width: 440px;
              padding: 32px;
              border: 1px solid #334155;
              border-radius: 20px;
              background: #111c2e;
              box-shadow: 0 24px 70px #0005;
            }

            h1 {
              margin: 0 0 8px;
              font-size: 28px;
            }

            .subtitle {
              margin: 0 0 26px;
              color: #94a3b8;
              line-height: 1.5;
            }

            label {
              display: block;
              margin: 16px 0 8px;
              color: #cbd5e1;
              font-size: 14px;
            }

            input, select, button {
              width: 100%;
              padding: 13px 14px;
              border-radius: 10px;
              font-size: 16px;
            }

            input, select {
              border: 1px solid #475569;
              outline: none;
              color: #f8fafc;
              background: #0f172a;
            }

            input:focus, select:focus {
              border-color: #38bdf8;
            }

            button {
              margin-top: 22px;
              border: 0;
              color: #082f49;
              background: #38bdf8;
              font-weight: bold;
              cursor: pointer;
            }

            button:hover {
              background: #7dd3fc;
            }

            button:disabled {
              opacity: 0.6;
              cursor: wait;
            }

            .result {
              margin-top: 20px;
              padding: 16px;
              border-radius: 10px;
              background: #0f172a;
              overflow-wrap: anywhere;
            }

            .result-label {
              color: #94a3b8;
              font-size: 13px;
            }

            #resultValue {
              margin-top: 8px;
              font-size: 28px;
              font-weight: bold;
            }

            #status {
              min-height: 20px;
              margin-top: 12px;
              color: #fca5a5;
              font-size: 13px;
            }

            footer {
              margin-top: 22px;
              color: #64748b;
              font-size: 12px;
              text-align: center;
            }
          </style>
        </head>

        <body>
          <main class="calculator">
            <h1>DevOps Calculator</h1>

            <p class="subtitle">
              Java REST API deployed using Docker, Kubernetes and AWS EKS.
            </p>

            <form id="calculatorForm">
              <label for="numberA">First number</label>
              <input id="numberA" type="number" step="1"
                     required value="10">

              <label for="numberB">Second number</label>
              <input id="numberB" type="number" step="1"
                     required value="4">

              <label for="operation">Operation</label>
              <select id="operation">
                <option value="add">Addition (+)</option>
                <option value="subtract">Subtraction (-)</option>
                <option value="multiply">Multiplication (×)</option>
                <option value="divide">Division (÷)</option>
              </select>

              <button id="calculateButton" type="submit">
                Calculate
              </button>
            </form>

            <section class="result" aria-live="polite">
              <div class="result-label">Result</div>
              <div id="resultValue">—</div>
            </section>

            <div id="status"></div>

            <footer>
              Frontend and backend served by the same application
            </footer>
          </main>

          <script>
            const form = document.getElementById("calculatorForm");
            const button = document.getElementById("calculateButton");
            const resultValue = document.getElementById("resultValue");
            const status = document.getElementById("status");

            form.addEventListener("submit", async (event) => {
              event.preventDefault();

              const a = document.getElementById("numberA").value;
              const b = document.getElementById("numberB").value;
              const operation =
                document.getElementById("operation").value;

              button.disabled = true;
              button.textContent = "Calculating...";
              resultValue.textContent = "—";
              status.textContent = "";

              try {
                const url =
                  `/${operation}?a=${encodeURIComponent(a)}&b=${encodeURIComponent(b)}`;

                const response = await fetch(url);
                const data = await response.json();

                if (!response.ok) {
                  throw new Error(data.error || "Request failed");
                }

                resultValue.textContent = data.result;
              } catch (error) {
                status.textContent =
                  error.message || "Unable to calculate";
              } finally {
                button.disabled = false;
                button.textContent = "Calculate";
              }
            });
          </script>
        </body>
        </html>
        """;
}