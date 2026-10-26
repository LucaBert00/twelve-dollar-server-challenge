package challenge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Gate: the feed fast path and the immutable reference SQL agree byte for byte.
 *
 * <p>Each case builds its own database, opens {@link Db} on it, renders the chosen path and the
 * reference path, and compares the feed bytes. The production path is never run twice; this
 * comparison is a correctness test, not a benchmark. The seed case also proves the golden bytes.
 */
@Tag("gate")
@DisplayName("Feed fast path")
class FeedPathTest {

  private static Path tempDir;
  private static Path seedDb;
  private static Path goldenDir;
  private static String schemaSql;
  private static String goldenFeed;

  @BeforeAll
  static void arrange() throws Exception {
    // Arrange: locate the repo assets from the module or the repo root.
    seedDb =
        firstExisting(
            Paths.get("..", "..", "seed", "feed.db"),
            Paths.get("seed", "feed.db"));
    goldenDir =
        firstExisting(Paths.get("..", "..", "test", "golden"), Paths.get("test", "golden"));
    Path schema =
        firstExisting(
            Paths.get("..", "..", "schema.sql"),
            Paths.get("schema.sql"));
    schemaSql = Files.readString(schema, StandardCharsets.UTF_8);
    goldenFeed = readGolden("feed.json");
    tempDir = Files.createTempDirectory("feed-path-");
  }

  @AfterAll
  static void clean() throws Exception {
    Db.close();
    deleteTree(tempDir);
  }

  @Test
  @DisplayName("the oracle agrees on every listed feed shape")
  void oracleAgreesAcrossEveryCase() throws Exception {
    // Arrange: seed plus controlled shapes. The seed's newest 20 ids are contiguous;
    // every other case is built from schema.sql so it can be mutated freely.
    List<Case> cases = new ArrayList<>();
    cases.add(new Case("seed", copySeed(), 20, true));
    cases.add(new Case("fresh", freshDb("fresh"), 0, false));

    Path ties = freshDb("equal-created-at");
    insertPostsAt(ties, ids(25), "2025-01-01T00:00:00.000Z");
    cases.add(new Case("equal-created-at", ties, 20, true));

    Path outlier = freshDb("created-at-dominates");
    insertPostsAt(outlier, ids(25), "2025-01-01T00:00:00.000Z");
    TestSqlite.exec(
        outlier, "UPDATE posts SET created_at='2025-01-01T00:00:01.000Z' WHERE id=10");
    cases.add(new Case("created-at-dominates", outlier, 20, true));

    Path gap = freshDb("id-gap");
    insertPosts(gap, concat(ids(19), List.of(21)));
    cases.add(new Case("id-gap", gap, 20, false));

    Path missing = freshDb("missing-row");
    insertPosts(missing, ids(21));
    deletePost(missing, 11);
    cases.add(new Case("missing-row", missing, 20, false));

    for (int n : new int[] {19, 20, 21}) {
      Path boundary = freshDb("boundary-" + n);
      insertPosts(boundary, ids(n));
      cases.add(new Case("boundary-" + n, boundary, n == 19 ? 19 : 20, n != 19));
    }

    Path outside = freshDb("gap-outside-window");
    insertPosts(outside, ids(22));
    deletePost(outside, 1);
    cases.add(new Case("gap-outside-window", outside, 20, true));

    // Act plus Assert: every case renders the chosen path and the reference path.
    int mismatches = 0;
    boolean fastExercised = false;
    boolean fallbackExercised = false;
    for (Case testCase : cases) {
      Db.close();
      Db.init(testCase.db().toString());
      try {
        Optional<List<Db.PostRow>> fastPath = Db.feedFastPath();
        List<Db.PostRow> chosen = Db.feed();
        List<Db.PostRow> reference = Db.feedReference();
        byte[] chosenBytes = Json.feed(chosen);
        byte[] referenceBytes = Json.feed(reference);

        boolean agree = Arrays.equals(chosenBytes, referenceBytes);
        if (!agree) {
          mismatches++;
          System.out.println("feed oracle: mismatch on " + testCase.name());
        }
        assertEquals(testCase.expectedRows(), chosen.size(), testCase.name() + " row count");
        assertEquals(
            testCase.expectFast(), fastPath.isPresent(), testCase.name() + " path selection");
        if (testCase.name().equals("seed")) {
          assertEquals(goldenFeed, new String(chosenBytes, StandardCharsets.UTF_8), "seed golden");
          System.out.println("feed golden: matches");
        }
        System.out.println(
            "case "
                + testCase.name()
                + ": "
                + (fastPath.isPresent() ? "fast" : "fallback")
                + " rows="
                + chosen.size());
        if (fastPath.isPresent()) {
          fastExercised = true;
        } else {
          fallbackExercised = true;
        }
      } finally {
        Db.close();
      }
    }

    // Assert: zero mismatches, and both paths were really reached.
    System.out.println("feed oracle: mismatches=" + mismatches);
    System.out.println("fast path: " + (fastExercised ? "exercised" : "NOT exercised"));
    System.out.println("fallback: " + (fallbackExercised ? "exercised" : "NOT exercised"));
    assertEquals(0, mismatches, "the fast path and the reference SQL must agree");
    assertTrue(fastExercised, "the fast path must be exercised");
    assertTrue(fallbackExercised, "the fallback must be exercised");
  }

