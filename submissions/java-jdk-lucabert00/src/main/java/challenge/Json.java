package challenge;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Compact-JSON writer with SPEC key-order discipline.
 *
 * <p>Single owner for all response bytes in this submission: the post object
 * key order ({@code id,body,created_at,author,like_count}), the feed wrapper
 * ({@code posts}), the single-post wrapper ({@code post}) and the error shape
 * ({@code {"error":"<message>"}}). Output is compact (no spaces or newlines)
 * and UTF-8 encoded by the caller-facing {@code *Bytes} helpers.
 *
 * <p>Stdlib only, no JSON library (decision recorded in the submission README
 * under T04): escaping covers {@code "}, {@code \} and control characters;
 * {@code <>&/} and non-ASCII text pass through raw so UTF-8 round-trips
 * (caf\u00e9/emoji survive byte-identical). {@code created_at} is never
 * reformatted; callers pass the stored text through untouched.
 */
public final class Json {

  private Json() {}

  /**
   * Escapes a string for embedding in compact JSON, without surrounding quotes.
   *
   * @param value the raw string, never null.
   * @return the escaped string without surrounding quotes.
   */
  public static String quote(String value) {
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
   * @param post the row to render, never null.
   * @return the compact post object, without trailing newline.
   */
  public static String postObject(Db.PostRow post) {
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
   * Renders the feed wrapper over already-ordered post objects.
   *
   * @param posts the 20 newest post objects in wire order, never null.
   * @return the exact feed response bytes.
   */
  public static byte[] feed(List<Db.PostRow> posts) {
    StringBuilder out = new StringBuilder(1024 * posts.size());
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
   * @param post the row to render, never null.
   * @return the exact single-post response bytes.
   */
  public static byte[] singlePost(Db.PostRow post) {
    return ("{\"post\":" + postObject(post) + "}").getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Renders the error shape for a fixed SPEC message.
   *
   * @param message the exact error message, never null.
   * @return the exact error response bytes.
   */
  public static byte[] error(String message) {
    return ("{\"error\":\"" + quote(message) + "\"}").getBytes(StandardCharsets.UTF_8);
  }
}
