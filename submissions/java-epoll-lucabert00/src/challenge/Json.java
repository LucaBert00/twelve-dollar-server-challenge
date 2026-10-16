package challenge;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Compact-JSON writer with SPEC key-order discipline.
 *
 * <p>Single owner for all JSON body bytes in this submission: the post object key order
 * ({@code id,body,created_at,author,like_count}), the feed wrapper ({@code posts}), the
 * single-post wrapper ({@code post}), the like shape, the health shapes and the error shape
 * ({@code {"error":"<message>"}}). Output is compact (no spaces or newlines) and UTF-8 encoded
 * by the caller-facing helpers.
 *
 * <p>Stdlib only, no JSON library: escaping covers {@code "}, {@code \} and control characters;
 * {@code <>&/} and non-ASCII text pass through raw so UTF-8 round-trips. {@code created_at} is
 * never reformatted; callers pass the stored text through untouched. {@code like_count} is always
 * rendered from the live row handed in, never from a held copy.
 */
final class Json {

  private Json() {}

  /**
   * Escapes a string for embedding in compact JSON, without surrounding quotes.
   *
   * @param value the raw string, never null
   * @return the escaped string without surrounding quotes
   */
  static String quote(String value) {
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
   * Renders one post object with exact SPEC key order.
   *
   * @param post the row to render, never null
   * @return the compact post object, without trailing newline
   */
  static String postObject(Db.PostRow post) {
    return "{\"id\":"
        + post.id()
        + ",\"body\":\""
        + quote(post.body())
        + "\",\"created_at\":\""
        + quote(post.createdAt())
        + "\",\"author\":\""
        + quote(post.author())
        + "\",\"like_count\":"
        + post.likeCount()
        + "}";
  }

  /**
   * Renders the feed wrapper over already-ordered rows.
   *
   * @param posts the 20 newest rows in wire order, never null
   * @return the exact feed response bytes
   */
  static byte[] feed(List<Db.PostRow> posts) {
    StringBuilder out = new StringBuilder(1024 * Math.max(posts.size(), 1));
    out.append("{\"posts\":[");
    for (int i = 0; i < posts.size(); i++) {
      if (i > 0) {
        out.append(',');
      }
      out.append(postObject(posts.get(i)));
    }
    out.append("]}");
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Renders the single-post wrapper.
   *
   * @param post the row to render, never null
   * @return the exact single-post response bytes
   */
  static byte[] singlePost(Db.PostRow post) {
    return ("{\"post\":" + postObject(post) + "}").getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Renders the like response with exact SPEC key order.
   *
   * @param liked always true per SPEC
   * @param alreadyLiked true on repeats, false on the first like
   * @param postId the liked post id
   * @return the exact like response bytes
   */
  static byte[] like(boolean liked, boolean alreadyLiked, long postId) {
    return ("{\"liked\":"
            + liked
            + ",\"already_liked\":"
            + alreadyLiked
            + ",\"post_id\":"
            + postId
            + "}")
        .getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Renders the health shape after a {@code SELECT 1} probe succeeds.
   *
   * @param uptimeS whole seconds since the worker started
   * @return the exact health response bytes
   */
  static byte[] health(long uptimeS) {
    return ("{\"status\":\"ok\",\"db\":\"ok\",\"uptime_s\":" + uptimeS + "}")
        .getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Renders the degraded shape when the {@code SELECT 1} probe throws.
   *
   * @param message the probe failure text, never null
   * @return the exact degraded response bytes
   */
  static byte[] degraded(String message) {
    return ("{\"status\":\"degraded\",\"db\":\"unreachable\",\"error\":\""
            + quote(message)
            + "\"}")
        .getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Renders the error shape for a fixed SPEC message.
   *
   * @param message the exact error message, never null
   * @return the exact error response bytes
   */
  static byte[] error(String message) {
    return ("{\"error\":\"" + quote(message) + "\"}").getBytes(StandardCharsets.UTF_8);
  }
}
