package challenge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Gate: database layer on a fresh seed copy (goldens, pragmas, schema, writes).
 *
 * <p>Arrange-Act-Assert throughout. Uses one fresh copy per run; golden reads run
 * before writes (order mirrors {@code test/test.sh}).
 */
@Tag("gate")
@DisplayName("Db layer")
@TestMethodOrder(OrderAnnotation.class)
class DbTest {

  private static Path tempDb;
  private static Path seedDb;
  private static Path goldenDir;

  @BeforeAll
  static void freshCopy() throws Exception {
    // Arrange: locate the seed and goldens from the module or repo root.
    seedDb = firstExisting(
        Paths.get("..", "..", "seed", "feed.db"),
        Paths.get("seed", "feed.db"),
        Paths.get("submissions", "java-jdk-lucabert00", "..", "..", "seed", "feed.db"));
    goldenDir = firstExisting(
        Paths.get("..", "..", "test", "golden"),
        Paths.get("test", "golden"));
    tempDb = Files.createTempFile("gate-db-", ".db");
    Files.copy(seedDb, tempDb, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    // Act: init the pool on the fresh copy.
    Db.init(tempDb.toString());
  }

  @AfterAll
  static void close() throws Exception {
    // Act: release the pool and remove the copy.
    Db.close();
    deleteSidecars(tempDb);
    Files.deleteIfExists(tempDb);
  }

  @Test
  @Order(1)
  @DisplayName("pool holds WAL + NORMAL + FK + busy on every conn")
  void pragmas() throws Exception {
    // Arrange
    HikariDataSource pool = pool();

    // Act + Assert: all four pooled conns hold the floor.
    List<Connection> held = new ArrayList<>();
    try {
      for (int i = 0; i < Db.POOL_SIZE; i++) {
        held.add(pool.getConnection());
      }
      for (Connection c : held) {
        assertEquals("wal", pragma(c, "journal_mode").toLowerCase());
        assertEquals("1", pragma(c, "synchronous"));
        assertEquals("1", pragma(c, "foreign_keys"));
        assertEquals(String.valueOf(Db.BUSY_TIMEOUT_MS), pragma(c, "busy_timeout"));
      }
    } finally {
      for (Connection c : held) {
        c.close();
      }
    }
  }

  @Test
  @Order(2)
  @DisplayName("health probe and schema diff")
  void healthAndSchema() throws Exception {
    // Act: liveness probe (no throw) + schema read.
    assertDoesNotThrow(Db::checkHealth);

    // Assert: user tables and required indexes only (plus SQLite internals).
    List<String> tables = names("table");
    assertTrue(tables.containsAll(List.of("users", "posts", "likes")));
    for (String t : tables) {
      assertTrue(
          t.equals("users") || t.equals("posts") || t.equals("likes")
              || t.equals("sqlite_stat1") || t.startsWith("sqlite_"),
          "unexpected table " + t);
    }
    List<String> indexes = names("index");
    assertTrue(indexes.containsAll(
        List.of("posts_created_at_id_idx", "posts_user_id_idx", "likes_post_id_idx")));
  }

  @Test
  @Order(3)
  @DisplayName("feed matches the golden bytes")
  void feedGolden() throws Exception {
    // Arrange
    String golden = readGolden("feed.json");

    // Act
    List<Db.PostRow> rows = Db.feed();
    String out = new String(Json.feed(rows), StandardCharsets.UTF_8);

    // Assert
    assertEquals(20, rows.size());
    assertEquals(golden, out);
  }

  @ParameterizedTest(name = "post {0} matches golden")
  @ValueSource(longs = {1, 250000, 500000})
  @Order(4)
  @DisplayName("single posts match golden bytes")
  void singleGolden(long id) throws Exception {
    // Arrange
    String golden = readGolden("post-" + id + ".json");

    // Act
    Optional<Db.PostRow> row = Db.getPost(id);

    // Assert
    assertTrue(row.isPresent());
    assertEquals(golden, new String(Json.singlePost(row.get()), StandardCharsets.UTF_8));
  }

  @ParameterizedTest(name = "id [{0}] is invalid")
  @ValueSource(strings = {"0", "-1", "abc", "1.5"})
  @Order(5)
  @DisplayName("id matrix rejects non-positive integers")
  void idMatrix(String raw) throws Exception {
    // Arrange
    Method parse = Main.class.getDeclaredMethod("parsePostId", String.class);
    parse.setAccessible(true);

    // Act
    long value = (long) parse.invoke(null, raw);

    // Assert
    assertEquals(-1, value);
  }

  @Test
  @Order(6)
  @DisplayName("create, like, read-back and feed top")
  void createLikeReadBack() throws Exception {
    // Arrange: golden like_count for the seeded post.
    int baseLikes = likeCount(readGolden("post-1.json"));

    // Act: create a 500-char post (trimmed, like_count 0).
    String body500 = "b".repeat(500);
    Db.PostRow created = Db.createPost(1, body500, "golden_ember_1");
    long newId = created.id();

    // Assert: create shape.
    assertEquals(body500, created.body());
    assertEquals("golden_ember_1", created.author());
    assertEquals(0, created.likeCount());
    assertTrue(created.createdAt().matches(
        "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z$"));

    // Act: like first (false) then repeat (true).
    Optional<Boolean> first = Db.likePost(1, newId);
    Optional<Boolean> repeat = Db.likePost(1, newId);

    // Assert: idempotent like shapes.
    assertTrue(first.isPresent() && !first.get());
    assertTrue(repeat.isPresent() && repeat.get());

    // Act: read back + seeded like + feed.
    Optional<Db.PostRow> back = Db.getPost(newId);
    Db.likePost(1, 1);
    Optional<Db.PostRow> seeded = Db.getPost(1);
    List<Db.PostRow> feed = Db.feed();

    // Assert: read-back like_count 1, seeded +1, feed top matches.
    assertTrue(back.isPresent() && back.get().likeCount() == 1);
    assertTrue(seeded.isPresent() && seeded.get().likeCount() == baseLikes + 1);
    assertEquals(20, feed.size());
    assertEquals(newId, feed.get(0).id());
    assertEquals(
        "{\"id\":" + newId + ",\"body\":\"" + body500 + "\",\"created_at\":\""
            + created.createdAt() + "\",\"author\":\"golden_ember_1\",\"like_count\":1}",
        Json.postObject(feed.get(0)));
  }

  // Helpers (Arrange).

  private static Path firstExisting(Path... candidates) {
    for (Path p : candidates) {
      if (Files.exists(p)) {
        return p.toAbsolutePath().normalize();
      }
    }
    throw new IllegalStateException("seed/golden not found from " + java.util.Arrays.toString(candidates));
  }

  private static String readGolden(String name) throws Exception {
    String text = Files.readString(goldenDir.resolve(name), StandardCharsets.UTF_8);
    if (text.endsWith("\n")) {
      text = text.substring(0, text.length() - 1);
    }
    if (text.endsWith("\r")) {
      text = text.substring(0, text.length() - 1);
    }
    return text;
  }

  private static HikariDataSource pool() throws Exception {
    Field field = Db.class.getDeclaredField("pool");
    field.setAccessible(true);
    return (HikariDataSource) field.get(null);
  }

  private static String pragma(Connection c, String name) throws Exception {
    try (Statement s = c.createStatement();
        ResultSet rs = s.executeQuery("PRAGMA " + name)) {
      rs.next();
      return rs.getString(1);
    }
  }

  private static List<String> names(String type) throws Exception {
    HikariDataSource pool = pool();
    try (Connection c = pool.getConnection();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(
            "SELECT name FROM sqlite_master WHERE type='" + type + "' ORDER BY name")) {
      List<String> out = new ArrayList<>();
      while (rs.next()) {
        out.add(rs.getString(1));
      }
      return out;
    }
  }

  private static int likeCount(String postJson) {
    Matcher m = Pattern.compile("\"like_count\":(\\d+)").matcher(postJson);
    if (!m.find()) {
      throw new IllegalStateException("no like_count in " + postJson);
    }
    return Integer.parseInt(m.group(1));
  }

  private static void deleteSidecars(Path db) throws Exception {
    for (String suffix : new String[] {"-wal", "-shm", "-journal"}) {
      Files.deleteIfExists(Paths.get(db.toString() + suffix));
    }
  }
}
