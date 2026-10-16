package challenge;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WAL checkpointer that keeps {@code COMMIT} off the fsync path.
 *
 * <p>Assumes every connection sets {@code PRAGMA wal_autocheckpoint=0} and {@code PRAGMA
 * journal_size_limit=67108864} (see {@link Db}). Auto-checkpoint off without a schedule grows
 * the WAL unbounded, so both halves are mandatory: off plus this scheduled passive loop.
 *
 * <p>The {@code sqlite3_wal_hook} callback is a pure observer: it records the frame count and
 * returns 0, doing no I/O and never checkpointing inline. A dedicated thread with its own
 * connection runs {@code sqlite3_wal_checkpoint_v2} with {@code PASSIVE} on schedule, never
 * inside {@code COMMIT}, so the request loop never stalls on the two fsyncs.
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
   * WAL size cap in bytes applied on every connection.
   *
   * <p>Bounds the WAL at 67108864 bytes alongside the scheduled passive loop.
   */
  static final long JOURNAL_SIZE_LIMIT = 67108864L;

  /**
   * Checkpoint mode for {@code sqlite3_wal_checkpoint_v2}.
   *
   * <p>{@code PASSIVE} never blocks writers and returns {@code SQLITE_BUSY} when it cannot make
   * progress. Never use FULL, RESTART or TRUNCATE in the request path.
   */
  static final int CHECKPOINT_PASSIVE = 0;

  /** Last frame count seen by the {@code sqlite3_wal_hook} observer. */
  private static final AtomicInteger OBSERVED_FRAMES = new AtomicInteger();

  /** {@link System#nanoTime} instant until which writes are held, or 0 when none. */
  private static volatile long writesHeldUntilNanos;

  private final Server server;
  private final String sqlitePath;
  private final MemorySegment checkpointDb;
  private final MemorySegment walHookStub;
  private volatile boolean running;
  private Thread thread;

  /**
   * Creates a checkpointer for one worker.
   *
   * <p>Opens its own connection with the same pragma block and installs the {@code
   * sqlite3_wal_hook} pure observer on it.
   *
   * @param server the event loop whose wait timeout derives from the pause deadline, never null
   * @param sqlitePath filesystem path to the same database file, never null
   */
  Checkpointer(Server server, String sqlitePath) {
    this.server = Objects.requireNonNull(server, "server");
    this.sqlitePath = Objects.requireNonNull(sqlitePath, "sqlitePath");
    this.checkpointDb = Db.openCheckpointConnection(sqlitePath);
    this.walHookStub = installWalHook(checkpointDb);
  }

  /**
   * Pure observer for {@code sqlite3_wal_hook}.
   *
   * <p>Records the frame count and returns 0, doing no I/O and never checkpointing inline.
   *
   * @param pArg unused hook argument, may be null
   * @param db the connection handle, may be null
   * @param zDb the database name pointer, may be null
   * @param nFrames the WAL frame count at commit time
   * @return 0 always, so the commit is never aborted
   */
  static int walHookCallback(MemorySegment pArg, MemorySegment db, MemorySegment zDb, int nFrames) {
    OBSERVED_FRAMES.set(nFrames);
    return 0;
  }

  /**
   * Reports the last frame count seen by the observer.
   *
   * @return the frame count from the most recent commit hook firing
   */
  static int observedFrames() {
    return OBSERVED_FRAMES.get();
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
   */
  private void armPauseDeadline() {
    long deadline = System.nanoTime() + PAUSE_MAX_MILLIS * 1_000_000L;
    writesHeldUntilNanos = deadline;
    server.setPauseDeadlineNanos(deadline);
  }

  /**
   * Clears any pause deadline previously armed.
   */
  private void clearPauseDeadline() {
    writesHeldUntilNanos = 0;
    server.clearPauseDeadline();
  }

  private static MemorySegment installWalHook(MemorySegment db) {
    try {
      MethodHandle target =
          MethodHandles.lookup()
              .findStatic(
                  Checkpointer.class,
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
      return stub;
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }
}
