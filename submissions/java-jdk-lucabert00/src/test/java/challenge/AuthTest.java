package challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Gate: HS256 verifier matrices (auth-first shapes, id helper).
 *
 * <p>Arrange-Act-Assert throughout. Mirrors {@code test/test.sh} auth rows
 * plus the id-validation partition.
 */
@Tag("gate")
@DisplayName("Auth verifier")
class AuthTest {

  private static final String SECRET = "test-secret";

  @BeforeAll
  static void init() {
    // Arrange (shared): verifier under test.
    Auth.init(SECRET);
  }

  @ParameterizedTest(name = "missing bearer for [{0}]")
  @NullAndEmptySource
  @ValueSource(strings = {"Basic abc", "Bearer", "Token xyz"})
  @DisplayName("absent or non-Bearer header")
  void missingBearer(String header) {
    // Act
    Auth.AuthException failure =
        assertThrows(Auth.AuthException.class, () -> Auth.authenticate(header));

    // Assert
    assertEquals("missing bearer token", failure.getMessage());
  }

  static Stream<Arguments> invalidTokens() {
    // Arrange: tokens that must fail signature/expiry/shape checks.
    long future = Instant.now().getEpochSecond() + 3600;
    long past = Instant.now().getEpochSecond() - 3600;
    String goodPayload =
        "{\"sub\":\"1\",\"username\":\"one\",\"iat\":" + (future - 3600) + ",\"exp\":" + future + "}";
    String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    return Stream.of(
        Arguments.of("abc"),
        Arguments.of("a.b"),
        Arguments.of("a.b.c.d"),
        Arguments.of(".."),
        Arguments.of(TestTokens.sign(SECRET, header, goodPayload) + "x"),
        Arguments.of(
            TestTokens.sign(
                SECRET,
                "{\"alg\":\"none\",\"typ\":\"JWT\"}",
                "{\"sub\":\"1\",\"username\":\"one\",\"exp\":" + future + "}")),
        Arguments.of(
            TestTokens.sign(SECRET, "{\"alg\":\"HS384\"}", goodPayload)),
        Arguments.of(
            TestTokens.sign(
                SECRET,
                header,
                "{\"sub\":\"1\",\"username\":\"one\",\"iat\":"
                    + (past - 3600) + ",\"exp\":" + past + "}")),
        Arguments.of(TestTokens.sign(SECRET, header, "{\"sub\":\"1\",\"username\":\"one\"}")),
        Arguments.of(TestTokens.sign(SECRET, header, "{\"sub\":\"1\",\"username\":\"one\",\"exp\":\"x\"}")));
  }

  @ParameterizedTest(name = "invalid or expired [{index}]")
  @MethodSource("invalidTokens")
  @DisplayName("bad signature, alg, expiry or shape")
  void invalidOrExpired(String token) {
    // Arrange
    String header = "Bearer " + token;

    // Act
    Auth.AuthException failure =
        assertThrows(Auth.AuthException.class, () -> Auth.authenticate(header));

    // Assert
    assertEquals("invalid or expired token", failure.getMessage());
  }

  static Stream<Arguments> badPayloads() {
    // Arrange: valid signature but bad sub/username claims.
    long future = Instant.now().getEpochSecond() + 3600;
    String header = "{\"alg\":\"HS256\"}";
    return Stream.of(
        Arguments.of("{\"sub\":1,\"username\":\"one\",\"exp\":" + future + "}"),
        Arguments.of("{\"sub\":\"0\",\"username\":\"one\",\"exp\":" + future + "}"),
        Arguments.of("{\"sub\":\"-1\",\"username\":\"one\",\"exp\":" + future + "}"),
        Arguments.of("{\"sub\":\" 1\",\"username\":\"one\",\"exp\":" + future + "}"),
        Arguments.of("{\"sub\":\"abc\",\"username\":\"one\",\"exp\":" + future + "}"),
        Arguments.of("{\"sub\":\"1\",\"username\":1,\"exp\":" + future + "}"),
        Arguments.of("{\"sub\":\"1\",\"exp\":" + future + "}"),
        Arguments.of("{\"username\":\"one\",\"exp\":" + future + "}"));
  }

  @ParameterizedTest(name = "invalid payload [{index}]")
  @MethodSource("badPayloads")
  @DisplayName("sub must be a positive-integer string and username a string")
  void invalidPayload(String payloadJson) {
    // Arrange
    String token = TestTokens.sign(SECRET, "{\"alg\":\"HS256\"}", payloadJson);

    // Act
    Auth.AuthException failure =
        assertThrows(Auth.AuthException.class, () -> Auth.authenticate("Bearer " + token));

    // Assert
    assertEquals("invalid token payload", failure.getMessage());
  }

  @Test
  @DisplayName("valid token returns the user")
  void validToken() {
    // Arrange
    String token = TestTokens.valid(SECRET, 1, "one");

    // Act
    Auth.User user = Auth.authenticate("Bearer " + token);

    // Assert
    assertEquals(1, user.id());
    assertEquals("one", user.username());
  }

  @ParameterizedTest(name = "positive integer [{0}]")
  @ValueSource(strings = {"1", "01", "250000", "9223372036854775807"})
  @DisplayName("positiveInteger accepts digits >= 1")
  void positiveIntegerAccepts(String text) {
    // Act
    long value = Auth.positiveInteger(text);

    // Assert
    assertEquals(Long.parseLong(text), value);
  }

  @ParameterizedTest(name = "not positive integer [{0}]")
  @ValueSource(strings = {"0", "-1", "abc", "1.5", "", "9223372036854775808"})
  @DisplayName("positiveInteger rejects the id matrix")
  void positiveIntegerRejects(String text) {
    // Act + Assert
    assertThrows(IllegalArgumentException.class, () -> Auth.positiveInteger(text));
  }
}
