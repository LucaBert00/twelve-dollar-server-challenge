package challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Gate: compact-JSON writer with SPEC key-order discipline.
 *
 * <p>Arrange-Act-Assert throughout. No database, no server.
 */
@Tag("gate")
@DisplayName("Json writer")
class JsonTest {

  @Test
  @DisplayName("post object uses the exact SPEC key order")
  void postObjectKeyOrder() {
    // Arrange
    Db.PostRow row = new Db.PostRow(1, "hi", "2025-12-31T23:59:19.409Z", "alice", 3);

    // Act
    String out = Json.postObject(row);

    // Assert
    assertEquals(
        "{\"id\":1,\"body\":\"hi\",\"created_at\":\"2025-12-31T23:59:19.409Z\","
            + "\"author\":\"alice\",\"like_count\":3}",
        out);
  }

  @Test
  @DisplayName("quote escapes JSON controls and passes UTF-8 through")
  void quoteEscapesControls() {
    // Arrange
    String raw = "\"\\\n\r\t\u0001<> &/\u00e9\u2713\ud83d\ude00";

    // Act
    String out = Json.quote(raw);

    // Assert
    assertEquals("\\\"\\\\\\n\\r\\t\\u0001<> &/\u00e9\u2713\ud83d\ude00", out);
  }

  @Test
  @DisplayName("feed wrapper joins exactly the given rows")
  void feedWrapper() {
    // Arrange
    List<Db.PostRow> rows =
        List.of(
            new Db.PostRow(2, "b", "2025-01-02T00:00:00.000Z", "u", 0),
            new Db.PostRow(1, "a", "2025-01-01T00:00:00.000Z", "u", 5));

    // Act
    String out = new String(Json.feed(rows), StandardCharsets.UTF_8);

    // Assert
    assertTrue(out.startsWith("{\"posts\":["));
    assertTrue(out.endsWith("]}"));
    assertEquals(
        "{\"posts\":["
            + "{\"id\":2,\"body\":\"b\",\"created_at\":\"2025-01-02T00:00:00.000Z\","
            + "\"author\":\"u\",\"like_count\":0},"
            + "{\"id\":1,\"body\":\"a\",\"created_at\":\"2025-01-01T00:00:00.000Z\","
            + "\"author\":\"u\",\"like_count\":5}]}",
        out);
  }

  @Test
  @DisplayName("single-post and like and error shapes match SPEC")
  void wrappers() {
    // Arrange
    Db.PostRow row = new Db.PostRow(7, "x", "2025-06-01T12:00:00.000Z", "bob", 1);

    // Act
    String single = new String(Json.singlePost(row), StandardCharsets.UTF_8);
    String likeFirst = new String(Json.like(true, false, 7), StandardCharsets.UTF_8);
    String likeRepeat = new String(Json.like(true, true, 7), StandardCharsets.UTF_8);
    String err = new String(Json.error("not found"), StandardCharsets.UTF_8);

    // Assert
    assertEquals(
        "{\"post\":{\"id\":7,\"body\":\"x\",\"created_at\":\"2025-06-01T12:00:00.000Z\","
            + "\"author\":\"bob\",\"like_count\":1}}",
        single);
    assertEquals("{\"liked\":true,\"already_liked\":false,\"post_id\":7}", likeFirst);
    assertEquals("{\"liked\":true,\"already_liked\":true,\"post_id\":7}", likeRepeat);
    assertEquals("{\"error\":\"not found\"}", err);
  }
}
