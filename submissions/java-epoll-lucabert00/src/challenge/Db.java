package challenge;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SQLite owner over FFM against the amalgamation that {@code build.sh} compiles.
 *
 * <p>One worker keeps two handles to the same file: a writer and a reader. A third handle is
 * opened on demand for the dedicated checkpoint thread (E05). All handles set the same pragma
 * block on open and fail fast when any value drifts.
 *
 * <p>Statements for request paths are prepared once per handle and reused with {@code reset}
 * plus rebind per use. That reuses the plan and never holds rows. They are prepared without
 * {@code SQLITE_PREPARE_PERSISTENT}: a persistent statement is written into the schema, so
 * preparing one takes the write lock, which the reader handle must never do.
 *
 * <p>Writes use group commit. The caller opens with {@code BEGIN IMMEDIATE}, runs the whole
 * batch of inserts inside the same txn, then runs {@code COMMIT}, and the answer for each op
 * goes out only after that commit returns. When the commit fails the caller answers
 * {@code 500} for every op in the batch.
 */
final class Db {
  /**
   * Post-object base query in SPEC reference shape.
   *
   * <p>Verbatim reference SQL for the post object with correlated like count.
   *
   * @param p the post row alias used in the reference text
   * @return the base text, never null
   */
  static final String SELECT_POST_BASE =
      "SELECT p.id, p.body, p.created_at, u.username,"
          + " (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count"
          + " FROM posts p JOIN users u ON u.id = p.user_id";

  /**
   * Feed query in SPEC reference shape.
   *
   * <p>Base plus newest-first ordering on the covering index with limit 20.
   */
  static final String FEED_SQL = SELECT_POST_BASE + " ORDER BY p.created_at DESC, p.id DESC LIMIT 20";

  /**
   * Single-post lookup in SPEC reference shape.
   *
   * <p>Base plus primary-key filter.
   */
  static final String GET_POST_SQL = SELECT_POST_BASE + " WHERE p.id = ?";

  /**
   * Post creation in SPEC reference shape.
   *
   * <p>Insert with returning id and stored timestamp text.
   */
  static final String CREATE_POST_SQL =
      "INSERT INTO posts (user_id, body) VALUES (?, ?) RETURNING id, created_at";

  /**
   * Like upsert in SPEC reference shape.
   *
   * <p>One statement: the insert is guarded by the post's existence, so a first like runs
   * no second query. {@code changes()} is 1 for a fresh like and 0 for both a conflict and
   * a missing post, so a conflict is told apart from a missing post by one existence probe.
   */
  static final String LIKE_UPSERT_SQL =
      "INSERT INTO likes (user_id, post_id)"
          + " SELECT ?, ? WHERE EXISTS (SELECT 1 FROM posts WHERE id = ?)"
          + " ON CONFLICT (user_id, post_id) DO NOTHING";

  /**
   * Liveness probe used by {@code GET /health}.
   */
  static final String HEALTH_SQL = "SELECT 1";

  /**
   * Post existence probe for the like path.
   */
  static final String POST_EXISTS_SQL = "SELECT count(*) FROM posts WHERE id = ?";

  /**
   * Writer busy wait in millis.
   *
   * <p>Zero so a held write lock never stalls the single loop thread. On {@code SQLITE_BUSY} the
   * caller keeps queued writes and retries on the next loop pass.
   */
  static final int WRITER_BUSY_TIMEOUT_MS = 0;

  /**
   * Reader busy wait in millis.
   *
   * <p>Fail-fast like the writer. Reads never take the write lock, so {@code BUSY} only arises
   * beside a checkpoint and a retry on the next pass is enough.
   */
  static final int READER_BUSY_TIMEOUT_MS = 0;

  /**
   * How long a queued write keeps retrying {@code BEGIN IMMEDIATE} before giving up.
   */
  static final long WRITE_GIVE_UP_NANOS = 5_000_000_000L;

  /**
   * Status answered for every op in a batch whose {@code COMMIT} fails.
   *
   * <p>The caller waits for {@code COMMIT} before answering, so every response waits for its
   * commit and a failed batch answers this status for each op it held.
   */
  static final int BATCH_FAILURE_STATUS = 500;

  /**
   * Page cache per writer and reader connection.
   *
   * <p>Negative means KiB rather than pages. This is a session pragma, so it is set and
   * asserted on every connection. 16 MiB per connection, because the score is connection
   * capacity and the RAM budget is the binding ceiling.
   */
  static final int CACHE_SIZE = -16384;

