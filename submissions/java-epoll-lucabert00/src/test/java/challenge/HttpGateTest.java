package challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Gate: HTTP black-box mirror of {@code test/test.sh} on a fresh copy.
 *
 * <p>Arrange-Act-Assert throughout. Spawns one committed epoll worker in a
 * child JVM (no handler changes) and byte-compares responses. Golden reads run
 * before writes, mirroring the suite order. Needs the Linux build
 * ({@code build.sh} first for {@code build/native/libsqlite3.so}); the suite
 * itself is sequential, so one worker serves the whole gate.
 */
@Tag("gate")
@DisplayName("HTTP gate")
@TestMethodOrder(OrderAnnotation.class)
class HttpGateTest {

  private static final String SECRET = "twelve-dollar-challenge";
  private static final String USER = "golden_ember_1";

  private static Path tempDir;
  private static Path goldenDir;
  private static int port;
  private static Process server;
  private static HttpClient client;
  private static String base;

  private record Reply(int status, String body, String contentType) {}

  @BeforeAll
  static void startServer() throws Exception {
    // Arrange: fresh copy + free port + child JVM.
    Path seed = firstExisting(
        Paths.get("..", "..", "seed", "feed.db"), Paths.get("seed", "feed.db"));
    goldenDir = firstExisting(
        Paths.get("..", "..", "test", "golden"), Paths.get("test", "golden"));
    Path submission = firstExisting(
        Paths.get("submissions", "java-epoll-lucabert00"),
        Paths.get("..", "..", "submissions", "java-epoll-lucabert00"));
    Path nativeLib = submission.resolve("build/native/libsqlite3.so").toAbsolutePath().normalize();
    if (!Files.isRegularFile(nativeLib)) {
      throw new IllegalStateException("missing " + nativeLib + " (run build.sh on Linux first)");
    }
    tempDir = Files.createTempDirectory("http-gate-");
    Files.copy(seed, tempDir.resolve("feed.db"));
    port = freePort();
    String javaBin = Paths.get(System.getProperty("java.home"), "bin",
        System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java")
        .toString();
    String classpath = System.getProperty("java.class.path");
    List<String> command = new ArrayList<>(List.of(
        javaBin, "--enable-native-access=ALL-UNNAMED", "-Dsqlite.lib=" + nativeLib,
        "-Xms256m", "-Xmx256m", "-cp", classpath, "challenge.Main"));
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.environment().put("SQLITE_PATH", tempDir.resolve("feed.db").toString());
    pb.environment().put("JWT_SECRET", SECRET);
    pb.environment().put("HOST", "127.0.0.1");
    pb.environment().put("PORT", String.valueOf(port));
    pb.redirectOutput(tempDir.resolve("server.log").toFile());
    pb.redirectErrorStream(true);
    server = pb.start();
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    base = "http://127.0.0.1:" + port;

    // Act: wait for /health (SPEC start <= 60 s; poll 30 s here).
    long deadline = System.currentTimeMillis() + 30_000;
    while (true) {
      try {
        Reply health = get("/health");
        if (health.status() == 200) {
          break;
        }
      } catch (IOException | InterruptedException e) {
        // Not up yet.
      }
      if (!server.isAlive()) {
        String log = Files.readString(tempDir.resolve("server.log"));
        throw new IllegalStateException("server died during start: " + log);
      }
      if (System.currentTimeMillis() > deadline) {
        String log = Files.readString(tempDir.resolve("server.log"));
        throw new IllegalStateException("no healthy /health after 30 s: " + log);
      }
      Thread.sleep(500);
    }
  }

  @AfterAll
  static void stopServer() throws Exception {
    // Act: kill + cleanup (mirror test/run.sh trap).
    if (server != null && server.isAlive()) {
      server.destroy();
      server.waitFor(5, TimeUnit.SECONDS);
      if (server.isAlive()) {
        server.destroyForcibly();
      }
    }
  }

  @Test
  @Order(1)
  @DisplayName("GET /feed matches golden")
  void goldenFeed() throws Exception {
    // Arrange
    String golden = readGolden("feed.json");

    // Act
    Reply reply = get("/feed");

    // Assert
    assertEquals(200, reply.status());
    assertEquals(golden, reply.body());
    assertJsonType(reply);
  }

  @ParameterizedTest(name = "GET /posts/{0} matches golden")
  @ValueSource(longs = {1, 250000, 500000})
  @Order(2)
  @DisplayName("single posts match golden bytes")
  void goldenPosts(long id) throws Exception {
    // Arrange
    String golden = readGolden("post-" + id + ".json");

    // Act
    Reply reply = get("/posts/" + id);

    // Assert
    assertEquals(200, reply.status(), reply.body());
    assertEquals(golden, reply.body());
  }

  @Test
  @Order(3)
  @DisplayName("health, 404s and missing post")
  void healthAnd404() throws Exception {
    // Act
    Reply health = get("/health");
    Reply nope = get("/nope");
    Reply missing = get("/posts/999999999");

    // Assert
    assertEquals(200, health.status());
    assertTrue(health.body().matches(
        "^\\{\"status\":\"ok\",\"db\":\"ok\",\"uptime_s\":[0-9]+\\}$"), health.body());
    assertEquals(404, nope.status());
    assertEquals("{\"error\":\"not found\"}", nope.body());
    assertEquals(404, missing.status());
    assertEquals("{\"error\":\"post not found\"}", missing.body());
  }

  @ParameterizedTest(name = "GET /posts/{0} -> 400")
  @ValueSource(strings = {"0", "-1", "abc", "1.5"})
  @Order(4)
  @DisplayName("id matrix")
  void idMatrix(String raw) throws Exception {
    // Act
    Reply reply = get("/posts/" + raw);

    // Assert
    assertEquals(400, reply.status());
    assertEquals("{\"error\":\"invalid post id\"}", reply.body());
  }

  static Stream<Arguments> authCases() {
    // Arrange: mirror test/test.sh auth rows (body {"body":"x"}).
    long now = Instant.now().getEpochSecond();
    String valid = TestTokens.valid(SECRET, 1, USER);
    String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    String expired = TestTokens.sign(SECRET, header,
        "{\"sub\":\"1\",\"username\":\"" + USER + "\",\"iat\":" + (now - 7200)
            + ",\"exp\":" + (now - 3600) + "}");
    String badSub = TestTokens.sign(SECRET, header,
        "{\"sub\":\"abc\",\"username\":\"" + USER + "\",\"iat\":" + now
            + ",\"exp\":" + (now + 3600) + "}");
    String noName = TestTokens.sign(SECRET, header,
        "{\"sub\":\"1\",\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}");
    String noneAlg = TestTokens.sign(SECRET, "{\"alg\":\"none\",\"typ\":\"JWT\"}",
        "{\"sub\":\"1\",\"username\":\"" + USER + "\",\"exp\":" + (now + 3600) + "}");
    String badSig = valid.substring(0, valid.lastIndexOf('.') + 1)
        + "c2lnbmF0dXJlLWlzLW5vdC12YWxpZC1hdC1hbGwtYXQtYWxsLTEyMzQ";
    return Stream.of(
        Arguments.of("no token", null, 401, "{\"error\":\"missing bearer token\"}"),
        Arguments.of("Basic", "Basic abc", 401, "{\"error\":\"missing bearer token\"}"),
        Arguments.of("garbage", "Bearer abc", 401, "{\"error\":\"invalid or expired token\"}"),
        Arguments.of("bad sig", "Bearer " + badSig, 401, "{\"error\":\"invalid or expired token\"}"),
        Arguments.of("none", "Bearer " + noneAlg, 401, "{\"error\":\"invalid or expired token\"}"),
        Arguments.of("expired", "Bearer " + expired, 401, "{\"error\":\"invalid or expired token\"}"),
        Arguments.of("bad sub", "Bearer " + badSub, 401, "{\"error\":\"invalid token payload\"}"),
        Arguments.of("no name", "Bearer " + noName, 401, "{\"error\":\"invalid token payload\"}"));
  }

  @ParameterizedTest(name = "POST /posts {0}")
  @MethodSource("authCases")
  @Order(5)
  @DisplayName("auth matrix")
  void authMatrix(String label, String auth, int status, String body) throws Exception {
    // Act
    Reply reply = post("/posts", auth, "{\"body\":\"x\"}");

    // Assert
    assertEquals(status, reply.status(), label);
    assertEquals(body, reply.body(), label);
  }

  @Test
  @Order(6)
  @DisplayName("auth runs before id")
  void authBeforeId() throws Exception {
    // Act
    Reply noToken = post("/posts/abc/like", null, null);
    Reply withToken = post("/posts/abc/like", "Bearer " + TestTokens.valid(SECRET, 1, USER), null);

    // Assert
    assertEquals(401, noToken.status());
    assertEquals("{\"error\":\"missing bearer token\"}", noToken.body());
    assertEquals(400, withToken.status());
    assertEquals("{\"error\":\"invalid post id\"}", withToken.body());
  }

  static Stream<Arguments> createCases() {
    // Arrange: mirror test/test.sh create-validation rows.
    String long501 = "a".repeat(501);
    return Stream.of(
        Arguments.of("empty", "{\"body\":\"\"}", "{\"error\":\"body is required\"}"),
        Arguments.of("whitespace", "{\"body\":\"   \"}", "{\"error\":\"body is required\"}"),
        Arguments.of("numeric", "{\"body\":123}", "{\"error\":\"body is required\"}"),
        Arguments.of("missing", "{}", "{\"error\":\"body is required\"}"),
        Arguments.of("malformed", "{bad json", "{\"error\":\"malformed JSON body\"}"),
        Arguments.of("501 chars", "{\"body\":\"" + long501 + "\"}",
            "{\"error\":\"body must be at most 500 characters\"}"));
  }

  @ParameterizedTest(name = "create {0}")
  @MethodSource("createCases")
  @Order(7)
  @DisplayName("create validation")
  void createValidation(String label, String body, String expected) throws Exception {
    // Arrange
    String auth = "Bearer " + TestTokens.valid(SECRET, 1, USER);

    // Act
    Reply reply = post("/posts", auth, body);

    // Assert
    assertEquals(400, reply.status(), label);
    assertEquals(expected, reply.body(), label);
  }

  @Test
  @Order(8)
  @DisplayName("create, like, read-back and feed top")
  void createLikeReadBack() throws Exception {
    // Arrange
    String auth = "Bearer " + TestTokens.valid(SECRET, 1, USER);
    int baseLikes = likeCount(readGolden("post-1.json"));

    // Act: special-char round-trip (trimmed).
    Reply special = post("/posts", auth,
        "{\"body\":\"  test: <b>&amp;</b> \\\"quoted\\\" / caf\u00e9 \u2713  \"}");

    // Assert: 201 + trimmed body survives.
    assertEquals(201, special.status(), special.body());
    assertTrue(special.body().contains(
        "\"body\":\"test: <b>&amp;</b> \\\"quoted\\\" / caf\u00e9 \u2713\""), special.body());

    // Act: 500-char create.
    String body500 = "b".repeat(500);
    Reply created = post("/posts", auth, "{\"body\":\"  " + body500 + " \"}");

    // Assert: 201 shape + created_at format + drift.
    assertEquals(201, created.status(), created.body());
    long newId = longField(created.body(), "id");
    String createdAt = stringField(created.body(), "created_at");
    assertTrue(createdAt.matches(
        "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z$"), createdAt);
    long drift = Math.abs(Instant.now().getEpochSecond()
        - Instant.parse(createdAt).getEpochSecond());
    assertTrue(drift <= 60, "drift " + drift);
    assertEquals(
        "{\"post\":{\"id\":" + newId + ",\"body\":\"" + body500 + "\",\"created_at\":\""
            + createdAt + "\",\"author\":\"" + USER + "\",\"like_count\":0}}",
        created.body());
    assertJsonType(created);

    // Act: like first + repeat + missing + bad id.
    Reply like1 = post("/posts/" + newId + "/like", auth, null);
    Reply like2 = post("/posts/" + newId + "/like", auth, null);
    Reply likeMissing = post("/posts/999999999/like", auth, null);
    Reply likeBad = post("/posts/abc/like", auth, null);

    // Assert: 201 then 200, 404, 400.
    assertEquals(201, like1.status());
    assertEquals("{\"liked\":true,\"already_liked\":false,\"post_id\":" + newId + "}", like1.body());
    assertEquals(200, like2.status());
    assertEquals("{\"liked\":true,\"already_liked\":true,\"post_id\":" + newId + "}", like2.body());
    assertEquals(404, likeMissing.status());
    assertEquals("{\"error\":\"post not found\"}", likeMissing.body());
    assertEquals(400, likeBad.status());
    assertEquals("{\"error\":\"invalid post id\"}", likeBad.body());

    // Act: read back + seeded like + feed.
    Reply back = get("/posts/" + newId);
    post("/posts/1/like", auth, null);
    Reply seeded = get("/posts/1");
    Reply feed = get("/feed");

    // Assert: read-back like_count 1, seeded +1, feed 20 + top + entry.
    assertEquals(
        "{\"post\":{\"id\":" + newId + ",\"body\":\"" + body500 + "\",\"created_at\":\""
            + createdAt + "\",\"author\":\"" + USER + "\",\"like_count\":1}}",
        back.body());
    assertEquals(baseLikes + 1, likeCount(seeded.body()));
    assertEquals(20, countOccurrences(feed.body(), "\"like_count\":"));
    assertTrue(feed.body().startsWith("{\"posts\":[{\"id\":" + newId + ","));
    assertTrue(feed.body().contains(
        "{\"id\":" + newId + ",\"body\":\"" + body500 + "\",\"created_at\":\""
            + createdAt + "\",\"author\":\"" + USER + "\",\"like_count\":1}"));
  }

  // HTTP helpers (Act).

  @Test
  @Order(9)
  @DisplayName("a client disconnect releases its descriptor")
  void descriptorReleasedOnPeerFin() throws Exception {
    // Arrange: the worker's own open descriptor count.
    int before = countOpenDescriptors(server.pid());
    Assumptions.assumeTrue(before >= 0, "needs /proc/<pid>/fd (Linux)");

    // Act: one keep-alive request over a raw socket, then close the socket.
    // Closing sends the peer FIN that EPOLLHUP never reports, because the
    // server never shuts its own write side.
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
      socket.getOutputStream()
          .write("GET /health HTTP/1.1\r\nHost: gate\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
      socket.getOutputStream().flush();
      socket.getInputStream().read(new byte[512]);
    }

    // Assert: the descriptor comes back. A leaked one would sit in CLOSE_WAIT
    // until the eviction watermark, and the descriptor budget is the score.
    long deadline = System.currentTimeMillis() + 5000;
    int after = countOpenDescriptors(server.pid());
    while (after > before && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
      after = countOpenDescriptors(server.pid());
    }
    assertTrue(
        after <= before,
        "the peer half-close must release the descriptor: before=" + before + " after=" + after);
  }

  private static Reply get(String path) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
        .timeout(Duration.ofSeconds(10)).GET().build();
    return send(request);
  }

