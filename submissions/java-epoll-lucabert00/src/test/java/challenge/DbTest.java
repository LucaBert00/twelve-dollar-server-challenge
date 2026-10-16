package challenge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 * before writes (order mirrors {@code test/test.sh}). The epoll worker reaches
 * SQLite through FFM (no JDBC): the pragma block is asserted fail-fast inside
 * {@link Db#init}, so opening the fresh copy without a throw is the pragma
 * proof; writes open with {@code BEGIN IMMEDIATE} and answer only after
 * {@code COMMIT}.
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
        Paths.get("submissions", "java-epoll-lucabert00", "..", "..", "seed", "feed.db"));
    goldenDir = firstExisting(
        Paths.get("..", "..", "test", "golden"),
        Paths.get("test", "golden"));
    tempDb = Files.createTempFile("gate-db-", ".db");
    Files.copy(seedDb, tempDb, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

    // Act: init the writer plus reader handles on the fresh copy.
    // Db.init asserts the full pragma block (WAL, synchronous=NORMAL,
    // foreign_keys=ON, busy_timeout, wal_autocheckpoint=0, journal_size_limit,
    // mmap_size, cache_size, temp_store) and throws on any drift.
    assertDoesNotThrow(() -> Db.init(tempDb.toString()));
  }

  @AfterAll
  static void close() throws Exception {
    // Act: release the handles and remove the copy.
    Db.close();
    if (tempDb != null) {
      deleteSidecars(tempDb);
      Files.deleteIfExists(tempDb);
    }
  }

  @Test
  @Order(1)
  @DisplayName("fail-fast posture holds on a fresh copy")
  void posture() throws Exception {
    // Act: liveness probe on the reader handle (no throw).
    assertDoesNotThrow(Db::checkHealth);

    // Assert: writer and reader fail fast instead of stalling the loop,
    // and a failed batch answers 500 for every op it held.
    assertEquals(0, Db.WRITER_BUSY_TIMEOUT_MS);
    assertEquals(0, Db.READER_BUSY_TIMEOUT_MS);
    assertEquals(500, Db.BATCH_FAILURE_STATUS);
  }

  @Test
  @Order(2)
  @DisplayName("app issues no DDL")
  void noDdl() throws Exception {
    // Act: every statement the layer prepares.
    List<String> statements = List.of(
        Db.SELECT_POST_BASE,
        Db.FEED_SQL,
        Db.GET_POST_SQL,
        Db.CREATE_POST_SQL,
        Db.LIKE_UPSERT_SQL,
        Db.HEALTH_SQL,
        Db.POST_EXISTS_SQL);

    // Assert: reads and writes only; rule 3 forbids adding or changing
    // tables, columns, indexes or triggers, so no DDL may appear.
    for (String sql : statements) {
      String upper = sql.toUpperCase();
      assertTrue(!upper.contains("CREATE") && !upper.contains("ALTER")
          && !upper.contains("DROP") && !upper.contains("TRIGGER")
          && !upper.contains("INDEX"), "DDL in: " + sql);
    }
    assertTrue(Db.FEED_SQL.contains("ORDER BY p.created_at DESC, p.id DESC LIMIT 20"));
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
    // Act
    long value = Http.parsePostId(raw);

    // Assert
    assertEquals(-1, value);
  }

  @Test
  @Order(6)
  @DisplayName("create, like, read-back and feed top")
  void createLikeReadBack() throws Exception {
    // Arrange: golden like_count for the seeded post.
    int baseLikes = likeCount(readGolden("post-1.json"));

    // Act: create a 500-char post (trimmed, like_count 0); the response
    // waits for COMMIT before it may be answered.
    String body500 = "b".repeat(500);
    assertTrue(Db.tryBeginImmediate());
    Db.CreatedPost created = Db.insertPostInTxn(1, body500);
    assertTrue(Db.commitWrites());
    long newId = created.id();

    // Assert: create shape (author comes from the token, as the handler passes it).
    assertTrue(newId > 0);
    assertTrue(created.createdAt().matches(
        "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z$"));

    // Act: like first (false) then repeat (true), each committed before answering.
    assertTrue(Db.tryBeginImmediate());
    Optional<Boolean> first = Db.insertLikeInTxn(1, newId);
    assertTrue(Db.commitWrites());
    assertTrue(Db.tryBeginImmediate());
    Optional<Boolean> repeat = Db.insertLikeInTxn(1, newId);
    assertTrue(Db.commitWrites());

    // Assert: idempotent like shapes.
    assertTrue(first.isPresent() && !first.get());
    assertTrue(repeat.isPresent() && repeat.get());

    // Act: read back + seeded like + feed.
    Optional<Db.PostRow> back = Db.getPost(newId);
    assertTrue(Db.tryBeginImmediate());
    Db.insertLikeInTxn(1, 1);
    assertTrue(Db.commitWrites());
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