  /**
   * Page cache for the checkpoint connection.
   *
   * <p>Zero: the checkpoint connection never runs a query, so a page cache for it is pure
   * waste of the 2 GB budget.
   */
  static final int CHECKPOINT_CACHE_SIZE = 0;

  /**
   * WAL size cap in bytes, applied on every connection.
   *
   * <p>Bounds the WAL at 64 MiB alongside the scheduled passive loop.
   */
  static final long JOURNAL_SIZE_LIMIT = 67108864L;

  /**
   * Memory-mapped database size in bytes, applied on every connection.
   *
   * <p>The direction of this knob is disputed across the field; 1 GiB is the default
   * because it is not load-bearing at this duty cycle.
   */
  static final long MMAP_SIZE = 1073741824L;

  /**
   * Last frame count seen by the commit observer on the writer.
   *
   * <p>The observer is installed on the writer because the writer is the only connection
   * that commits. On the checkpoint connection it could never fire.
   */
  private static final AtomicInteger OBSERVED_FRAMES = new AtomicInteger();

  /**
   * Pure observer for {@code sqlite3_wal_hook}.
   *
   * <p>Records the frame count and returns 0, doing no I/O and never checkpointing inline.
   * The commit is never aborted.
   *
   * @param pArg unused hook argument, may be null
   * @param db the connection handle, may be null
   * @param zDb the database name pointer, may be null
   * @param nFrames the WAL frame count at commit time
   * @return 0 always
   */
  static int walHookCallback(MemorySegment pArg, MemorySegment db, MemorySegment zDb, int nFrames) {
    OBSERVED_FRAMES.set(nFrames);
    return 0;
  }

  /**
   * Reports the frame count seen at the most recent commit.
   *
   * @return the frame count from the most recent commit on the writer
   */
  static int observedFrames() {
    return OBSERVED_FRAMES.get();
  }

