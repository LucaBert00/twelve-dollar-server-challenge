package challenge;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Test-only SQL setup over the submission's own FFM SQLite.
 *
 * <p>The gate builds controlled databases (ties, gaps, boundaries) with the same native
 * library the server uses. The server never creates or mutates a database, so this helper
 * stays out of production sources.
 */
final class TestSqlite {
  private static final int OPEN_CREATE = 0x4;

  private TestSqlite() {}

  /**
   * Opens {@code db}, runs the statements in order, and closes the handle.
   *
   * @param db the database file, created when missing
   * @param statements SQL statements to run; each may itself hold several statements
   */
  static void exec(Path db, String... statements) {
    Sqlite.initialize();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment ppDb = arena.allocate(ADDRESS);
      int rc =
          Sqlite.openV2(
              nul(db.toString(), arena),
              ppDb,
              Sqlite.OPEN_READWRITE | OPEN_CREATE | Sqlite.OPEN_NOMUTEX);
      if (rc != Sqlite.OK) {
        throw new IllegalStateException("open " + db + " failed (" + rc + ")");
      }
      MemorySegment handle = ppDb.get(ADDRESS, 0);
      try {
        execOrThrow(handle, "PRAGMA foreign_keys=ON", arena);
        for (String sql : statements) {
          execOrThrow(handle, sql, arena);
        }
      } finally {
        Sqlite.close(handle);
      }
    }
  }

  private static void execOrThrow(MemorySegment db, String sql, Arena arena) {
    int rc = Sqlite.exec(db, nul(sql, arena));
    if (rc != Sqlite.OK) {
      throw new IllegalStateException(
          "exec failed (" + rc + "): " + Sqlite.errmsg(db) + " in: " + sql);
    }
  }

  private static MemorySegment nul(String s, Arena arena) {
    byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
    MemorySegment seg = arena.allocate(utf8.length + 1L, 1);
    for (int i = 0; i < utf8.length; i++) {
      seg.set(JAVA_BYTE, i, utf8[i]);
    }
    seg.set(JAVA_BYTE, utf8.length, (byte) 0);
    return seg;
  }
}
