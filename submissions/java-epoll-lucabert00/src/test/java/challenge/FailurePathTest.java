package challenge;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Gate: the three failure paths that must not amplify a saturated box.
 *
 * <p>Proves the accept-pressure branch does not allocate, the LRU eviction path does
 * not allocate, and a failed {@code COMMIT} answers the exact {@code 500} body to every
 * queued op without closing a socket. The two allocation probes run in child JVMs: the
 * accept probe under a 512-descriptor limit, the eviction probe under {@code -Xint} so
 * escape analysis cannot hide a per-call object.
 */
@Tag("gate")
@DisplayName("failure paths")
@TestMethodOrder(OrderAnnotation.class)
class FailurePathTest {

  /** Exact failure body: {@code Json.error(Http.ERR_INTERNAL)}. */
  private static final String INTERNAL = "{\"error\":\"internal server error\"}";

  private static Path tempDb;

  @BeforeAll
  static void freshCopy() throws Exception {
    // Arrange: Linux sockets and /dev/null are the probe's prerequisites.
    Assumptions.assumeTrue(
        System.getProperty("os.name").toLowerCase().contains("linux"),
        "needs Linux accept4/epoll and /dev/null");
    Path seed = firstExisting(
        Paths.get("..", "..", "seed", "feed.db"),
        Paths.get("seed", "feed.db"),
        Paths.get("submissions", "java-epoll-lucabert00", "..", "..", "seed", "feed.db"));
    tempDb = Files.createTempFile("failure-path-", ".db");
    Files.copy(seed, tempDb, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    Db.init(tempDb.toString());
  }

  @AfterAll
  static void close() throws Exception {
    // Act: release the singleton and remove the copy.
    Db.close();
    if (tempDb != null) {
      deleteSidecars(tempDb);
      Files.deleteIfExists(tempDb);
    }
  }

  @Test
  @Order(1)
  @DisplayName("a failed COMMIT answers 500 for every queued op and closes nothing")
  void commitFailureAnswersEveryQueuedOp() throws Throwable {
    // Arrange: a listening socket plus two registered client connections.
    InetAddress loopback = InetAddress.getLoopbackAddress();
    int port = freePort();
    Server server = new Server(loopback, 0, null);
    int listenFd = Sys.listen(loopback, port, Server.BACKLOG, false);
    int epFd = Sys.epollCreate();
    setIntField(server, "listenFd", listenFd);
    setIntField(server, "epFd", epFd);
    try (Socket a = new Socket(); Socket b = new Socket()) {
      a.connect(new InetSocketAddress(loopback, port), 5000);
      b.connect(new InetSocketAddress(loopback, port), 5000);
      acceptAll(server);
      int[] fds = acceptedFds(server);
      assertEquals(2, fds.length, "both connections must be registered");

      Main.IoHandler io = new Main.IoHandler(System.nanoTime());
      io.attach(server);

      // Arrange: make the foreign key deferred so the INSERT succeeds and COMMIT fails.
      MemorySegment writerDb = (MemorySegment) field(Db.class, "writerDb").get(null);
      assertEquals(Sqlite.OK, execText(writerDb, "PRAGMA defer_foreign_keys=ON"));

      // Act: two ops for a user that does not exist, in one batch.
      io.run(fds[0],
          new Http.WriteOp(Http.WriteKind.CREATE_POST, 999_999_999L, "ghost", "ghost one", 0),
          true);
      io.run(fds[1],
          new Http.WriteOp(Http.WriteKind.CREATE_POST, 999_999_999L, "ghost", "ghost two", 0),
          true);
      long commitsBefore = Db.commitCount();
      flushWrites(io);

      // Assert: the failed COMMIT committed nothing and answered both ops byte-exactly.
      assertEquals(commitsBefore, Db.commitCount(), "a failed COMMIT must not count");
      String expected =
          "HTTP/1.1 500 Internal Server Error\r\n"
              + "Content-Type: application/json\r\n"
              + "Content-Length: " + INTERNAL.length() + "\r\n"
              + "Connection: keep-alive\r\n\r\n"
              + INTERNAL;
      assertEquals(expected, readResponse(a));
      assertEquals(expected, readResponse(b));

      // Assert: no socket was closed, and both descriptors are still usable.
      assertPeerOpen(a);
      assertPeerOpen(b);
      assertEquals(2, intField(server, "activeCount"), "no connection may be closed");
      System.out.println(
          "batch failure: commits " + commitsBefore + " -> " + Db.commitCount()
              + ", 2 responses byte-exact 500, activeCount=2, both peers open");
      io.run(fds[0],
          new Http.WriteOp(Http.WriteKind.CREATE_POST, 1, "golden_ember_1", "still open one", 0),
          true);
      io.run(fds[1],
          new Http.WriteOp(Http.WriteKind.CREATE_POST, 1, "golden_ember_1", "still open two", 0),
          true);
      flushWrites(io);
      assertTrue(readResponse(a).startsWith("HTTP/1.1 201 Created\r\n"),
          "the first connection must still serve after the failed batch");
      assertTrue(readResponse(b).startsWith("HTTP/1.1 201 Created\r\n"),
          "the second connection must still serve after the failed batch");

      for (int fd : fds) {
        server.closeConn(fd);
      }
    } finally {
      Sys.close(listenFd);
      Sys.close(epFd);
    }
  }

  @Test
  @Order(2)
  @DisplayName("LRU eviction allocates nothing under -Xint")
  void evictionPathAllocatesNothing() throws Exception {
    // Act: the child runs a new Object() control, then 10,000 lingerZero calls.
    String output = runChild(
        List.of("-Xint"), List.of(), "challenge.EvictionAllocProbe");

    // Assert: the counter sees the control and the eviction path is an exact zero.
    assertTrue(output.contains("control allocated bytes: "), output);
    assertTrue(output.contains("allocated bytes: 0 over 10000 lingerZero calls"), output);
  }

  @Test
  @Order(3)
  @DisplayName("EMFILE accept pressure allocates nothing")
  void acceptPressureAllocatesNothing() throws Exception {
    // Act: the child fills its descriptor table under ulimit -n 512, then loops on EMFILE.
    String output = runChild(
        List.of(),
        List.of("bash", "-c", "ulimit -n 512; exec \"$@\"", "bash"),
        "challenge.AcceptPressureProbe");

    // Assert: the branch was reached and it allocated an exact zero after warmup.
    assertTrue(output.contains("errno=24 (EMFILE)"), output);
    assertTrue(output.contains("allocated bytes: 0 over 50000 accept-pressure passes"), output);
  }

  // Arrange helpers.

  private static int freePort() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static void acceptAll(Server server) throws Throwable {
    MethodHandle acceptAll =
        MethodHandles.privateLookupIn(Server.class, MethodHandles.lookup())
            .findVirtual(Server.class, "acceptAll", MethodType.methodType(void.class));
    acceptAll.invokeExact(server);
  }

  private static int[] acceptedFds(Server server) throws Exception {
    Server.Conn[] table = (Server.Conn[]) field(Server.class, "table").get(server);
    int[] found = new int[2];
    int count = 0;
    for (Server.Conn conn : table) {
      if (conn != null) {
        if (count == found.length) {
          throw new AssertionError("more than two registered connections");
        }
        found[count++] = conn.fd;
      }
    }
    return Arrays.copyOf(found, count);
  }

  private static void flushWrites(Main.IoHandler io) throws Throwable {
    Method flush = Main.IoHandler.class.getDeclaredMethod("flushWrites");
    flush.setAccessible(true);
    try {
      flush.invoke(io);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException r) {
        throw r;
      }
      if (cause instanceof Error err) {
        throw err;
      }
      throw e;
    }
  }

