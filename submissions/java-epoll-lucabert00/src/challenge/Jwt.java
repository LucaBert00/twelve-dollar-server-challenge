package challenge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HS256 per-request JWT verifier for the two authed routes.
 *
 * <p>Stdlib only ({@code javax.crypto.Mac} HMAC-SHA256 plus {@code java.util.Base64} URL
 * decoding). The signature and {@code exp} are checked on every authed request; no verdict is
 * held between requests. The only per-thread state is the {@code Mac} instance itself, which
 * is allowed by name in the architecture (reuse the primitive, never the decision).
 *
 * <p>Exact 401 shapes (SPEC Errors table): absent or non-{@code Bearer} header becomes
 * {@code missing bearer token}; bad signature, {@code none} or wrong {@code alg}, expired or
 * malformed token becomes {@code invalid or expired token}; valid signature but {@code sub}
 * not a positive-integer string or {@code username} not a string becomes {@code invalid token
 * payload}.
 */
final class Jwt {

  /**
   * Plug-in verifier for {@link Http#handle}.
   *
   * <p>{@link Http#requireAuth} rejects the missing-bearer shape inline and calls this for
   * everything else, so auth-before-id holds regardless of the caller.
   */
  static final Http.AuthCheck CHECK = Jwt::verify;

  private static volatile SecretKeySpec key;

  private static final ThreadLocal<Mac> MAC = new ThreadLocal<>();

  private Jwt() {}

  /**
   * Initializes the verifier with the shared JWT secret.
   *
   * @param secret the {@code JWT_SECRET} value, never null or empty
   * @throws IllegalArgumentException when the secret is null or empty
   */
  static void init(String secret) {
    if (secret == null || secret.isEmpty()) {
      throw new IllegalArgumentException("JWT_SECRET must not be empty");
    }
    key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    MAC.remove();
  }

