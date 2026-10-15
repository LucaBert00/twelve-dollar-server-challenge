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
 * Phase 3 SQLite baseline plus T04 reads and T05 authed writes.
 *
 * <p>Binds {@code HOST:PORT} from the environment, routes the five SPEC paths
 * to handlers, and answers every other path with byte-exact 404 JSON.
 * {@code GET /health} probes the live DB; {@code GET /feed} and
 * {@code GET /posts/:id} serve live read paths (T04 byte-exact, untouched by
 * T05); {@code POST /posts} and {@code POST /posts/:id/like} verify HS256 JWTs
 * per request and serve the write paths (T05) with auth-first routing.
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
  private static final byte[] INVALID_POST_ID =
      "{\"error\":\"invalid post id\"}".getBytes(StandardCharsets.UTF_8);
  private static final byte[] POST_NOT_FOUND =
      "{\"error\":\"post not found\"}".getBytes(StandardCharsets.UTF_8);
  private static final byte[] INTERNAL_ERROR =
      "{\"error\":\"internal server error\"}".getBytes(StandardCharsets.UTF_8);

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
   * <p>Initializes the durable SQLite layer plus the JWT verifier first and
   * fails fast when pragma assertions do not hold or the JWT secret is
   * missing; the HTTP layer starts only after the pool is proven on all 4
   * connections.
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
    String jwtSecret = System.getenv("JWT_SECRET");
    if (jwtSecret == null || jwtSecret.isEmpty()) {
      System.err.println("JWT_SECRET must not be empty");
      System.exit(1);
      return;
    }
    try {
      Auth.init(jwtSecret);
    } catch (RuntimeException e) {
      System.err.println("Auth init failed, failing fast: " + e.getMessage());
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
   * Routes one exchange to its handler or the 404 fallback.
   *
   * <p>Authed routes verify the JWT before any id or body checks
   * (auth-before-id).
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
      handleFeed(exchange);
    } else if ("GET".equals(method) && isSinglePostPath(path)) {
      handlePost(exchange, path);
    } else if ("POST".equals(method) && "/posts".equals(path)) {
      handleCreate(exchange);
    } else if ("POST".equals(method) && isLikePath(path)) {
      handleLike(exchange, path);
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
                  + Json.quote(message)
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
   * Answers {@code GET /feed} with the 20 newest posts.
   *
   * <p>Runs the reference feed query per request with exact golden bytes via
   * {@link Json#feed}. Any failure becomes 500 {@code internal server error}.
   *
   * @param exchange the current HTTP exchange.
   * @throws IOException if the response cannot be sent.
   */
  private static void handleFeed(HttpExchange exchange) throws IOException {
    try {
      sendJson(exchange, 200, Json.feed(Db.feed()));
    } catch (Exception e) {
      sendJson(exchange, 500, INTERNAL_ERROR);
    }
  }

  /**
   * Answers {@code GET /posts/:id} with one post or an id error.
   *
   * <p>Non-positive-integer ids ({@code 0}, {@code -1}, {@code abc},
   * {@code 1.5}) become 400 {@code invalid post id}; a well-formed but absent
   * id becomes 404 {@code post not found}; otherwise the exact golden bytes
   * via {@link Json#singlePost}. Any failure becomes 500
   * {@code internal server error}.
   *
   * @param exchange the current HTTP exchange.
   * @param path the request path, already known to have the single-post shape.
   * @throws IOException if the response cannot be sent.
   */
  private static void handlePost(HttpExchange exchange, String path) throws IOException {
    long id = parsePostId(path.substring("/posts/".length()));
    if (id < 1) {
      sendJson(exchange, 400, INVALID_POST_ID);
      return;
    }
    try {
      var row = Db.getPost(id);
      if (row.isEmpty()) {
        sendJson(exchange, 404, POST_NOT_FOUND);
        return;
      }
      sendJson(exchange, 200, Json.singlePost(row.get()));
    } catch (Exception e) {
      sendJson(exchange, 500, INTERNAL_ERROR);
    }
  }

  /**
   * Answers {@code POST /posts} with trim-then-validate creation.
   *
   * <p>Auth runs first; then the JSON body is parsed ({@code malformed JSON
   * body}), the {@code body} field must be a string that is non-empty after
   * {@code strip()} ({@code body is required}) and at most 500 code points
   * after trim ({@code body must be at most 500 characters}). Success inserts
   * via the reference SQL in auto-commit (commit before respond) and answers
   * 201 with the trimmed body, token username, and {@code like_count:0}.
   *
   * @param exchange the current HTTP exchange.
   * @throws IOException if the response cannot be sent.
   */
  private static void handleCreate(HttpExchange exchange) throws IOException {
    Auth.User user;
    try {
      user = Auth.authenticate(exchange.getRequestHeaders().getFirst("Authorization"));
    } catch (Auth.AuthException e) {
      drainRequestBody(exchange);
      sendJson(exchange, 401, Json.error(e.getMessage()));
      return;
    }
    byte[] raw;
    try (InputStream in = exchange.getRequestBody()) {
      raw = in.readAllBytes();
    } catch (IOException e) {
      sendJson(exchange, 400, Json.error("malformed JSON body"));
      return;
    }
    java.util.Map<String, Object> obj;
    try {
      obj = Auth.parseObject(new String(raw, StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      sendJson(exchange, 400, Json.error("malformed JSON body"));
      return;
    }
    Object value = obj.get("body");
    if (!(value instanceof String)) {
      sendJson(exchange, 400, Json.error("body is required"));
      return;
    }
    String trimmed = ((String) value).strip();
    if (trimmed.isEmpty()) {
      sendJson(exchange, 400, Json.error("body is required"));
      return;
    }
    if (trimmed.codePointCount(0, trimmed.length()) > 500) {
      sendJson(exchange, 400, Json.error("body must be at most 500 characters"));
      return;
    }
    try {
      var row = Db.createPost(user.id(), trimmed, user.username());
      sendJson(exchange, 201, Json.singlePost(row));
    } catch (Exception e) {
      sendJson(exchange, 500, INTERNAL_ERROR);
    }
  }

  /**
   * Answers {@code POST /posts/:id/like} with idempotent likes.
   *
   * <p>Auth runs before id validation: without a token the answer is 401 even
   * for {@code abc}; with a valid token a bad id is 400. Missing posts are 404
   * (the app checks existence). The first like is 201 with
   * {@code already_liked:false}, repeats are 200 with {@code true}. Each write
   * commits in auto-commit before the response is sent.
   *
   * @param exchange the current HTTP exchange.
   * @param path the request path, already known to have the like shape.
   * @throws IOException if the response cannot be sent.
   */
  private static void handleLike(HttpExchange exchange, String path) throws IOException {
    Auth.User user;
    try {
      user = Auth.authenticate(exchange.getRequestHeaders().getFirst("Authorization"));
    } catch (Auth.AuthException e) {
      drainRequestBody(exchange);
      sendJson(exchange, 401, Json.error(e.getMessage()));
      return;
    }
    String raw = path.substring("/posts/".length(), path.length() - "/like".length());
    long id = parsePostId(raw);
    if (id < 1) {
      drainRequestBody(exchange);
      sendJson(exchange, 400, INVALID_POST_ID);
      return;
    }
    drainRequestBody(exchange);
    try {
      var result = Db.likePost(user.id(), id);
      if (result.isEmpty()) {
        sendJson(exchange, 404, POST_NOT_FOUND);
        return;
      }
      boolean alreadyLiked = result.get();
      sendJson(exchange, alreadyLiked ? 200 : 201, Json.like(true, alreadyLiked, id));
    } catch (Exception e) {
      sendJson(exchange, 500, INTERNAL_ERROR);
    }
  }

  /**
   * Parses a raw post id segment into a positive integer.
   *
   * <p>Out-of-scope choice (SPEC tests only {@code 0,-1,abc,1.5} plus a large
   * missing id): digits-only and fitting in a {@code long} with value at least
   * 1 wins; leading zeros resolve to their numeric value ({@code 01} reads
   * post 1) and digit strings overflowing {@code long} read as invalid.
   *
   * @param raw the raw id segment, never null.
   * @return the id, or -1 when it is not a positive integer.
   */
  private static long parsePostId(String raw) {
    if (raw.isEmpty()) {
      return -1;
    }
    for (int i = 0; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      if (ch < '0' || ch > '9') {
        return -1;
      }
    }
    try {
      long id = Long.parseLong(raw);
      return id >= 1 ? id : -1;
    } catch (NumberFormatException e) {
      return -1;
    }
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
   * <p>Shape routing only: id validation lives in {@link #handlePost}, which
   * answers the {@code invalid post id} matrix. Matches exactly two segments.
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
   * <p>Shape routing only: auth runs first in {@link #handleLike}, then id
   * validation, then the existence check.
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
      // Body errors never change the already-chosen response.
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
