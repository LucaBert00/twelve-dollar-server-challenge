package challenge;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * Durable SQLite owner for pool, pragmas, reference SQL and live health checks.
 *
 * <p>Single clear owner for all data-access posture in Phase 3: HikariCP pool
 * wiring, per-connection pragma block, startup assertions, SPEC reference SQL
 * constants and the live {@code SELECT 1} probe used by {@code GET /health}.
 * Feed, post and like handler logic lands in later tickets; this module holds
 * only the SQL shapes they will reuse.
 *
 * <p>Durability posture (SPEC Responses plus README rules 5-6): WAL with
 * {@code synchronous=NORMAL} floor, commit-before-respond, no caches. Pool of
 * 4 is the starting point, not an optimum; sizing belongs to later phases.
 * Session-only pragma reversion is guarded by applying the pragma block on
 * every connection acquisition and by failing fast at startup when any of the
 * 4 pooled connections does not hold the expected values.
 *
 * <p>Prepared-statement posture (OPT-06): prepare at first checkout, reset and
 * rebind per use. Statements are created per checkout via
 * {@code PreparedStatement} and closed per use, which reuses the query plan
 * without caching results. No response caches, no query-result caches and no
 * in-memory table copies exist in this module.
 */
public final class Db {

  /** Pool size is the explicit start value, not an optimum. */
  public static final int POOL_SIZE = 4;

  /** Busy timeout in millis applied on every pooled connection. */
  public static final int BUSY_TIMEOUT_MS = 5000;

  /**
   * Post-object base query in SPEC reference shape.
   *
   * <p>Verbatim reference SQL for the post object with correlated like count.
   */
  public static final String SELECT_POST_BASE =
      "SELECT p.id, p.body, p.created_at, u.username,"
          + " (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count"
          + " FROM posts p JOIN users u ON u.id = p.user_id";

  /**
   * Feed query in SPEC reference shape.
   *
   * <p>Base plus newest-first ordering on the covering index with limit 20.
   */
  public static final String FEED_SQL =
      SELECT_POST_BASE + " ORDER BY p.created_at DESC, p.id DESC LIMIT 20";

  /**
   * Single-post lookup in SPEC reference shape.
   *
   * <p>Base plus primary-key filter.
   */
  public static final String GET_POST_SQL = SELECT_POST_BASE + " WHERE p.id = ?";

  /**
   * Post creation in SPEC reference shape.
   *
   * <p>Insert with returning id and stored timestamp text.
   */
  public static final String CREATE_POST_SQL =
      "INSERT INTO posts (user_id, body) VALUES (?, ?) RETURNING id, created_at";

  /**
   * Like upsert in SPEC reference shape.
   *
   * <p>Idempotent insert that does nothing on repeat likes.
   */
  public static final String LIKE_UPSERT_SQL =
      "INSERT INTO likes (user_id, post_id) VALUES (?, ?)"
          + " ON CONFLICT (user_id, post_id) DO NOTHING";

  /** Liveness probe used by {@code GET /health}. */
  public static final String HEALTH_SQL = "SELECT 1";

  private static volatile HikariDataSource pool;

  private static volatile String dbPath;

  private Db() {}

  /**
   * Initializes the pool against the given database file with pragma guards.
   *
   * <p>Configures WAL, NORMAL, foreign-keys-on and busy timeout via the driver
   * plus an explicit per-connection pragma block, then asserts the four
   * pragmas on all 4 pooled connections and fails fast on any mismatch.
   *
   * @param sqlitePath filesystem path to an existing database file.
   * @throws IllegalArgumentException when the path does not name a file.
   * @throws IllegalStateException when any pragma assertion does not hold.
   */
  public static synchronized void init(String sqlitePath) {
    if (pool != null) {
      return;
    }
    if (sqlitePath == null || !Files.isRegularFile(Path.of(sqlitePath))) {
      throw new IllegalArgumentException("SQLITE_PATH must name an existing database file");
    }
    SQLiteConfig sqlite = new SQLiteConfig();
    sqlite.setJournalMode(SQLiteConfig.JournalMode.WAL);
    sqlite.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
    sqlite.enforceForeignKeys(true);
    sqlite.setBusyTimeout(BUSY_TIMEOUT_MS);
    SQLiteDataSource source = new SQLiteDataSource(sqlite);
    source.setUrl("jdbc:sqlite:" + Path.of(sqlitePath).toAbsolutePath());

    HikariConfig config = new HikariConfig();
    config.setDataSource(source);
    // Pool of 4 is the starting point, not an optimum (sizing owns later phases).
    config.setMaximumPoolSize(POOL_SIZE);
    config.setMinimumIdle(1);
    config.setAutoCommit(true);
    config.setConnectionTimeout(10000);
    HikariDataSource created = new HikariDataSource(config);

    List<Connection> held = new ArrayList<>(POOL_SIZE);
    try {
      for (int i = 0; i < POOL_SIZE; i++) {
        Connection c = created.getConnection();
        held.add(c);
        applyPragmas(c);
      }
      for (int i = 0; i < held.size(); i++) {
        assertPragmas(held.get(i), i + 1, held.size());
      }
      System.out.println(
          "HikariCP pool size "
              + POOL_SIZE
              + " (starting point, not optimum): pragma assertions 4/4 ok");
    } catch (SQLException | IllegalStateException e) {
      for (Connection c : held) {
        try {
          c.close();
        } catch (SQLException ignored) {
          // Closing after a failed start; the original failure is reported.
        }
      }
      created.close();
      throw new IllegalStateException("pragma assertions failed, failing fast", e);
    } finally {
      for (Connection c : held) {
        try {
          c.close();
        } catch (SQLException ignored) {
          // Return-to-pool best effort after logging; startup already recorded.
        }
      }
    }
    pool = created;
    dbPath = sqlitePath;
  }

