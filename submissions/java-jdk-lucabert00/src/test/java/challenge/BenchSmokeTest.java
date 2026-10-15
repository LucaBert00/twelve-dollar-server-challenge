package challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Bench tag separation (no benchmarking in this ticket).
 *
 * <p>Gate runs carry {@code @Tag("gate")}; bench-only work carries
 * {@code @Tag("bench")} and is owned by later tickets. This smoke proves the
 * tag exists and the serialization shape used by bench flows stays valid.
 */
@Tag("bench")
@DisplayName("Bench smoke")
class BenchSmokeTest {

  @Test
  @DisplayName("feed shape renders 20 rows")
  void feedShape() {
    // Arrange
    List<Db.PostRow> rows = new ArrayList<>();
    for (int i = 20; i >= 1; i--) {
      rows.add(new Db.PostRow(i, "b" + i, "2025-01-01T00:00:00.000Z", "u", 0));
    }

    // Act
    String out = new String(Json.feed(rows), StandardCharsets.UTF_8);

    // Assert
    assertTrue(out.startsWith("{\"posts\":["));
    assertEquals(20, count(out, "\"like_count\":"));
  }

  private static int count(String text, String part) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(part, index)) >= 0) {
      count++;
      index += part.length();
    }
    return count;
  }
}
