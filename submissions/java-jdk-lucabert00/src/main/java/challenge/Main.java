package challenge;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 3 SQLite baseline: blocking-handler JDK {@code HttpServer} behind Nginx.
 *
 * <p>Binds {@code HOST:PORT} from the environment, routes the five SPEC paths
 * to handlers, and answers every other path with byte-exact 404 JSON.
 * Only {@code GET /health} is live in this ticket via {@link Db#checkHealth};
 * feed, post and like handlers stay as stubs until later tickets.
 *
 * <p>Durability posture (SPEC Responses plus README rules 5-6): WAL with
 * {@code synchronous=NORMAL} floor, foreign-keys-on, busy timeout 5000,
 * enforced on every pooled connection with startup assertions on all 4 conns.
 * Pool of 4 is the starting point, not an optimum; sizing belongs to later
 * phases. No result caching, no table copies; schema diff stays empty.
 *
 * <p>Keep-alive posture (planning doc {@code §K}, SPEC {@code ≥65 s} rule):
 * idle lifetime is configured in {@code start.sh} via
 * {@code -Dsun.net.httpserver.idleInterval=70}. The other two JDK knobs keep
 * their defaults here: {@code maxIdleConnections=200} (covers the Nginx
 * 64-idle envelope, unchanged) and {@code nodelay=false} (unchanged,
 * experimental, deferred to Phase 10). Direct-mode serving ({@code 0.0.0.0:80})
 * is out of scope; DX-3 owns it in Phase 10.
 *
 * <p>Threading/backlog posture is the starting point, not an optimum:
 * fixed pool of 4 threads and backlog 128 are recorded here and unchanged
 * in this ticket. Tuning belongs to Phases 9-11.
 *
 * <p>Notes for later direct mode: the benchmark box grants
 * {@code LimitNOFILE=65535}; backlog stays 128 behind Nginx. Sizing for
 * ~15k direct-mode idles is deferred to Phase 10/DX-3 (notes only here).
 */
public final class Main {

  private static final byte[] NOT_FOUND = "{\"error\":\"not found\"}".getBytes(StandardCharsets.UTF_8);
  private static final byte[] FEED_STUB =
      "{\"posts\":[]}".getBytes(StandardCharsets.UTF_8);
  private static final byte[] POST_STUB =
      "{\"post\":{\"id\":1,\"body\":\"\",\"created_at\":\"2025-12-31T23:59:19.409Z\",\"author\":\"\",\"like_count\":0}}"
          .getBytes(StandardCharsets.UTF_8);
  private static final byte[] LIKE_STUB =
      "{\"liked\":true,\"already_liked\":false,\"post_id\":1}".getBytes(StandardCharsets.UTF_8);

  /** Start time in millis, used for the health {@code uptime_s} field. */
  private static final long START_MILLIS = System.currentTimeMillis();

  /** Total requests seen since start, for later benchmark columns (§3.4). */
  private static final AtomicLong TOTAL_REQUESTS = new AtomicLong();

  /** Per-status response-code counters, for later benchmark columns (§3.4). */
  private static final ConcurrentHashMap<Integer, AtomicLong> STATUS_COUNTS = new ConcurrentHashMap<>();

  private Main() {}

  /**
   * Bootstraps the server on {@code HOST:PORT} with the starting pool posture.
   *
   * <p>Initializes the durable SQLite layer first and fails fast when pragma
   * assertions do not hold; the HTTP layer starts only after the pool is
   * proven on all 4 connections.
   *
   * @param args command-line arguments, currently unused.
   * @throws IOException if the socket cannot be bound.
   */
  public static void main(String[] args) throws IOException {
    String host = envOr("HOST", "127.0.0.1");
    int port = Integer.parseInt(envOr("PORT", "3000"));
    String sqlitePath = System.getenv("SQLITE_PATH");
    if (sqlitePath == null || sqlitePath.isEmpty()) {
      System.err.println("SQLITE_PATH must name a fresh copy of the seed database");
      System.exit(1);
      return;
    }

    logStartupFlags();

    try {
      Db.init(sqlitePath);
    } catch (RuntimeException e) {
      System.err.println("DB init failed, failing fast: " + e.getMessage());
      System.exit(1);
      return;
    }

    // Backlog 128 is the starting posture behind Nginx (notes only for direct
    // mode; DX-3 owns sizing in Phase 10). LimitNOFILE=65535 is provided by
    // the box; this ticket serves Nginx-first on 127.0.0.1:3000 only.
    HttpServer server = HttpServer.create(new InetSocketAddress(host, port), 128);
    server.createContext("/", Main::route);
    // Fixed pool of 4 threads: starting posture, explicitly not an optimum.
    // Tuning belongs to Phases 9-11; do not tune here.
    ExecutorService pool = Executors.newFixedThreadPool(4);
    server.setExecutor(pool);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      server.stop(0);
      pool.shutdownNow();
      Db.close();
    }));
    server.start();
  }

  /**
   * Logs the effective JDK HttpServer flag values at startup.
   *
   * <p>Reads the live system properties so the startup log proves
   * {@code idleInterval=70} is in effect.
   */
  private static void logStartupFlags() {
    String idle = System.getProperty("sun.net.httpserver.idleInterval", "30");
    String maxIdle = System.getProperty("sun.net.httpserver.maxIdleConnections", "200");
    String nodelay = System.getProperty("sun.net.httpserver.nodelay", "false");
    System.out.println("sun.net.httpserver.idleInterval=" + idle);
    System.out.println("sun.net.httpserver.maxIdleConnections=" + maxIdle);
    System.out.println("sun.net.httpserver.nodelay=" + nodelay);
  }

  /**
   * Routes one exchange to its stub handler or the 404 fallback.
   *
   * @param exchange the current HTTP exchange.
   * @throws IOException if the response cannot be sent.
   */
  private static void route(HttpExchange exchange) throws IOException {
    String method = exchange.getRequestMethod();
    String path = exchange.getRequestURI().getPath();
    if (path == null || path.isEmpty()) {
      path = "/";
    }

    if ("GET".equals(method) && "/health".equals(path)) {
      handleHealth(exchange);
    } else if ("GET".equals(method) && "/feed".equals(path)) {
      sendJson(exchange, 200, FEED_STUB);
    } else if ("GET".equals(method) && isSinglePostPath(path)) {
      sendJson(exchange, 200, POST_STUB);
    } else if ("POST".equals(method) && "/posts".equals(path)) {
      drainRequestBody(exchange);
      sendJson(exchange, 201, POST_STUB);
    } else if ("POST".equals(method) && isLikePath(path)) {
      drainRequestBody(exchange);
      sendJson(exchange, 201, LIKE_STUB);
    } else {
      handleNotFound(exchange);
    }
  }

  /**
   * Answers {@code GET /health} with a live-DB probe.
   *
   * <p>Runs {@code SELECT 1} via the pooled layer: 200
   * {@code {"status":"ok","db":"ok","uptime_s":<int>}} on success, 503
   * {@code {"status":"degraded","db":"unreachable","error":<string>}} when the
   * probe throws. No result caching; the probe prepares at checkout and closes
   * per use.
   *
   * @param exchange the current HTTP exchange.
   * @throws IOException if the response cannot be sent.
   */
  private static void handleHealth(HttpExchange exchange) throws IOException {
    long uptime = (System.currentTimeMillis() - START_MILLIS) / 1000L;
    try {
      Db.checkHealth();
    } catch (Exception e) {
      String message = e.getMessage();
      if (message == null || message.isEmpty()) {
        message = e.getClass().getSimpleName();
      }
      byte[] body =
          ("{\"status\":\"degraded\",\"db\":\"unreachable\",\"error\":\""
                  + escapeJson(message)
                  + "\"}")
              .getBytes(StandardCharsets.UTF_8);
      sendJson(exchange, 503, body);
      return;
    }
    byte[] body =
        ("{\"status\":\"ok\",\"db\":\"ok\",\"uptime_s\":" + uptime + "}").getBytes(StandardCharsets.UTF_8);
    sendJson(exchange, 200, body);
  }

  /**
   * Escapes a string for embedding in compact JSON.
   *
   * @param value the raw string, never null.
   * @return the escaped string without surrounding quotes.
   */
  private static String escapeJson(String value) {
    StringBuilder out = new StringBuilder(value.length() + 16);
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      switch (ch) {
        case '"':
          out.append("\\\"");
          break;
        case '\\':
          out.append("\\\\");
          break;
        case '\n':
          out.append("\\n");
          break;
        case '\r':
          out.append("\\r");
          break;
        case '\t':
          out.append("\\t");
          break;
        default:
          if (ch < 0x20) {
            out.append(String.format("\\u%04x", (int) ch));
          } else {
            out.append(ch);
          }
          break;
      }
    }
    return out.toString();
  }

  /**
   * Answers unknown paths with the exact 404 error bytes.
   *
   * @param exchange the current HTTP exchange.
   * @throws IOException if the response cannot be sent.
   */
  private static void handleNotFound(HttpExchange exchange) throws IOException {
    drainRequestBody(exchange);
    sendJson(exchange, 404, NOT_FOUND);
  }

  /**
   * Checks whether a path is a single-post shape ({@code /posts/<id>}).
   *
   * <p>Stub routing only: no id validation here (later tickets own the
   * {@code invalid post id} matrix). Matches exactly two segments.
   *
   * @param path the request path, never null.
   * @return true when the path has the single-post shape.
   */
  private static boolean isSinglePostPath(String path) {
    if (!path.startsWith("/posts/")) {
      return false;
    }
    String rest = path.substring("/posts/".length());
    return !rest.isEmpty() && rest.indexOf('/') == -1;
  }

  /**
   * Checks whether a path is a like shape ({@code /posts/<id>/like}).
   *
   * <p>Stub routing only: no id or auth checks here.
   *
   * @param path the request path, never null.
   * @return true when the path has the like shape.
   */
  private static boolean isLikePath(String path) {
    if (!path.startsWith("/posts/") || !path.endsWith("/like")) {
      return false;
    }
    String middle = path.substring("/posts/".length(), path.length() - "/like".length());
    return !middle.isEmpty() && middle.indexOf('/') == -1;
  }

  /**
   * Drains and closes the request body so the keep-alive connection stays reusable.
   *
   * @param exchange the current HTTP exchange.
   */
  private static void drainRequestBody(HttpExchange exchange) {
    try (InputStream in = exchange.getRequestBody()) {
      in.readAllBytes();
    } catch (IOException ignored) {
      // Stub handlers ignore body errors; the response still carries the stub.
    }
  }

  /**
   * Sends a compact-JSON response and records request-logging hooks.
   *
   * <p>Hooks for later benchmark columns (§3.4): total count, per-status
   * counts, and a one-line method/path/status log. Accept-queue depth and
   * refused connections are observed at the OS/executor level; this ticket
   * adds the response-code hook only, with no profiling agent.
   *
   * @param exchange the current HTTP exchange.
   * @param status the HTTP status code to send.
   * @param body the exact response bytes to send.
   * @throws IOException if the response cannot be sent.
   */
  private static void sendJson(HttpExchange exchange, int status, byte[] body) throws IOException {
    try {
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    } finally {
      exchange.close();
      recordRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), status);
    }
  }

  /**
   * Records one completed request for later benchmark columns.
   *
   * @param method the HTTP method, e.g. GET.
   * @param path the request path.
   * @param status the response status code sent.
   */
  private static void recordRequest(String method, String path, int status) {
    TOTAL_REQUESTS.incrementAndGet();
    STATUS_COUNTS.computeIfAbsent(status, k -> new AtomicLong()).incrementAndGet();
    System.out.println(method + " " + path + " -> " + status);
  }

  /**
   * Reads an environment variable or returns a fallback.
   *
   * @param name the variable name.
   * @param fallback the value when the variable is unset or empty.
   * @return the variable value or the fallback.
   */
  private static String envOr(String name, String fallback) {
    String value = System.getenv(name);
    return (value == null || value.isEmpty()) ? fallback : value;
  }
}