  private static Reply post(String path, String auth, String json) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
        .timeout(Duration.ofSeconds(10));
    if (auth != null) {
      builder.header("Authorization", auth);
    }
    if (json != null) {
      builder.header("Content-Type", "application/json");
      builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
    } else {
      builder.POST(HttpRequest.BodyPublishers.noBody());
    }
    return send(builder.build());
  }

  private static Reply send(HttpRequest request) throws Exception {
    HttpResponse<String> response =
        client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    String type = response.headers().firstValue("content-type").orElse("");
    return new Reply(response.statusCode(), response.body(), type);
  }

  private static void assertJsonType(Reply reply) {
    assertTrue(reply.contentType().startsWith("application/json"), reply.contentType());
  }

  // Arrange helpers.

  private static Path firstExisting(Path... candidates) {
    for (Path p : candidates) {
      if (Files.exists(p)) {
        return p.toAbsolutePath().normalize();
      }
    }
    throw new IllegalStateException("not found");
  }

  private static String readGolden(String name) throws Exception {
    String text = Files.readString(goldenDir.resolve(name), StandardCharsets.UTF_8);
    if (text.endsWith("\n")) {
      text = text.substring(0, text.length() - 1);
    }
    return text;
  }

  private static int freePort() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  /**
   * Counts the worker's open descriptors.
   *
   * @param pid the worker process id
   * @return the count, or -1 when the process's descriptor directory is not readable
   */
  private static int countOpenDescriptors(int pid) throws IOException {
    Path fdDir = Path.of("/proc", String.valueOf(pid), "fd");
    if (!Files.isDirectory(fdDir)) {
      return -1;
    }
    try (Stream<Path> entries = Files.list(fdDir)) {
      return (int) entries.count();
    }
  }

  private static int likeCount(String json) {
    Matcher m = Pattern.compile("\"like_count\":(\\d+)").matcher(json);
    if (!m.find()) {
      throw new IllegalStateException("no like_count");
    }
    return Integer.parseInt(m.group(1));
  }

  private static long longField(String json, String name) {
    Matcher m = Pattern.compile("\"" + name + "\":(\\d+)").matcher(json);
    if (!m.find()) {
      throw new IllegalStateException("no " + name + " in " + json);
    }
    return Long.parseLong(m.group(1));
  }

  private static String stringField(String json, String name) {
    Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(json);
    if (!m.find()) {
      throw new IllegalStateException("no " + name + " in " + json);
    }
    return m.group(1);
  }

  private static int countOccurrences(String text, String part) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(part, index)) >= 0) {
      count++;
      index += part.length();
    }
    return count;
  }
}
