package challenge;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * SQLite reached through FFM against the amalgamation that {@code build.sh} compiles.
 *
 * <p>The library is loaded from {@code -Dsqlite.lib} (default {@code build/libsqlite3.so}) so the
 * build can pin the version rather than inheriting whatever the distro ships - PR #73 links
 * Ubuntu's {@code libsqlite3.so.0} and silently gets 3.45.1, nine minor versions behind.
 *
 * <p>Statements are prepared once per connection with {@link #PREPARE_PERSISTENT} and reused
 * with {@code reset}. That caches the query plan, never the data, which rule 5 permits by name.
 */
final class Sqlite {
  static final int OK = 0, ROW = 100, DONE = 101, BUSY = 5;
  static final int OPEN_READWRITE = 0x2, OPEN_NOMUTEX = 0x8000, OPEN_EXRESCODE = 0x02000000;

  /**
   * {@code SQLITE_STATIC} as a destructor pointer: SQLite will not copy or free the bound bytes.
   *
   * <p>Safe because every caller binds from a buffer that outlives the {@code step} that reads
   * it. Passing NULL would mean SQLITE_TRANSIENT and cost a copy on every bind.
   */
  private static final MemorySegment SQLITE_STATIC = MemorySegment.ofAddress(-1L);

  private static final Linker LINKER = Linker.nativeLinker();
  private static final SymbolLookup LIB =
      SymbolLookup.libraryLookup(
          Path.of(System.getProperty("sqlite.lib", "build/native/libsqlite3.so")), Arena.global());
  // Non-critical downcalls: a GC may run during the call. Safe at this duty cycle, and it keeps
  // the heap responsive while a statement is stepping.
  private static final Linker.Option SAFE = Linker.Option.critical(false);

  private static MethodHandle fn(String name, FunctionDescriptor fd, Linker.Option... options) {
    return LINKER.downcallHandle(
        LIB.find(name).orElseThrow(() -> new UnsatisfiedLinkError(name)), fd, options);
  }

  private static final MethodHandle INITIALIZE =
      fn("sqlite3_initialize", FunctionDescriptor.of(JAVA_INT));
  private static final MethodHandle OPEN_V2 =
      fn("sqlite3_open_v2", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
  private static final MethodHandle EXEC =
      fn("sqlite3_exec", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
  private static final MethodHandle PREPARE_V3 =
      fn("sqlite3_prepare_v3",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
  private static final MethodHandle STEP = fn("sqlite3_step", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle RESET = fn("sqlite3_reset", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle FINALIZE = fn("sqlite3_finalize", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle CLEAR_BINDINGS =
      fn("sqlite3_clear_bindings", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle ERRMSG = fn("sqlite3_errmsg", FunctionDescriptor.of(ADDRESS, ADDRESS));
  private static final MethodHandle CHANGES =
      fn("sqlite3_changes", FunctionDescriptor.of(JAVA_INT, ADDRESS), SAFE);
  private static final MethodHandle BIND_INT64 =
      fn("sqlite3_bind_int64", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG), SAFE);
  private static final MethodHandle BIND_TEXT =
      fn("sqlite3_bind_text",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS), SAFE);
  private static final MethodHandle BLOB =
      fn("sqlite3_bind_blob",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS), SAFE);
  private static final MethodHandle COLUMN_TEXT =
      fn("sqlite3_column_text", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT), SAFE);
  private static final MethodHandle COLUMN_BYTES =
      fn("sqlite3_column_bytes", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT), SAFE);
  private static final MethodHandle COLUMN_INT64 =
      fn("sqlite3_column_int64", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT), SAFE);

  /**
   * {@code sqlite3_wal_checkpoint_v2}. The {@code pnLog}/{@code pnCkpt} out-params report whether
   * the checkpoint copied the <em>whole</em> WAL - that is the test which tells us a restart is
   * achievable now, and therefore whether writes need to be held for the second phase.
   */
  private static final MethodHandle WAL_CHECKPOINT_V2 =
      fn("sqlite3_wal_checkpoint_v2",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS), SAFE);

  /** {@code sqlite3_wal_hook}: a pure observer for commit-frame counts. Returning 0 never aborts. */
  private static final MethodHandle WAL_HOOK =
      fn("sqlite3_wal_hook", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));

  private Sqlite() {}

  static final class SqliteException extends RuntimeException {
    SqliteException(String message) {
      super(message);
    }
  }

  private static RuntimeException rethrow(Throwable t) {
    if (t instanceof RuntimeException r) {
      return r;
    }
    if (t instanceof Error e) {
      throw e;
    }
    return new IllegalStateException(t);
  }

  static int initialize() {
    try {
      return (int) INITIALIZE.invokeExact();
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  static int openV2(MemorySegment path, MemorySegment ppDb, int flags) {
    try {
      return (int) OPEN_V2.invokeExact(path, ppDb, flags, MemorySegment.NULL);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  static int exec(MemorySegment db, MemorySegment sql) {
    try {
      return (int) EXEC.invokeExact(db, sql, MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  static int prepareV3(MemorySegment db, MemorySegment sql, int flags, MemorySegment ppStmt) {
    try {
      return (int) PREPARE_V3.invokeExact(db, sql, -1, flags, ppStmt, MemorySegment.NULL);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  static String errmsg(MemorySegment db) {
    try {
      MemorySegment p = (MemorySegment) ERRMSG.invokeExact(db);
      return p.reinterpret(Long.MAX_VALUE).getString(0);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  static int step(MemorySegment stmt) {
    try {
      return (int) STEP.invokeExact(stmt);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  /** Unbinds and rewinds a persistent statement for its next use. Omitting this leaks state. */
  static int reset(MemorySegment stmt) {
    try {
      return (int) RESET.invokeExact(stmt);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  static int finalizeStmt(MemorySegment stmt) {
    try {
      return (int) FINALIZE.invokeExact(stmt);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  static int clearBindings(MemorySegment stmt) {
    try {
      return (int) CLEAR_BINDINGS.invokeExact(stmt);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  static int bindInt64(MemorySegment stmt, int index, long value) {
    try {
      return (int) BIND_INT64.invokeExact(stmt, index, value);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  /** Binds UTF-8 bytes with SQLITE_STATIC: the caller owns the lifetime. */
  static int bindBlob(MemorySegment stmt, int index, MemorySegment bytes, int nBytes) {
    try {
      return (int) BLOB.invokeExact(stmt, index, bytes, nBytes, SQLITE_STATIC);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  /** Binds a NUL-terminated UTF-8 string with SQLITE_STATIC: the caller owns the lifetime. */
  static int bindText(MemorySegment stmt, int index, MemorySegment text, int nBytes) {
    try {
      return (int) BIND_TEXT.invokeExact(stmt, index, text, nBytes, SQLITE_STATIC);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  static MemorySegment columnText(MemorySegment stmt, int col) {
    try {
      return (MemorySegment) COLUMN_TEXT.invokeExact(stmt, col);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  static int columnBytes(MemorySegment stmt, int col) {
    try {
      return (int) COLUMN_BYTES.invokeExact(stmt, col);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  static long columnInt64(MemorySegment stmt, int col) {
    try {
      return (long) COLUMN_INT64.invokeExact(stmt, col);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  static int changes(MemorySegment db) {
    try {
      return (int) CHANGES.invokeExact(db);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  /** Installs the commit-frame observer. The callback records frames and returns 0 (never aborts). */
  static MemorySegment setWalHook(MemorySegment db, MemorySegment callback, MemorySegment pArg) {
    try {
      return (MemorySegment) WAL_HOOK.invokeExact(db, callback, pArg);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }

  /**
   * One PASSIVE checkpoint. Never blocks writers; returns SQLITE_BUSY if it cannot make progress.
   *
   * <p>{@code pnLog}/{@code pnCkpt} receive the WAL frame count and the frames actually copied.
   * {@code log == ckpt} means the WAL restarted, i.e. the next phase-A checkpoint has an easy job.
   */
  static int walCheckpointV2(MemorySegment db, MemorySegment pnLog, MemorySegment pnCkpt) {
    try {
      return (int) WAL_CHECKPOINT_V2.invokeExact(db, MemorySegment.NULL, 0, pnLog, pnCkpt);
    } catch (Throwable t) {
      throw rethrow(t);
    }
  }
}