  private static String runChild(List<String> jvmArgs, List<String> prefix, String mainClass)
      throws Exception {
    String javaBin =
        Paths.get(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java")
            .toString();
    List<String> command = new ArrayList<>(prefix);
    command.add(javaBin);
    command.add("--enable-native-access=ALL-UNNAMED");
    command.addAll(jvmArgs);
    command.add("-cp");
    command.add(System.getProperty("java.class.path"));
    command.add(mainClass);
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(true);
    Process process = builder.start();
    if (!process.waitFor(120, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new AssertionError(mainClass + " did not finish in 120 s");
    }
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    System.out.println("---- " + mainClass + " (exit " + process.exitValue() + ") ----");
    System.out.println(output);
    assertEquals(0, process.exitValue(), mainClass + " failed:\n" + output);
    return output;
  }

  private static Field field(Class<?> type, String name) throws Exception {
    Field field = type.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }

  private static void setIntField(Server server, String name, int value) throws Exception {
    field(Server.class, name).setInt(server, value);
  }

  private static int intField(Server server, String name) throws Exception {
    return field(Server.class, name).getInt(server);
  }

  private static int execText(MemorySegment db, String sql) {
    byte[] utf8 = sql.getBytes(StandardCharsets.UTF_8);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment text = arena.allocate(utf8.length + 1L, 1);
      MemorySegment.copy(utf8, 0, text, JAVA_BYTE, 0L, utf8.length);
      text.set(ValueLayout.JAVA_BYTE, utf8.length, (byte) 0);
      return Sqlite.exec(db, text);
    }
  }

  // Assert helpers.

  /**
   * Reads one complete response off a blocking client socket.
   *
   * @param socket the client side of the connection
   * @return status line, headers and body, exactly as received
   */
  private static String readResponse(Socket socket) throws IOException {
    socket.setSoTimeout(5000);
    InputStream in = socket.getInputStream();
    byte[] buffer = new byte[8192];
    int length = 0;
    for (;;) {
      int read = in.read(buffer, length, buffer.length - length);
      if (read < 0) {
        throw new EOFException(
            "peer closed after " + length + " bytes: "
                + new String(buffer, 0, length, StandardCharsets.ISO_8859_1));
      }
      length += read;
      String text = new String(buffer, 0, length, StandardCharsets.ISO_8859_1);
      int headerEnd = text.indexOf("\r\n\r\n");
      if (headerEnd < 0) {
        if (length == buffer.length) {
          throw new AssertionError("response headers exceed " + buffer.length + " bytes");
        }
        continue;
      }
      int total = headerEnd + 4 + contentLength(text.substring(0, headerEnd));
      if (total > buffer.length) {
        throw new AssertionError("response exceeds " + buffer.length + " bytes");
      }
      if (length >= total) {
        return text.substring(0, total);
      }
    }
  }

  private static int contentLength(String head) {
    Matcher matcher = Pattern.compile("(?i)\\r\\nContent-Length: (\\d+)").matcher(head);
    if (!matcher.find()) {
      throw new AssertionError("no Content-Length in: " + head);
    }
    return Integer.parseInt(matcher.group(1));
  }

  /**
   * Asserts the peer kept the connection open.
   *
   * <p>An open socket with no pending bytes times out. A closed socket returns EOF
   * immediately, which is what a failure path that closed the connection would do.
   *
   * @param socket the client side of the connection
   */
  private static void assertPeerOpen(Socket socket) throws IOException {
    socket.setSoTimeout(300);
    try {
      int read = socket.getInputStream().read();
      fail("the socket must stay open, but read returned " + read);
    } catch (SocketTimeoutException expected) {
      // Open with no pending bytes: the failure path answered and kept the connection.
    } finally {
      socket.setSoTimeout(5000);
    }
  }

  // Arrange helpers from the sibling suites.

  private static Path firstExisting(Path... candidates) {
    for (Path path : candidates) {
      if (Files.exists(path)) {
        return path.toAbsolutePath().normalize();
      }
    }
    throw new IllegalStateException("seed not found from " + Arrays.toString(candidates));
  }

  private static void deleteSidecars(Path db) throws Exception {
    for (String suffix : new String[] {"-wal", "-shm", "-journal"}) {
      Files.deleteIfExists(Paths.get(db.toString() + suffix));
    }
  }
}
