package challenge;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/**
 * WAL checkpointer that keeps {@code COMMIT} off the fsync path.
 *
 * <p>Assumes every connection sets {@code PRAGMA wal_autocheckpoint=0} and {@code PRAGMA
 * journal_size_limit=67108864} (see {@link Db}). Auto-checkpoint off without a schedule grows
 * the WAL unbounded, so both halves are mandatory: off plus this scheduled passive loop.
 *
 * <p>The {@code sqlite3_wal_hook} callback is a pure observer on the writer: it records the
 * frame count and returns 0, doing no I/O and never checkpointing inline. A dedicated thread
 * with its own connection runs {@code sqlite3_wal_checkpoint_v2} with {@code PASSIVE} on
 * schedule, never inside {@code COMMIT}, so the request loop never stalls on the two fsyncs.
 *
 * <p>Whether the checkpoint copied the whole WAL is the {@code log == done} test on the
 * {@code pnLog}/{@code pnCkpt} out-params: equal means the WAL restarted. Two phases: phase A
 * checkpoints while writes continue; when the WAL did not fully restart, phase B holds writes
 * only (reads keep serving) for at most 20 ms and runs one more {@code PASSIVE} checkpoint,
 * which then only copies the frames appended during phase A.
 *
 * <p>The event-loop wait timeout derives from the pause deadline via {@link Server}, so held
 * writes wake on time. That timeout derivation is the wakeup into the epoll set: no extra
 * eventfd is needed at the 1/s schedule because {@code epollWait} already wakes on deadline
 * expiry.
 */
final class Checkpointer {
  /**
   * Checkpoint schedule in millis.
   *
   * <p>Once per second, matching the observed write rate where writes are 7.8% of traffic.
   */
  static final long CHECK_INTERVAL_MILLIS = 1000;

  /**
   * Upper bound in millis for holding writes in phase B.
   *
   * <p>Reads keep serving during the hold; the bound is 20 ms.
   */
  static final long PAUSE_MAX_MILLIS = 20;

  /**
   * Checkpoint mode for {@code sqlite3_wal_checkpoint_v2}.
   *
   * <p>{@code PASSIVE} never blocks writers and returns {@code SQLITE_BUSY} when it cannot make
   * progress. Never use FULL, RESTART or TRUNCATE in the request path.
   */
  static final int CHECKPOINT_PASSIVE = 0;

  /**
   * {@link System#nanoTime} instant until which writes are held, or 0 when none.
   */
  private static volatile long writesHeldUntilNanos;

  private final Server server;
  private final MemorySegment checkpointDb;
  private volatile boolean running;
  private Thread thread;

  /**
   * Creates a checkpointer for one worker.
   *
   * <p>Opens its own connection with the same pragma block. The commit-frame observer
   * lives on the writer, in {@link Db}, because the writer is the only connection that
   * commits.
   *
   * @param server the event loop whose wait timeout derives from the pause deadline, never null
   * @param sqlitePath filesystem path to the same database file, never null
   */
  Checkpointer(Server server, String sqlitePath) {
    this.server = Objects.requireNonNull(server, "server");
    this.checkpointDb = Db.openCheckpointConnection(sqlitePath);
  }

  /**
   * Reports the frame count seen at the most recent commit.
   *
   * @return the frame count from the most recent commit on the writer
   */
  static int observedFrames() {
    return Db.observedFrames();
  }

  /**
   * Reports whether writes are currently held for phase B.
   *
   * <p>Read paths never consult this; only the write path holds.
   *
   * @return true while the pause deadline is in the future
   */
  static boolean writesHeld() {
    return System.nanoTime() < writesHeldUntilNanos;
  }

  /**
   * Starts the dedicated checkpoint thread.
   */
  synchronized void start() {
    if (running) {
      return;
    }
    running = true;
    thread = new Thread(this::loop, "checkpointer");
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Stops the dedicated checkpoint thread.
   *
   * <p>The checkpoint handle is process-lifetime like the writer and reader handles; stopping
   * only halts the schedule.
   */
  synchronized void stop() {
    running = false;
    if (thread != null) {
      thread.interrupt();
      thread = null;
    }
    clearPauseDeadline();
  }

  /**
   * Runs one two-phase passive checkpoint.
   *
   * <p>Phase A calls {@code sqlite3_wal_checkpoint_v2} with {@code PASSIVE} while writes
   * continue. When the {@code log == done} test shows the WAL did not fully restart, phase B
   * holds writes only for at most 20 ms and runs one more {@code PASSIVE} checkpoint.
   *
   * @return true when the WAL restarted (log == done), false otherwise
   */
  boolean checkpointOnce() {
    int log;
    int done;
    int rc;
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment pnLog = arena.allocate(JAVA_INT);
      MemorySegment pnCkpt = arena.allocate(JAVA_INT);
      rc = Sqlite.walCheckpointV2(checkpointDb, pnLog, pnCkpt);
      if (rc != Sqlite.OK && rc != Sqlite.BUSY) {
        return false;
      }
      if (rc == Sqlite.BUSY) {
        return false;
      }
      log = pnLog.get(JAVA_INT, 0);
      done = pnCkpt.get(JAVA_INT, 0);
    }
    // log == done means the whole WAL was copied and restarted.
    if (log == done) {
      return true;
    }
    armPauseDeadline();
    try {
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment pnLog = arena.allocate(JAVA_INT);
        MemorySegment pnCkpt = arena.allocate(JAVA_INT);
        int rc2 = Sqlite.walCheckpointV2(checkpointDb, pnLog, pnCkpt);
        if (rc2 != Sqlite.OK && rc2 != Sqlite.BUSY) {
          return false;
        }
        if (rc2 == Sqlite.BUSY) {
          return false;
        }
        int log2 = pnLog.get(JAVA_INT, 0);
        int done2 = pnCkpt.get(JAVA_INT, 0);
        // log == done on the second pass means the restart completed.
        return log2 == done2;
      }
    } finally {
      clearPauseDeadline();
    }
  }

  private void loop() {
    while (running) {
      try {
        Thread.sleep(CHECK_INTERVAL_MILLIS);
      } catch (InterruptedException e) {
        if (!running) {
          break;
        }
      }
      if (!running) {
        break;
      }
      try {
        checkpointOnce();
      } catch (RuntimeException ignored) {
        // Best effort on the schedule; the next 1/s tick retries. Never stalls the loop.
      }
    }
  }

  /**
   * Arms the writes-only pause and shortens the loop wait via the server deadline.
   *
   * <p>The server deadline is armed first, so a reader that sees the hold also sees the
   * deadline that will wake it.
   */
  private void armPauseDeadline() {
    long deadline = System.nanoTime() + PAUSE_MAX_MILLIS * 1_000_000L;
    server.setPauseDeadlineNanos(deadline);
    writesHeldUntilNanos = deadline;
  }

  /**
   * Clears any pause deadline previously armed.
   */
  private void clearPauseDeadline() {
    writesHeldUntilNanos = 0;
    server.clearPauseDeadline();
  }
}
