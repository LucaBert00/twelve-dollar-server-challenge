package challenge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hand-written HTTP/1.1 parser, router and SPEC validation for the epoll worker.
 *
 * <p>Bytes arrive in the worker-shared scratch buffer (see {@link Server#SHARED_BUF_BYTES}); this
 * module never allocates per-connection buffers. {@link #tryParse} consumes exactly one request
 * (request line, headers, {@code Content-Length} body) and reports how many bytes it took, so the
 * caller loops over pipelined remainder without copying. Header names match case-insensitively;
 * {@code Authorization} keeps its raw value for the E04 verifier, {@code Content-Length} bounds
 * the body, and the keep-alive decision follows the version default unless {@code Connection}
 * overrides it.
 *
 * <p>Bodies over {@link #MAX_BODY_BYTES} are out of scope per SPEC and rejected here before any
 * allocation proportional to the declared length. Unknown paths answer {@code 404} with the exact
 * SPEC bytes; wrong methods on known paths fall into the same fallback, which SPEC leaves as
 * framework default.
 *
 * <p>Auth runs before id on both authed routes: {@link #requireAuth} rejects a missing bearer
 * first, then delegates the signature and payload checks to the {@link AuthCheck} hook that E04
 * provides, and only afterwards does the caller validate the id or the body. Create validation
 * trims with {@code strip()} before any check and counts the 500 limit in code points, not
 * UTF-16 units.
 */
final class Http {

  /** Out-of-scope guard: bodies above this are rejected before reading, per SPEC. */
  static final int MAX_BODY_BYTES = 16 * 1024;

  /** Out-of-scope guard: header blocks above this without a terminator close the connection. */
  static final int MAX_HEADER_BYTES = 16 * 1024;

  /** Exact SPEC error text for unknown paths. */
  static final String ERR_NOT_FOUND = "not found";

  /** Exact SPEC error text for a non-positive-integer id. */
  static final String ERR_INVALID_POST_ID = "invalid post id";

  /** Exact SPEC error text for a well-formed but absent id. */
  static final String ERR_POST_NOT_FOUND = "post not found";

  /** Exact SPEC error text for a missing or non-Bearer authorization header. */
  static final String ERR_MISSING_BEARER = "missing bearer token";

  /** Exact SPEC error text for a bad signature, expiry, alg or malformed JWT. */
  static final String ERR_INVALID_TOKEN = "invalid or expired token";

  /** Exact SPEC error text for a valid JWT whose sub or username has the wrong shape. */
  static final String ERR_INVALID_PAYLOAD = "invalid token payload";

  /** Exact SPEC error text for a request body that is not valid JSON. */
  static final String ERR_MALFORMED_JSON = "malformed JSON body";

  /** Exact SPEC error text for a missing, non-string or blank-after-trim body. */
  static final String ERR_BODY_REQUIRED = "body is required";

  /** Exact SPEC error text for a body over 500 code points after trim. */
  static final String ERR_BODY_TOO_LONG = "body must be at most 500 characters";

  /** Exact SPEC error text for unexpected failures. */
  static final String ERR_INTERNAL = "internal server error";

  /** Routes for the five SPEC endpoints plus the 404 fallback. */
  enum Route {
    /** GET /health. */
    HEALTH,
    /** GET /feed. */
    FEED,
    /** GET /posts/:id. */
    GET_POST,
    /** POST /posts. */
    CREATE_POST,
    /** POST /posts/:id/like. */
    LIKE,
    /** Anything else. */
    NOT_FOUND
  }

  /**
   * One parsed request with its keep-alive decision.
   *
   * @param method the request method as sent, never null
   * @param path the origin path with any query stripped, never null
   * @param version the protocol token as sent, never null
   * @param authorization the raw Authorization value, or null when absent
   * @param contentLength the declared body length in bytes, at least 0
   * @param keepAlive the Connection decision for the response echo
   * @param body the body decoded as UTF-8, never null
   */
  static record Request(
      String method,
      String path,
      String version,
      String authorization,
      int contentLength,
      boolean keepAlive,
      String body) {}

  /**
   * One parsed request plus the bytes it consumed for pipelining.
   *
   * @param request the parsed request, never null
   * @param consumed header plus body bytes taken from the buffer offset
   */
  static record Parsed(Request request, int consumed) {}

  /**
   * Status plus exact JSON body bytes for one routed request.
   *
   * @param status the HTTP status code to frame
   * @param body the exact JSON body bytes, never null
   */
  static record Reply(int status, byte[] body) {}

  /**
   * Authenticated user from the token hook.
   *
   * @param id the integer user id from {@code sub}
   * @param username the username used as-is for the author field
   */
  static record User(long id, String username) {}

  /** Thrown when the request line or headers are not valid HTTP/1.1. */
  static class MalformedException extends RuntimeException {
    /**
     * Creates a protocol failure.
     *
     * @param message the detail, never null
     */
    MalformedException(String message) {
      super(message);
    }
  }

  /** Thrown when the declared body exceeds the out-of-scope guard. */
  static final class TooLargeException extends MalformedException {
    /**
     * Creates an over-guard failure.
     *
     * @param message the detail, never null
     */
    TooLargeException(String message) {
      super(message);
    }
  }

  /** Thrown for every auth failure; the message is the exact SPEC 401 text. */
  static final class AuthFailure extends RuntimeException {
    /**
     * Creates an auth failure with the exact SPEC error message.
     *
     * @param message the exact SPEC message, never null
     */
    AuthFailure(String message) {
      super(message);
    }
  }

  /** Thrown for create-body validation; the message is the exact SPEC 400 text. */
  static final class BodyFailure extends RuntimeException {
    /**
     * Creates a body failure with the exact SPEC error message.
     *
     * @param message the exact SPEC message, never null
     */
    BodyFailure(String message) {
      super(message);
    }
  }

  /**
   * Verifier hook owned by E04.
   *
   * <p>E03 checks the missing-bearer shape inline and calls this for everything else, so the
   * ordering (auth before id) holds regardless of which verifier is plugged in.
   */
  interface AuthCheck {
    /**
     * Verifies one Bearer value already known to carry the prefix.
     *
     * @param authorization the raw header value with the Bearer prefix, never null
     * @return the authenticated user, never null
     * @throws AuthFailure with the exact SPEC 401 text on any failure
     */
    User check(String authorization);
  }

  private Http() {}

  /**
   * Parses exactly one request from shared-buffer bytes.
   *
   * <p>Pipelining-safe: the returned {@link Parsed#consumed} tells the caller where the next
   * request starts, which may be before the end of the readable bytes.
   *
   * @param buf the worker-shared scratch bytes, never null
   * @param off the offset to parse from, at least 0
   * @param len the readable bytes from the offset, at least 0
   * @return the request plus consumed bytes, or null when more bytes are needed
   * @throws MalformedException when the request line or headers are not valid HTTP
   * @throws TooLargeException when the declared body exceeds the out-of-scope guard
   */
  static Parsed tryParse(byte[] buf, int off, int len) {
    if (off < 0 || len < 0 || off + len > buf.length) {
      throw new IllegalArgumentException("offset/length out of range");
    }
    if (len == 0) {
      return null;
    }
    int end = off + len;
    int headerEnd = -1;
    int blankLen = 0;
    for (int i = off; i + 3 < end + 1 && i + 4 <= end; i++) {
      if (buf[i] == '\r' && buf[i + 1] == '\n' && buf[i + 2] == '\r' && buf[i + 3] == '\n') {
        headerEnd = i + 4;
        blankLen = 4;
        break;
      }
    }
    if (headerEnd < 0) {
      for (int i = off; i + 1 < end + 1 && i + 2 <= end; i++) {
        if (buf[i] == '\n' && buf[i + 1] == '\n') {
          headerEnd = i + 2;
          blankLen = 2;
          break;
        }
      }
    }
    if (headerEnd < 0) {
      if (len > MAX_HEADER_BYTES) {
        throw new MalformedException("header block too large");
      }
      return null;
    }
    int headLen = headerEnd - blankLen - off;
    String head = new String(buf, off, headLen, StandardCharsets.US_ASCII);
    String[] lines = head.split("\n", -1);
    if (lines.length == 0) {
      throw new MalformedException("empty request");
    }
    String requestLine = lines[0].endsWith("\r") ? lines[0].substring(0, lines[0].length() - 1) : lines[0];
    int first = requestLine.indexOf(' ');
    int last = requestLine.lastIndexOf(' ');
    if (first < 0 || last <= first) {
      throw new MalformedException("request line");
    }
    String method = requestLine.substring(0, first);
    String target = requestLine.substring(first + 1, last);
    String version = requestLine.substring(last + 1);
    if (method.isEmpty() || target.isEmpty() || version.isEmpty() || target.indexOf(' ') >= 0) {
      throw new MalformedException("request line parts");
    }
    int query = target.indexOf('?');
    String rawTarget = query < 0 ? target : target.substring(0, query);
    String path;
    int scheme = rawTarget.indexOf("://");
    if (scheme >= 0) {
      int slash = rawTarget.indexOf('/', scheme + 3);
      path = slash < 0 ? "/" : rawTarget.substring(slash);
    } else {
      path = rawTarget;
    }
    if (path.isEmpty()) {
      path = "/";
    }
    if (path.charAt(0) != '/') {
      throw new MalformedException("target");
    }
    String authorization = null;
    long contentLength = 0;
    boolean lengthSeen = false;
    List<String> connectionValues = new ArrayList<>(2);
    for (int i = 1; i < lines.length; i++) {
      String line = lines[i].endsWith("\r") ? lines[i].substring(0, lines[i].length() - 1) : lines[i];
      if (line.isEmpty()) {
        continue;
      }
      int colon = line.indexOf(':');
      if (colon < 0) {
        throw new MalformedException("header line");
      }
      String name = line.substring(0, colon).trim();
      String value = line.substring(colon + 1).trim();
      if (name.equalsIgnoreCase("authorization")) {
        if (authorization == null) {
          authorization = value;
        }
      } else if (name.equalsIgnoreCase("content-length")) {
        if (!lengthSeen) {
          lengthSeen = true;
          if (value.isEmpty() || !isDigits(value)) {
            throw new MalformedException("content-length");
          }
          long parsed;
          try {
            parsed = Long.parseLong(value);
          } catch (NumberFormatException e) {
            throw new MalformedException("content-length range");
          }
          if (parsed < 0) {
            throw new MalformedException("content-length sign");
          }
          if (parsed > MAX_BODY_BYTES) {
            throw new TooLargeException("body over guard");
          }
          contentLength = parsed;
        }
      } else if (name.equalsIgnoreCase("connection")) {
        connectionValues.add(value);
      }
    }
    boolean keepAlive = version.equalsIgnoreCase("HTTP/1.1");
    for (String value : connectionValues) {
      for (String token : value.split(",", -1)) {
        String t = token.trim();
        if (t.equalsIgnoreCase("close")) {
          keepAlive = false;
        } else if (t.equalsIgnoreCase("keep-alive")) {
          keepAlive = true;
        }
      }
    }
    long bodyStart = headerEnd - off;
    if (len < bodyStart + contentLength) {
      return null;
    }
    int bodyLen = (int) contentLength;
    String body = bodyLen == 0 ? "" : new String(buf, headerEnd, bodyLen, StandardCharsets.UTF_8);
    Request request =
        new Request(method, path, version, authorization, bodyLen, keepAlive, body);
    return new Parsed(request, (int) bodyStart + bodyLen);
  }

  /**
   * Classifies a method plus stripped path into its route.
   *
   * <p>Shape routing only: id validation lives in the handlers, which answer the {@code invalid
   * post id} matrix. Unknown shapes and wrong methods on known paths both fall through to {@code
   * NOT_FOUND}, which SPEC leaves as framework default for methods and fixes at {@code not found}
   * for paths.
   *
   * @param method the request method as sent, never null
   * @param path the stripped path, never null
   * @return the route, never null
   */
  static Route classify(String method, String path) {
    if ("GET".equals(method) && "/health".equals(path)) {
      return Route.HEALTH;
    }
    if ("GET".equals(method) && "/feed".equals(path)) {
      return Route.FEED;
    }
    if ("GET".equals(method) && isSinglePostPath(path)) {
      return Route.GET_POST;
    }
    if ("POST".equals(method) && "/posts".equals(path)) {
      return Route.CREATE_POST;
    }
    if ("POST".equals(method) && isLikePath(path)) {
      return Route.LIKE;
    }
    return Route.NOT_FOUND;
  }

  /**
   * Parses a raw post id segment into a positive integer.
   *
   * <p>Out-of-scope choice (SPEC tests only {@code 0,-1,abc,1.5} plus a large missing id):
   * digits-only and fitting in a {@code long} with value at least 1 wins; digit strings
   * overflowing {@code long} read as invalid.
   *
   * @param raw the raw id segment, never null
   * @return the id, or -1 when it is not a positive integer
   */
  static long parsePostId(String raw) {
    if (raw.isEmpty() || !isDigits(raw)) {
      return -1;
    }
    try {
      long id = Long.parseLong(raw);
      return id >= 1 ? id : -1;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /**
   * Validates a create body with trim-before-validate semantics.
   *
   * @param bodyJson the raw request body as UTF-8 text, never null
   * @return the trimmed body, never null
   * @throws BodyFailure with the exact SPEC 400 text on any rejection
   */
  static String validatedBody(String bodyJson) {
    Object value = bodyField(bodyJson);
    if (!(value instanceof String)) {
      throw new BodyFailure(ERR_BODY_REQUIRED);
    }
    String trimmed = ((String) value).strip();
    if (trimmed.isEmpty()) {
      throw new BodyFailure(ERR_BODY_REQUIRED);
    }
    if (trimmed.codePointCount(0, trimmed.length()) > 500) {
      throw new BodyFailure(ERR_BODY_TOO_LONG);
    }
    return trimmed;
  }

  /**
   * Enforces auth-before-id: missing bearer fails before any id or body check.
   *
   * @param authorization the raw header value, may be null
   * @param hook the E04 verifier for the remaining checks, never null
   * @return the authenticated user, never null
   * @throws AuthFailure with the exact SPEC 401 text on any failure
   */
  static User requireAuth(String authorization, AuthCheck hook) {
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new AuthFailure(ERR_MISSING_BEARER);
    }
    return hook.check(authorization);
  }

  /**
   * Routes one parsed request to its status plus exact JSON body bytes.
   *
   * <p>Reads run per request against the live handles with no held copy: feed order stays {@code
   * ORDER BY created_at DESC, id DESC} via the SPEC fallback in {@link Db}, and every like count
   * is read as of its own request. Writes open with {@code BEGIN IMMEDIATE}, run one op, then run
   * {@code COMMIT} and only afterwards answer, so each response waits for its commit; on commit
   * failure the single op answers {@code 500}. A busy write lock answers {@code 500} here and
   * leaves queuing across loop passes to the write owner.
   *
   * @param req the parsed request, never null
   * @param uptimeS whole seconds since the worker started, for the health shape
   * @param auth the E04 verifier hook for authed routes, never null
   * @return the status plus body bytes, never null
   */
  static Reply handle(Request req, long uptimeS, AuthCheck auth) {
    Route route = classify(req.method(), req.path());
    switch (route) {
      case HEALTH:
        try {
          Db.checkHealth();
        } catch (Exception e) {
          String message = e.getMessage();
          if (message == null || message.isEmpty()) {
            message = e.getClass().getSimpleName();
          }
          return new Reply(503, Json.degraded(message));
        }
        return new Reply(200, Json.health(uptimeS));
      case FEED:
        try {
          return new Reply(200, Json.feed(Db.feed()));
        } catch (Exception e) {
          return new Reply(500, Json.error(ERR_INTERNAL));
        }
      case GET_POST: {
        long id = parsePostId(req.path().substring("/posts/".length()));
        if (id < 1) {
          return new Reply(400, Json.error(ERR_INVALID_POST_ID));
        }
        try {
          Optional<Db.PostRow> found = Db.getPost(id);
          if (found.isEmpty()) {
            return new Reply(404, Json.error(ERR_POST_NOT_FOUND));
          }
          return new Reply(200, Json.singlePost(found.get()));
        } catch (Exception e) {
          return new Reply(500, Json.error(ERR_INTERNAL));
        }
      }
      case CREATE_POST: {
        User user;
        try {
          user = requireAuth(req.authorization(), auth);
        } catch (AuthFailure e) {
          return new Reply(401, Json.error(e.getMessage()));
        }
        String trimmed;
        try {
          trimmed = validatedBody(req.body());
        } catch (BodyFailure e) {
          return new Reply(400, Json.error(e.getMessage()));
        }
        try {
          if (!Db.tryBeginImmediate()) {
            return new Reply(500, Json.error(ERR_INTERNAL));
          }
          Db.CreatedPost created;
          try {
            created = Db.insertPostInTxn(user.id(), trimmed);
          } catch (RuntimeException e) {
            rollbackQuietly();
            return new Reply(500, Json.error(ERR_INTERNAL));
          }
          if (!Db.commitWrites()) {
            return new Reply(500, Json.error(ERR_INTERNAL));
          }
          Db.PostRow prow =
              new Db.PostRow(created.id(), trimmed, created.createdAt(), user.username(), 0);
          return new Reply(201, Json.singlePost(prow));
        } catch (Exception e) {
          rollbackQuietly();
          return new Reply(500, Json.error(ERR_INTERNAL));
        }
      }
      case LIKE: {
        User user;
        try {
          user = requireAuth(req.authorization(), auth);
        } catch (AuthFailure e) {
          return new Reply(401, Json.error(e.getMessage()));
        }
        String raw =
            req.path().substring("/posts/".length(), req.path().length() - "/like".length());
        long id = parsePostId(raw);
        if (id < 1) {
          return new Reply(400, Json.error(ERR_INVALID_POST_ID));
        }
        try {
          if (!Db.tryBeginImmediate()) {
            return new Reply(500, Json.error(ERR_INTERNAL));
          }
          Optional<Boolean> result;
          try {
            result = Db.insertLikeInTxn(user.id(), id);
          } catch (RuntimeException e) {
            rollbackQuietly();
            return new Reply(500, Json.error(ERR_INTERNAL));
          }
          if (!Db.commitWrites()) {
            return new Reply(500, Json.error(ERR_INTERNAL));
          }
          if (result.isEmpty()) {
            return new Reply(404, Json.error(ERR_POST_NOT_FOUND));
          }
          boolean already = result.get();
          return new Reply(already ? 200 : 201, Json.like(true, already, id));
        } catch (Exception e) {
          rollbackQuietly();
          return new Reply(500, Json.error(ERR_INTERNAL));
        }
      }
      default:
        return new Reply(404, Json.error(ERR_NOT_FOUND));
    }
  }

  /**
   * Checks whether a path is a single-post shape.
   *
   * @param path the stripped path, never null
   * @return true when the path has the single-post shape
   */
  static boolean isSinglePostPath(String path) {
    if (!path.startsWith("/posts/")) {
      return false;
    }
    String rest = path.substring("/posts/".length());
    return !rest.isEmpty() && rest.indexOf('/') < 0;
  }

  /**
   * Checks whether a path is a like shape.
   *
   * @param path the stripped path, never null
   * @return true when the path has the like shape
   */
  static boolean isLikePath(String path) {
    if (!path.startsWith("/posts/") || !path.endsWith("/like")) {
      return false;
    }
    String middle = path.substring("/posts/".length(), path.length() - "/like".length());
    return !middle.isEmpty() && middle.indexOf('/') < 0;
  }

  /**
   * Reports whether text is all ASCII digits.
   *
   * @param text the text, never null
   * @return true when non-empty and every char is 0-9
   */
  static boolean isDigits(String text) {
    if (text.isEmpty()) {
      return false;
    }
    for (int i = 0; i < text.length(); i++) {
      char ch = text.charAt(i);
      if (ch < '0' || ch > '9') {
        return false;
      }
    }
    return true;
  }

  private static void rollbackQuietly() {
    try {
      Db.rollbackWrites();
    } catch (RuntimeException ignored) {
      // Best effort: the 500 answer already carries the failure.
    }
  }

  /**
   * Extracts the top-level {@code body} field from a create payload.
   *
   * @param bodyJson the raw request body, never null
   * @return the string value, a marker for non-strings, or null when absent
   * @throws BodyFailure with the malformed text when the JSON is not valid
   */
  static Object bodyField(String bodyJson) {
    BodyJson parser = new BodyJson(bodyJson);
    try {
      return parser.parseCreateBody();
    } catch (IllegalArgumentException e) {
      throw new BodyFailure(ERR_MALFORMED_JSON);
    }
  }

  /** Tiny JSON reader for the create-body shape only. */
  private static final class BodyJson {
    private final String text;
    private int pos;

    /**
     * Creates a reader over the given text.
     *
     * @param text the raw JSON text, never null
     */
    BodyJson(String text) {
      this.text = text;
    }

    /**
     * Parses one top-level object and returns its body field.
     *
     * @return the body value, a marker, or null when absent
     */
    Object parseCreateBody() {
      skipWs();
      expect('{');
      Object body = null;
      boolean seenBody = false;
      skipWs();
      if (peek() == '}') {
        pos++;
        skipWs();
        if (!atEnd()) {
          throw new IllegalArgumentException("trailing");
        }
        return null;
      }
      while (true) {
        skipWs();
        String key = parseString();
        skipWs();
        expect(':');
        skipWs();
        Object value = parseValue();
        if (key.equals("body")) {
          body = value;
          seenBody = true;
        }
        skipWs();
        char ch = peek();
        if (ch == ',') {
          pos++;
          continue;
        }
        if (ch == '}') {
          pos++;
          skipWs();
          if (!atEnd()) {
            throw new IllegalArgumentException("trailing");
          }
          return seenBody ? body : null;
        }
        throw new IllegalArgumentException("object");
      }
    }

    /**
     * Parses one value (string, number, literal or nested marker).
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
            case '"':
            case '\\':
            case '/':
              out.append(esc);
              break;
            case 'b':
              out.append('\b');
              break;
            case 'f':
              out.append('\f');
              break;
            case 'n':
              out.append('\n');
              break;
            case 'r':
              out.append('\r');
              break;
            case 't':
              out.append('\t');
              break;
            case 'u':
              if (pos + 4 > text.length()) {
                throw new IllegalArgumentException("unicode");
              }
              int code;
              try {
                code = Integer.parseInt(text.substring(pos, pos + 4), 16);
              } catch (NumberFormatException e) {
                throw new IllegalArgumentException("unicode digits");
              }
              out.append((char) code);
              pos += 4;
              break;
            default:
              throw new IllegalArgumentException("escape kind");
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
        throw new IllegalArgumentException("number digits");
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
     * Reports whether the reader reached the end.
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