  /**
   * Runs the liveness probe against one pooled connection.
   *
   * <p>Applies the per-connection pragma block on acquisition, then prepares
   * {@code SELECT 1} at checkout and closes it per use (reset per use, plan
   * reuse, never result caching).
   *
   * @throws SQLException when the probe cannot acquire a connection or the query fails.
   */
  public static void checkHealth() throws SQLException {
    HikariDataSource current = pool;
    if (current == null) {
      throw new SQLException("pool not initialized");
    }
    try (Connection c = current.getConnection()) {
      applyPragmas(c);
      try (PreparedStatement ps = c.prepareStatement(HEALTH_SQL);
          ResultSet rs = ps.executeQuery()) {
        if (!rs.next() || rs.getInt(1) != 1) {
          throw new SQLException("SELECT 1 returned no row");
        }
      }
    }
  }

  /**
   * Closes the pool and releases all pooled connections.
   */
  public static synchronized void close() {
    if (pool != null) {
      pool.close();
      pool = null;
    }
  }

  /**
   * Applies the durability pragma block on one acquired connection.
   *
   * <p>Runs on every connection acquisition because SQLite pragmas revert
   * per session and pooled reuse must never rely on assumed values.
   *
   * @param c the acquired connection.
   * @throws SQLException when any pragma statement fails.
   */
  private static void applyPragmas(Connection c) throws SQLException {
    try (Statement s = c.createStatement()) {
      s.execute("PRAGMA journal_mode=WAL");
      s.execute("PRAGMA synchronous=NORMAL");
      s.execute("PRAGMA foreign_keys=ON");
      s.execute("PRAGMA busy_timeout=5000");
    }
  }

  /**
   * Asserts the four pragmas on one pooled connection and logs the result.
   *
   * @param c the connection under test.
   * @param index 1-based connection number for the startup log.
   * @param total total connections asserted at startup.
   * @throws SQLException when a pragma query fails.
   * @throws IllegalStateException when any pragma value does not hold.
   */
  private static void assertPragmas(Connection c, int index, int total)
      throws SQLException {
    String journalMode;
    int synchronous;
    int foreignKeys;
    int busyTimeout;
    try (Statement s = c.createStatement()) {
      try (ResultSet rs = s.executeQuery("PRAGMA journal_mode")) {
        rs.next();
        journalMode = rs.getString(1);
      }
      try (ResultSet rs = s.executeQuery("PRAGMA synchronous")) {
        rs.next();
        synchronous = rs.getInt(1);
      }
      try (ResultSet rs = s.executeQuery("PRAGMA foreign_keys")) {
        rs.next();
        foreignKeys = rs.getInt(1);
      }
      try (ResultSet rs = s.executeQuery("PRAGMA busy_timeout")) {
        rs.next();
        busyTimeout = rs.getInt(1);
      }
    }
    boolean ok =
        journalMode != null
            && journalMode.equalsIgnoreCase("wal")
            && synchronous == 1
            && foreignKeys == 1
            && busyTimeout == BUSY_TIMEOUT_MS;
    System.out.println(
        "pragma assertion conn "
            + index
            + "/"
            + total
            + (ok ? " ok" : " FAILED")
            + ": journal_mode="
            + journalMode
            + " synchronous="
            + synchronous
            + " foreign_keys="
            + foreignKeys
            + " busy_timeout="
            + busyTimeout);
    if (!ok) {
      throw new IllegalStateException(
          "pragma mismatch on conn "
              + index
              + "/"
              + total
              + ": journal_mode="
              + journalMode
              + " synchronous="
              + synchronous
              + " foreign_keys="
              + foreignKeys
              + " busy_timeout="
              + busyTimeout);
    }
  }
}
