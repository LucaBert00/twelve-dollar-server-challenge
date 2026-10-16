package challenge;

import java.nio.charset.StandardCharsets;

/**
 * HTTP/1.1 response framer for compact-JSON bodies.
 *
 * <p>Single owner for status lines and transport headers. Bodies come from {@link Json} with
 * SPEC-exact bytes; this module only prefixes them with the status line, {@code Content-Type},
 * {@code Content-Length} and the {@code Connection} echo of the request's keep-alive decision.
 * No body bytes are ever altered, templated or substituted here.
 */
final class Out {

  private Out() {}

  /**
   * Frames one complete HTTP/1.1 response over a JSON body.
   *
   * @param status the HTTP status code, one of 200, 201, 400, 401, 404, 500, 503
   * @param body the exact JSON body bytes, never null
   * @param keepAlive true to echo {@code Connection: keep-alive}, false for {@code close}
   * @return the full response bytes, headers plus body
   */
  static byte[] response(int status, byte[] body, boolean keepAlive) {
    String head =
        "HTTP/1.1 "
            + status
            + " "
            + reason(status)
            + "\r\nContent-Type: application/json\r\nContent-Length: "
            + body.length
            + "\r\nConnection: "
            + (keepAlive ? "keep-alive" : "close")
            + "\r\n\r\n";
    byte[] prefix = head.getBytes(StandardCharsets.US_ASCII);
    byte[] out = new byte[prefix.length + body.length];
    System.arraycopy(prefix, 0, out, 0, prefix.length);
    System.arraycopy(body, 0, out, prefix.length, body.length);
    return out;
  }

  /**
   * Maps a served status code to its reason phrase.
   *
   * @param status the HTTP status code
   * @return the reason phrase, never null
   */
  static String reason(int status) {
    switch (status) {
      case 200:
        return "OK";
      case 201:
        return "Created";
      case 400:
        return "Bad Request";
      case 401:
        return "Unauthorized";
      case 404:
        return "Not Found";
      case 500:
        return "Internal Server Error";
      case 503:
        return "Service Unavailable";
      default:
        return "Internal Server Error";
    }
  }
}
