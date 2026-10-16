package challenge;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal HS256 signing helper for gate tests.
 *
 * <p>Independent of {@link Jwt}: uses stdlib {@code Mac} directly so tests do not
 * verify the verifier with itself. Mirrors {@code test/test.sh} {@code sign()}.
 */
final class TestTokens {

  private TestTokens() {}

  /**
   * Signs a payload with the given secret.
   *
   * @param secret the HMAC secret, never null.
   * @param headerJson the header JSON, never null.
   * @param payloadJson the payload JSON, never null.
   * @return the compact JWT, never null.
   */
  static String sign(String secret, String headerJson, String payloadJson) {
    try {
      Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
      String unsigned =
          encoder.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8))
              + "."
              + encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return unsigned
          + "."
          + encoder.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.US_ASCII)));
    } catch (Exception e) {
      throw new IllegalStateException("sign failed", e);
    }
  }

  /**
   * Builds a valid token for the given user.
   *
   * @param secret the HMAC secret, never null.
   * @param userId the subject id.
   * @param username the username, never null.
   * @return the compact JWT, never null.
   */
  static String valid(String secret, long userId, String username) {
    long exp = Instant.now().getEpochSecond() + 3600;
    return sign(
        secret,
        "{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
        "{\"sub\":\"" + userId + "\",\"username\":\"" + username + "\",\"iat\":" + (exp - 3600)
            + ",\"exp\":" + exp + "}");
  }
}