  @Test
  @DisplayName("created_at orders first and id breaks ties")
  void orderIsCreatedAtThenIdDesc() throws Exception {
    // Arrange: 25 posts tie at T; id 10 is one second newer. The top 20 ids stay the
    // contiguous 6..25 set, but the reference order starts at 10, not at 25.
    Path db = freshDb("order");
    insertPostsAt(db, ids(25), "2025-01-01T00:00:00.000Z");
    TestSqlite.exec(db, "UPDATE posts SET created_at='2025-01-01T00:00:01.000Z' WHERE id=10");
    Db.close();
    Db.init(db.toString());
    try {
      // Act
      assertTrue(Db.feedFastPath().isPresent(), "the outlier set is still contiguous");
      List<Long> chosen = Db.feed().stream().map(Db.PostRow::id).toList();
      List<Long> reference = Db.feedReference().stream().map(Db.PostRow::id).toList();

      // Assert: created_at dominates id, and ties fall back to id DESC.
      List<Long> expected = new ArrayList<>();
      expected.add(10L);
      for (long id = 25; id >= 11; id--) {
        expected.add(id);
      }
      for (long id = 9; id >= 6; id--) {
        expected.add(id);
      }
      assertEquals(expected, chosen);
      assertEquals(chosen, reference);
      assertArrayEquals(
          Json.feed(Db.feedReference()),
          Json.feed(Db.feed()),
          "the two paths must agree on the ordered bytes");
    } finally {
      Db.close();
    }
  }

  private record Case(String name, Path db, int expectedRows, boolean expectFast) {}

  private static List<Integer> ids(int count) {
    return IntStream.rangeClosed(1, count).boxed().toList();
  }

  private static List<Integer> concat(List<Integer> left, List<Integer> right) {
    List<Integer> out = new ArrayList<>(left);
    out.addAll(right);
    return out;
  }

  private static Path copySeed() throws Exception {
    Path db = tempDir.resolve("seed.db");
    Files.copy(seedDb, db, StandardCopyOption.REPLACE_EXISTING);
    return db;
  }

  private static Path freshDb(String name) throws Exception {
    Path db = tempDir.resolve(name + ".db");
    Files.deleteIfExists(db);
    TestSqlite.exec(db, schemaSql);
    return db;
  }

  private static void insertPosts(Path db, List<Integer> ids) {
    insertPosts(db, ids, id -> String.format("2025-01-01T00:00:%02d.000Z", id));
  }

  private static void insertPostsAt(Path db, List<Integer> ids, String timestamp) {
    insertPosts(db, ids, id -> timestamp);
  }

  private static void insertPosts(Path db, List<Integer> ids, IntFunction<String> timestamp) {
    StringBuilder sql =
        new StringBuilder("INSERT INTO users (id, username) VALUES (1,'u1'),(2,'u2'),(3,'u3');");
    for (int id : ids) {
      sql.append("INSERT INTO posts (id,user_id,body,created_at) VALUES (")
          .append(id)
          .append(',')
          .append((id % 3) + 1)
          .append(",'p")
          .append(id)
          .append("','")
          .append(timestamp.apply(id))
          .append("');");
    }
    int top = ids.stream().mapToInt(Integer::intValue).max().orElse(0);
    sql.append("INSERT INTO likes (user_id,post_id) VALUES (1,")
        .append(top)
        .append("),(2,")
        .append(top)
        .append(')');
    if (ids.contains(10)) {
      sql.append(",(1,10)");
    }
    sql.append(';');
    TestSqlite.exec(db, sql.toString());
  }

  private static void deletePost(Path db, int id) {
    TestSqlite.exec(
        db, "DELETE FROM likes WHERE post_id=" + id + "; DELETE FROM posts WHERE id=" + id + ";");
  }

  private static Path firstExisting(Path... candidates) {
    for (Path p : candidates) {
      if (Files.exists(p)) {
        return p.toAbsolutePath().normalize();
      }
    }
    throw new IllegalStateException(
        "repo asset not found from " + Arrays.toString(candidates));
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

  private static void deleteTree(Path dir) throws Exception {
    if (dir == null || !Files.exists(dir)) {
      return;
    }
    try (var paths = Files.walk(dir)) {
      for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(p);
      }
    }
  }
}
