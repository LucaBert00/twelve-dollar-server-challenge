package challenge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Phase 1 skeleton: every path answers 404 compact JSON. Real routing lands in later tickets. */
public final class Main {

  private static final byte[] NOT_FOUND = "{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8);

  private Main() {}

  public static void main(String[] args) throws IOException {
    String host = envOr("HOST", "127.0.0.1");
    int port = Integer.parseInt(envOr("PORT", "3000"));

    HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 128);
    server.createContext("/", Main::handle404);
    ExecutorService pool = Executors.newFixedThreadPool(4);
    server.setExecutor(pool);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      server.stop(0);
      pool.shutdownNow();
    }));
    server.start();
  }

  private static void handle404(HttpExchange exchange) throws IOException {
    try {
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(404, NOT_FOUND.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(NOT_FOUND);
      }
    } finally {
      exchange.close();
    }
  }

  private static String envOr(String name, String fallback) {
    String value = System.getenv(name);
    return (value == null || value.isEmpty()) ? fallback : value;
  }
}