  /**
   * Verifies one {@code Authorization} header value per request.
   *
   * @param authorization the raw header value, may be null
   * @return the authenticated user, never null
   * @throws Http.AuthFailure with the exact SPEC 401 text on any failure
   */
  static Http.User verify(String authorization) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new Http.AuthFailure("missing bearer token");
    }
    String[] parts = authorization.substring(7).split("\\.", -1);
    if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
      throw new Http.AuthFailure("invalid or expired token");
    }
    Map<String, Object> payload;
    try {
      String headerJson = new String(b64urlDecode(parts[0]), StandardCharsets.UTF_8);
      String payloadJson = new String(b64urlDecode(parts[1]), StandardCharsets.UTF_8);
      Map<String, Object> header = parseObject(headerJson);
      Object alg = header.get("alg");
      if (!"HS256".equals(alg) || header.containsKey("crit") || header.containsKey("b64")) {
        throw new IllegalArgumentException("alg");
      }
      SecretKeySpec current = key;
      if (current == null) {
        throw new IllegalArgumentException("no key");
      }
      Mac mac = MAC.get();
      if (mac == null) {
        mac = Mac.getInstance("HmacSHA256");
        MAC.set(mac);
      }
      mac.init(current);
      byte[] expected =
          mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
      if (!MessageDigest.isEqual(expected, b64urlDecode(parts[2]))) {
        throw new IllegalArgumentException("sig");
      }
      payload = parseObject(payloadJson);
      Object exp = payload.get("exp");
      if (!(exp instanceof Number)
          || ((Number) exp).doubleValue() <= Instant.now().getEpochSecond()) {
        throw new IllegalArgumentException("exp");
      }
    } catch (Http.AuthFailure e) {
      throw e;
    } catch (Exception e) {
      throw new Http.AuthFailure("invalid or expired token");
    }
    Object sub = payload.get("sub");
    Object username = payload.get("username");
    if (!(sub instanceof String) || !(username instanceof String)) {
      throw new Http.AuthFailure("invalid token payload");
    }
    try {
      return new Http.User(positiveInteger((String) sub), (String) username);
    } catch (IllegalArgumentException e) {
      throw new Http.AuthFailure("invalid token payload");
    }
  }

  /**
   * Parses a positive integer string into a {@code long}.
   *
   * @param text the raw text, never null
   * @return the value, at least 1
   * @throws IllegalArgumentException when the text is not digits or is out of range
   */
  static long positiveInteger(String text) {
    if (!text.matches("[0-9]+")) {
      throw new IllegalArgumentException("not digits");
    }
    long value = Long.parseLong(text);
    if (value <= 0) {
      throw new IllegalArgumentException("not positive");
    }
    return value;
  }

  /**
   * Parses a flat JSON object into string keys and typed values.
   *
   * <p>Values decode as {@code String}, {@code Number}, {@code Boolean}, null, or a generic
   * marker for nested objects/arrays (never a {@code String} or {@code Number}, so field-type
   * checks fail closed). Any malformed input throws.
   *
   * @param json the raw object text, never null
   * @return the parsed entries, never null
   * @throws IllegalArgumentException when the text is not a JSON object
   */
  static Map<String, Object> parseObject(String json) {
    Parser parser = new Parser(json);
    Map<String, Object> out = parser.parseObject();
    parser.skipWs();
    if (!parser.atEnd()) {
      throw new IllegalArgumentException("trailing");
    }
    return out;
  }

  /**
   * Decodes unpadded base64url, adding the missing padding first.
   *
   * @param text the encoded text, never null
   * @return the decoded bytes
   * @throws IllegalArgumentException when the text is not valid base64url
   */
  private static byte[] b64urlDecode(String text) {
    String padded = text;
    int remainder = padded.length() % 4;
    if (remainder == 1) {
      throw new IllegalArgumentException("bad length");
    }
    if (remainder != 0) {
      padded += "=".repeat(4 - remainder);
    }
    return Base64.getUrlDecoder().decode(padded);
  }

  /** Tiny JSON-object parser for verifier shapes only. */
  private static final class Parser {
    private final String text;
    private int pos;

    /**
     * Creates a parser over the given text.
     *
     * @param text the raw JSON text, never null
     */
    Parser(String text) {
      this.text = text;
    }

    /**
     * Parses one top-level object.
     *
     * @return the entries, never null
     */
    Map<String, Object> parseObject() {
      skipWs();
      expect('{');
      Map<String, Object> out = new HashMap<>();
      skipWs();
      if (peek() == '}') {
        pos++;
        return out;
      }
      while (true) {
        skipWs();
        String key = parseString();
        skipWs();
        expect(':');
        skipWs();
        out.put(key, parseValue());
        skipWs();
        char ch = peek();
        if (ch == ',') {
          pos++;
          continue;
        }
        if (ch == '}') {
          pos++;
          return out;
        }
        throw new IllegalArgumentException("object");
      }
    }

    /**
     * Parses one value (string, number, literal, or nested marker).
     *
     * @return the value, possibly null for JSON null
     */
    Object parseValue() {
      char ch = peek();
      if (ch == '"') {
        return parseString();
      }
      if (ch == '{' || ch == '[') {
        return skipNested();
      }
      if (ch == '-' || (ch >= '0' && ch <= '9')) {
        return parseNumber();
      }
      if (match("true")) {
        return Boolean.TRUE;
      }
      if (match("false")) {
        return Boolean.FALSE;
      }
      if (match("null")) {
        return null;
      }
      throw new IllegalArgumentException("value");
    }

    /**
     * Parses a JSON string with standard escapes.
     *
     * @return the decoded string, never null
     */
    String parseString() {
      expect('"');
      StringBuilder out = new StringBuilder();
      while (true) {
        if (pos >= text.length()) {
          throw new IllegalArgumentException("string");
        }
        char ch = text.charAt(pos++);
        if (ch == '"') {
          return out.toString();
        }
        if (ch == '\\') {
          if (pos >= text.length()) {
            throw new IllegalArgumentException("escape");
          }
          char esc = text.charAt(pos++);
          switch (esc) {
            case '"', '\\', '/' -> out.append(esc);
            case 'b' -> out.append('\b');
            case 'f' -> out.append('\f');
            case 'n' -> out.append('\n');
            case 'r' -> out.append('\r');
            case 't' -> out.append('\t');
            case 'u' -> {
              if (pos + 4 > text.length()) {
                throw new IllegalArgumentException("unicode");
              }
              out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
              pos += 4;
            }
            default -> throw new IllegalArgumentException("escape");
          }
          continue;
        }
        out.append(ch);
      }
    }

    /**
     * Parses a JSON number as long when integral, else double.
     *
     * @return the number, never null
     */
    Number parseNumber() {
      int start = pos;
      if (peek() == '-') {
        pos++;
      }
      while (pos < text.length() && isNumChar(text.charAt(pos))) {
        pos++;
      }
      String raw = text.substring(start, pos);
      if (raw.isEmpty() || raw.equals("-")) {
        throw new IllegalArgumentException("number");
      }
      try {
        if (raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0) {
          return Long.parseLong(raw);
        }
        return Double.parseDouble(raw);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("number");
      }
    }

    /**
     * Skips a nested object or array, returning a non-string marker.
     *
     * @return a fresh marker object, never null
     */
    Object skipNested() {
      char open = text.charAt(pos++);
      char close = open == '{' ? '}' : ']';
      int depth = 1;
      boolean inString = false;
      while (pos < text.length() && depth > 0) {
        char ch = text.charAt(pos++);
        if (inString) {
          if (ch == '\\') {
            pos++;
          } else if (ch == '"') {
            inString = false;
          }
          continue;
        }
        if (ch == '"') {
          inString = true;
        } else if (ch == open) {
          depth++;
        } else if (ch == close) {
          depth--;
        }
      }
      if (depth != 0) {
        throw new IllegalArgumentException("nested");
      }
      return new Object();
    }

    /** Skips ASCII whitespace. */
    void skipWs() {
      while (pos < text.length()) {
        char ch = text.charAt(pos);
        if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
          pos++;
        } else {
          break;
        }
      }
    }

    /**
     * Returns true at end of input after whitespace.
     *
     * @return true when nothing remains
     */
    boolean atEnd() {
      skipWs();
      return pos >= text.length();
    }

    /**
     * Peeks at the next character.
     *
     * @return the character, or 0 at end of input
     */
    char peek() {
      return pos < text.length() ? text.charAt(pos) : 0;
    }

    /**
     * Expects one exact character.
     *
     * @param want the required character
     */
    void expect(char want) {
      if (pos >= text.length() || text.charAt(pos) != want) {
        throw new IllegalArgumentException("want " + want);
      }
      pos++;
    }

    /**
     * Matches one literal keyword.
     *
     * @param word the keyword, never null
     * @return true when matched and consumed
     */
    boolean match(String word) {
      if (text.startsWith(word, pos)) {
        pos += word.length();
        return true;
      }
      return false;
    }

    /**
     * Checks whether a character belongs to a JSON number.
     *
     * @param ch the character
     * @return true for number characters
     */
    static boolean isNumChar(char ch) {
      return (ch >= '0' && ch <= '9') || ch == '.' || ch == 'e' || ch == 'E' || ch == '+'
          || ch == '-';
    }
  }
}