  /**
   * Installs the commit-frame observer on a connection.
   *
   * @param db the connection handle
   */
  private static void installWalHook(MemorySegment db) {
    try {
      MethodHandle target =
          MethodHandles.lookup()
              .findStatic(
                  Db.class,
                  "walHookCallback",
                  MethodType.methodType(
                      int.class,
                      MemorySegment.class,
                      MemorySegment.class,
                      MemorySegment.class,
                      int.class));
      FunctionDescriptor desc =
          FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT);
      MemorySegment stub = Linker.nativeLinker().upcallStub(target, desc, Arena.global());
      Sqlite.setWalHook(db, stub, MemorySegment.NULL);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * One post row as the read handlers consume it.
   *
   * @param id the post id
   * @param body the post body text as stored
   * @param createdAt the stored timestamp text, passed through untouched
   * @param author the post author's username
   * @param likeCount the live like count as of this request
   */
  static record PostRow(long id, String body, String createdAt, String author, long likeCount) {}

  /**
   * One created post inside a write txn.
   *
   * @param id the new post id from {@code RETURNING}
   * @param createdAt the stored timestamp text from {@code RETURNING}
   */
  static record CreatedPost(long id, String createdAt) {}

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup NATIVE_LIB =
      SymbolLookup.libraryLookup(
          Path.of(System.getProperty("sqlite.lib", "build/native/libsqlite3.so")), Arena.global());
  private static final MethodHandle CLOSE_HANDLE =
      LINKER.downcallHandle(
          NATIVE_LIB.find("sqlite3_close").orElseThrow(() -> new UnsatisfiedLinkError("sqlite3_close")),
          FunctionDescriptor.of(JAVA_INT, ADDRESS));

  private static MemorySegment writerDb;
  private static MemorySegment readerDb;

  private static MemorySegment readerHealthStmt;
  private static MemorySegment readerFeedStmt;
  private static MemorySegment readerGetPostStmt;
  private static MemorySegment readerPostExistsStmt;

  private static MemorySegment writerCreatePostStmt;
  private static MemorySegment writerLikeStmt;
  private static MemorySegment writerPostExistsStmt;

  private Db() {}

  /**
   * Opens the writer plus reader handles with the pragma block and persistent statements.
   *
   * @param sqlitePath filesystem path to an existing database file
   * @throws IllegalArgumentException when the path does not name a file
   * @throws IllegalStateException when any pragma assertion does not hold
   */
  static synchronized void init(String sqlitePath) {
    if (writerDb != null) {
      return;
    }
    if (sqlitePath == null || !Files.isRegularFile(Path.of(sqlitePath))) {
      throw new IllegalArgumentException("SQLITE_PATH must name an existing database file");
    }
    Sqlite.initialize();
    writerDb = openDb(sqlitePath, WRITER_BUSY_TIMEOUT_MS, CACHE_SIZE, "writer");
    readerDb = openDb(sqlitePath, READER_BUSY_TIMEOUT_MS, CACHE_SIZE, "reader");
    // The commit observer goes on the writer: the writer is the only connection that
    // commits, so an observer on the checkpoint connection could never fire.
    installWalHook(writerDb);
    readerHealthStmt = prepare(readerDb, HEALTH_SQL);
    readerFeedStmt = prepare(readerDb, FEED_SQL);
    readerGetPostStmt = prepare(readerDb, GET_POST_SQL);
    readerPostExistsStmt = prepare(readerDb, POST_EXISTS_SQL);
    writerCreatePostStmt = prepare(writerDb, CREATE_POST_SQL);
    writerLikeStmt = prepare(writerDb, LIKE_UPSERT_SQL);
    writerPostExistsStmt = prepare(writerDb, POST_EXISTS_SQL);
  }

  /**
   * Opens a checkpoint handle with the same pragma block for the dedicated thread.
   *
   * <p>E05 owns the checkpoint schedule. The commit observer stays on the writer, and the
   * thread drives {@code sqlite3_wal_checkpoint_v2}.
   *
   * @param sqlitePath filesystem path to the same database file
   * @return the checkpoint handle, never null
   */
  static MemorySegment openCheckpointConnection(String sqlitePath) {
    if (sqlitePath == null || !Files.isRegularFile(Path.of(sqlitePath))) {
      throw new IllegalArgumentException("SQLITE_PATH must name an existing database file");
    }
    return openDb(sqlitePath, 0, CHECKPOINT_CACHE_SIZE, "checkpoint");
  }

  /**
   * Tries to open a write txn without blocking.
   *
   * @return true when {@code BEGIN IMMEDIATE} holds the write lock, false on {@code SQLITE_BUSY}
   *     when the caller must keep queued writes and retry on the next loop pass
   */
  static boolean tryBeginImmediate() {
    int rc = execRaw(writerDb, "BEGIN IMMEDIATE");
    if (rc == Sqlite.OK) {
      return true;
    }
    if (rc == Sqlite.BUSY) {
      return false;
    }
    throw new IllegalStateException("BEGIN IMMEDIATE failed (" + rc + "): " + Sqlite.errmsg(writerDb));
  }

  /**
   * Commits this worker has completed.
   *
   * <p>A batch of N ops adds exactly one. It is the observable that proves a group
   * commit really shared a transaction.
   */
  private static final AtomicLong COMMITS = new AtomicLong();

  /**
   * Reports how many commits this worker has completed.
   *
   * @return the commit count
   */
  static long commitCount() {
    return COMMITS.get();
  }

  /**
   * Commits the current write txn.
   *
   * @return true on {@code COMMIT}, false on failure when the caller must answer {@code 500} for
   *     every op in the batch
   */
  static boolean commitWrites() {
    int rc = execRaw(writerDb, "COMMIT");
    if (rc == Sqlite.OK) {
      COMMITS.incrementAndGet();
      return true;
    }
    try {
      execRaw(writerDb, "ROLLBACK");
    } catch (RuntimeException ignored) {
      // Best effort after a failed COMMIT; the 500-for-batch answer carries the failure.
    }
    return false;
  }

  /**
   * Rolls back the current write txn.
   */
  static void rollbackWrites() {
    execOrThrow(writerDb, "ROLLBACK", "ROLLBACK");
  }

  /**
   * Reports whether a queued write has waited past the give-up bound.
   *
   * @param queueStartNanos the {@link System#nanoTime} instant the batch first saw
   *     {@code SQLITE_BUSY}
   * @return true past 5 s, when the caller stops retrying and fails the batch
   */
  static boolean writeGiveUp(long queueStartNanos) {
    return System.nanoTime() - queueStartNanos > WRITE_GIVE_UP_NANOS;
  }

  /**
   * Runs the liveness probe on the reader handle.
   *
   * @throws IllegalStateException when the probe does not return one row holding 1
   */
  static void checkHealth() {
    ensureInit();
    try (Arena arena = Arena.ofConfined()) {
      resetForUse(readerHealthStmt);
      int rc = Sqlite.step(readerHealthStmt);
      if (rc != Sqlite.ROW) {
        throw new IllegalStateException("SELECT 1 returned no row (" + rc + ")");
      }
      long v = Sqlite.columnInt64(readerHealthStmt, 0);
      rc = Sqlite.step(readerHealthStmt);
      resetAfterUse(readerHealthStmt);
      if (v != 1 || rc != Sqlite.DONE) {
        throw new IllegalStateException("SELECT 1 probe mismatch");
      }
    }
  }

  /**
   * Reads the 20 newest posts for {@code GET /feed}.
   *
   * @return the 20 newest rows in wire order, never null
   */
  static List<PostRow> feed() {
    ensureInit();
    List<PostRow> out = new ArrayList<>(20);
    resetForUse(readerFeedStmt);
    for (; ; ) {
      int rc = Sqlite.step(readerFeedStmt);
      if (rc == Sqlite.DONE) {
        break;
      }
      if (rc != Sqlite.ROW) {
        resetAfterUse(readerFeedStmt);
        throw new IllegalStateException("feed step failed (" + rc + ")");
      }
      out.add(mapPost(readerFeedStmt));
    }
    resetAfterUse(readerFeedStmt);
    return out;
  }

  /**
   * Reads one post by id for {@code GET /posts/:id}.
   *
   * @param id the post id, already validated as a positive integer
   * @return the row, or empty when the post does not exist
   */
  static Optional<PostRow> getPost(long id) {
    ensureInit();
    try (Arena arena = Arena.ofConfined()) {
      resetForUse(readerGetPostStmt);
      checkBind(Sqlite.bindInt64(readerGetPostStmt, 1, id), "getPost bind");
      int rc = Sqlite.step(readerGetPostStmt);
      if (rc == Sqlite.DONE) {
        resetAfterUse(readerGetPostStmt);
        return Optional.empty();
      }
      if (rc != Sqlite.ROW) {
        resetAfterUse(readerGetPostStmt);
        throw new IllegalStateException("getPost step failed (" + rc + ")");
      }
      PostRow row = mapPost(readerGetPostStmt);
      rc = Sqlite.step(readerGetPostStmt);
      resetAfterUse(readerGetPostStmt);
      if (rc != Sqlite.DONE) {
        throw new IllegalStateException("getPost trailing step (" + rc + ")");
      }
      return Optional.of(row);
    }
  }

  /**
   * Inserts one post inside the caller's {@code BEGIN IMMEDIATE} .. {@code COMMIT}.
   *
   * <p>Does not commit. The caller must wait for {@code COMMIT} before answering, so every
   * response waits for its commit.
   *
   * @param userId the author id from the token
   * @param body the trimmed body, already validated to 1-500 code points
   * @return the new id plus stored timestamp, never null
   */
  static CreatedPost insertPostInTxn(long userId, String body) {
    ensureInit();
    try (Arena arena = Arena.ofConfined()) {
      resetForUse(writerCreatePostStmt);
      checkBind(Sqlite.bindInt64(writerCreatePostStmt, 1, userId), "createPost bind user");
      bindText(writerCreatePostStmt, 2, body, arena, "createPost bind body");
      int rc = Sqlite.step(writerCreatePostStmt);
      if (rc != Sqlite.ROW) {
        resetAfterUse(writerCreatePostStmt);
        throw new IllegalStateException(
            "createPost step failed (" + rc + "): " + Sqlite.errmsg(writerDb));
      }
      long id = Sqlite.columnInt64(writerCreatePostStmt, 0);
      String createdAt = columnString(writerCreatePostStmt, 1);
      rc = Sqlite.step(writerCreatePostStmt);
      resetAfterUse(writerCreatePostStmt);
      if (rc != Sqlite.DONE) {
        throw new IllegalStateException("createPost trailing step (" + rc + ")");
      }
      return new CreatedPost(id, createdAt);
    }
  }

  /**
   * Likes one post inside the caller's {@code BEGIN IMMEDIATE} .. {@code COMMIT}.
   *
   * <p>Does not commit. The caller must wait for {@code COMMIT} before answering, so every
   * response waits for its commit.
   *
   * @param userId the liking user id from the token
   * @param postId the post id, already validated as a positive integer
   * @return empty when the post does not exist, otherwise true on repeats and false on first like
   */
  static Optional<Boolean> insertLikeInTxn(long userId, long postId) {
    ensureInit();
    try (Arena arena = Arena.ofConfined()) {
      resetForUse(writerLikeStmt);
      checkBind(Sqlite.bindInt64(writerLikeStmt, 1, userId), "like bind user");
      checkBind(Sqlite.bindInt64(writerLikeStmt, 2, postId), "like bind post");
      checkBind(Sqlite.bindInt64(writerLikeStmt, 3, postId), "like bind exists post");
      int rc = Sqlite.step(writerLikeStmt);
      resetAfterUse(writerLikeStmt);
      if (rc != Sqlite.DONE) {
        throw new IllegalStateException(
            "like step failed (" + rc + "): " + Sqlite.errmsg(writerDb));
      }
      if (Sqlite.changes(writerDb) == 1) {
        // A fresh like. This is the common path, and it ran exactly one statement.
        return Optional.of(Boolean.FALSE);
      }
      // No row was inserted: either the like already existed, or the post does not exist.
      // One existence probe tells them apart.
      resetForUse(writerPostExistsStmt);
      checkBind(Sqlite.bindInt64(writerPostExistsStmt, 1, postId), "like exists bind");
      rc = Sqlite.step(writerPostExistsStmt);
      long n = 0;
      if (rc == Sqlite.ROW) {
        n = Sqlite.columnInt64(writerPostExistsStmt, 0);
        rc = Sqlite.step(writerPostExistsStmt);
      }
      resetAfterUse(writerPostExistsStmt);
      if (rc != Sqlite.DONE) {
        throw new IllegalStateException("like exists trailing step (" + rc + ")");
      }
      if (n == 0) {
        return Optional.empty();
      }
      return Optional.of(Boolean.TRUE);
    }
  }

  /**
   * Closes persistent statements plus the writer and reader handles.
   */
  static synchronized void close() {
    finalizeQuietly(readerHealthStmt);
    finalizeQuietly(readerFeedStmt);
    finalizeQuietly(readerGetPostStmt);
    finalizeQuietly(readerPostExistsStmt);
    finalizeQuietly(writerCreatePostStmt);
    finalizeQuietly(writerLikeStmt);
    finalizeQuietly(writerPostExistsStmt);
    readerHealthStmt = null;
    readerFeedStmt = null;
    readerGetPostStmt = null;
    readerPostExistsStmt = null;
    writerCreatePostStmt = null;
    writerLikeStmt = null;
    writerPostExistsStmt = null;
    closeQuietly(readerDb);
    closeQuietly(writerDb);
    readerDb = null;
    writerDb = null;
  }

  private static MemorySegment openDb(
      String sqlitePath, int busyTimeoutMs, int cacheSize, String role) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment pathSeg = toNul(sqlitePath, arena);
      MemorySegment ppDb = Arena.global().allocate(ADDRESS);
      int flags = Sqlite.OPEN_READWRITE | Sqlite.OPEN_NOMUTEX;
      int rc = Sqlite.openV2(pathSeg, ppDb, flags);
      if (rc != Sqlite.OK) {
        throw new IllegalStateException(role + " openV2 failed (" + rc + ")");
      }
      MemorySegment db = ppDb.get(ADDRESS, 0);
      if (db == null || db.equals(MemorySegment.NULL)) {
        throw new IllegalStateException(role + " open returned null handle");
      }
      applyPragmas(db, busyTimeoutMs, cacheSize, role);
      assertPragmas(db, busyTimeoutMs, cacheSize, role);
      return db;
    }
  }

  private static void applyPragmas(
      MemorySegment db, int busyTimeoutMs, int cacheSize, String role) {
    execOrThrow(db, "PRAGMA journal_mode=WAL", role);
    execOrThrow(db, "PRAGMA synchronous=NORMAL", role);
    execOrThrow(db, "PRAGMA foreign_keys=ON", role);
    execOrThrow(db, "PRAGMA busy_timeout=" + busyTimeoutMs, role);
    execOrThrow(db, "PRAGMA wal_autocheckpoint=0", role);
    execOrThrow(db, "PRAGMA journal_size_limit=" + JOURNAL_SIZE_LIMIT, role);
    execOrThrow(db, "PRAGMA mmap_size=" + MMAP_SIZE, role);
    execOrThrow(db, "PRAGMA cache_size=" + cacheSize, role);
    execOrThrow(db, "PRAGMA temp_store=MEMORY", role);
  }

  private static void assertPragmas(
      MemorySegment db, int busyTimeoutMs, int cacheSize, String role) {
    String journalMode = pragmaString(db, "PRAGMA journal_mode");
    long synchronous = pragmaLong(db, "PRAGMA synchronous");
    long foreignKeys = pragmaLong(db, "PRAGMA foreign_keys");
    long busyTimeout = pragmaLong(db, "PRAGMA busy_timeout");
    long autocheckpoint = pragmaLong(db, "PRAGMA wal_autocheckpoint");
    long journalSizeLimit = pragmaLong(db, "PRAGMA journal_size_limit");
    long mmapSize = pragmaLong(db, "PRAGMA mmap_size");
    long actualCacheSize = pragmaLong(db, "PRAGMA cache_size");
    long tempStore = pragmaLong(db, "PRAGMA temp_store");
    boolean ok =
        journalMode != null
            && journalMode.equalsIgnoreCase("wal")
            && synchronous == 1
            && foreignKeys == 1
            && busyTimeout == busyTimeoutMs
            && autocheckpoint == 0
            && journalSizeLimit == JOURNAL_SIZE_LIMIT
            && mmapSize == MMAP_SIZE
            && actualCacheSize == cacheSize
            && tempStore == 2L;
    if (!ok) {
      throw new IllegalStateException(
          "pragma mismatch on "
              + role
              + ": journal_mode="
              + journalMode
              + " synchronous="
              + synchronous
              + " foreign_keys="
              + foreignKeys
              + " busy_timeout="
              + busyTimeout
              + " wal_autocheckpoint="
              + autocheckpoint
              + " journal_size_limit="
              + journalSizeLimit
              + " mmap_size="
              + mmapSize
              + " cache_size="
              + actualCacheSize
              + " temp_store="
              + tempStore);
    }
  }

  private static String pragmaString(MemorySegment db, String pragma) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment sqlSeg = toNul(pragma, arena);
      MemorySegment ppStmt = arena.allocate(ADDRESS);
      int rc = Sqlite.prepareV3(db, sqlSeg, 0, ppStmt);
      if (rc != Sqlite.OK) {
        throw new IllegalStateException(pragma + " prepare failed (" + rc + ")");
      }
      MemorySegment stmt = ppStmt.get(ADDRESS, 0);
      try {
        rc = Sqlite.step(stmt);
        if (rc != Sqlite.ROW) {
          throw new IllegalStateException(pragma + " step failed (" + rc + ")");
        }
        return columnString(stmt, 0);
      } finally {
        Sqlite.finalizeStmt(stmt);
      }
    }
  }

  private static long pragmaLong(MemorySegment db, String pragma) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment sqlSeg = toNul(pragma, arena);
      MemorySegment ppStmt = arena.allocate(ADDRESS);
      int rc = Sqlite.prepareV3(db, sqlSeg, 0, ppStmt);
      if (rc != Sqlite.OK) {
        throw new IllegalStateException(pragma + " prepare failed (" + rc + ")");
      }
      MemorySegment stmt = ppStmt.get(ADDRESS, 0);
      try {
        rc = Sqlite.step(stmt);
        if (rc != Sqlite.ROW) {
          throw new IllegalStateException(pragma + " step failed (" + rc + ")");
        }
        return Sqlite.columnInt64(stmt, 0);
      } finally {
        Sqlite.finalizeStmt(stmt);
      }
    }
  }

  /**
   * Prepares one statement on a handle and keeps it for reuse.
   *
   * <p>Prepared without {@code SQLITE_PREPARE_PERSISTENT}. A persistent statement is written
   * into the schema, so preparing one takes the write lock: the reader handle must never take
   * the write lock, and with {@code busy_timeout=0} a sibling worker's startup write would
   * fail this prepare outright. The plan is still cached on the handle that prepared it, which
   * is the only reuse this workload does.
   *
   * @param db the connection handle
   * @param sql the statement text
   * @return the prepared statement handle, never null
   */
  private static MemorySegment prepare(MemorySegment db, String sql) {
    MemorySegment ppStmt = Arena.global().allocate(ADDRESS);
    int rc;
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment sqlSeg = toNul(sql, arena);
      rc = Sqlite.prepareV3(db, sqlSeg, 0, ppStmt);
    }
    if (rc != Sqlite.OK) {
      throw new IllegalStateException("prepare failed (" + rc + "): " + Sqlite.errmsg(db));
    }
    MemorySegment stmt = ppStmt.get(ADDRESS, 0);
    if (stmt == null || stmt.equals(MemorySegment.NULL)) {
      throw new IllegalStateException("prepare returned null handle");
    }
    return stmt;
  }

  private static PostRow mapPost(MemorySegment stmt) {
    long id = Sqlite.columnInt64(stmt, 0);
    String body = columnString(stmt, 1);
    String createdAt = columnString(stmt, 2);
    String author = columnString(stmt, 3);
    long likeCount = Sqlite.columnInt64(stmt, 4);
    return new PostRow(id, body, createdAt, author, likeCount);
  }

  private static String columnString(MemorySegment stmt, int col) {
    MemorySegment ptr = Sqlite.columnText(stmt, col);
    int n = Sqlite.columnBytes(stmt, col);
    if (n <= 0) {
      return "";
    }
    byte[] raw = ptr.reinterpret(n).toArray(JAVA_BYTE);
    return new String(raw, StandardCharsets.UTF_8);
  }

  private static void bindText(MemorySegment stmt, int index, String value, Arena arena, String role) {
    byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
    MemorySegment seg;
    if (utf8.length == 0) {
      seg = arena.allocate(1, 1);
    } else {
      seg = arena.allocate(utf8.length, 1);
      for (int i = 0; i < utf8.length; i++) {
        seg.set(JAVA_BYTE, i, utf8[i]);
      }
    }
    checkBind(Sqlite.bindText(stmt, index, seg, utf8.length), role);
  }

  private static void resetForUse(MemorySegment stmt) {
    Sqlite.reset(stmt);
    Sqlite.clearBindings(stmt);
  }

  private static void resetAfterUse(MemorySegment stmt) {
    Sqlite.reset(stmt);
    Sqlite.clearBindings(stmt);
  }

  private static void checkBind(int rc, String role) {
    if (rc != Sqlite.OK) {
      throw new IllegalStateException(role + " bind failed (" + rc + ")");
    }
  }

  private static int execRaw(MemorySegment db, String sql) {
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment sqlSeg = toNul(sql, arena);
      return Sqlite.exec(db, sqlSeg);
    }
  }

  private static void execOrThrow(MemorySegment db, String sql, String role) {
    int rc = execRaw(db, sql);
    if (rc != Sqlite.OK) {
      throw new IllegalStateException(role + " exec failed (" + rc + "): " + Sqlite.errmsg(db));
    }
  }

  private static MemorySegment toNul(String s, Arena arena) {
    byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
    MemorySegment seg = arena.allocate(utf8.length + 1L, 1);
    for (int i = 0; i < utf8.length; i++) {
      seg.set(JAVA_BYTE, i, utf8[i]);
    }
    seg.set(JAVA_BYTE, utf8.length, (byte) 0);
    return seg;
  }

  private static void ensureInit() {
    if (writerDb == null || readerDb == null) {
      throw new IllegalStateException("Db.init has not run");
    }
  }

  private static void finalizeQuietly(MemorySegment stmt) {
    if (stmt == null || stmt.equals(MemorySegment.NULL)) {
      return;
    }
    try {
      Sqlite.finalizeStmt(stmt);
    } catch (RuntimeException ignored) {
      // Best effort on shutdown; startup already proved the handles.
    }
  }

  private static void closeQuietly(MemorySegment db) {
    if (db == null || db.equals(MemorySegment.NULL)) {
      return;
    }
    try {
      closeDb(db);
    } catch (RuntimeException ignored) {
      // Best effort on shutdown.
    }
  }

  private static int closeDb(MemorySegment db) {
    try {
      return (int) CLOSE_HANDLE.invokeExact(db);
    } catch (Throwable t) {
      if (t instanceof RuntimeException r) {
        throw r;
      }
      if (t instanceof Error e) {
        throw e;
      }
      throw new IllegalStateException(t);
    }
  }
}
