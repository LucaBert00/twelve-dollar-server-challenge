package challenge;

import java.lang.foreign.MemorySegment;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Worker entry point. One process runs one {@link Server} event loop plus one
 * {@link Checkpointer} thread; {@code start.sh} owns the two processes.
 *
 * <p>Config comes only from {@code System.getenv} for {@code SQLITE_PATH},
 * {@code JWT_SECRET}, {@code HOST} and {@code PORT}. No {@code .env} file is
 * consulted, so a stray file can never override the benchmark environment.
 *
 * <p>Startup is fail-fast: a missing database file, an empty secret, an
 * unparseable host or port, or any pragma drift in {@link Db} throws before
 * the socket binds. The bind itself is immediate, so {@code GET /health}
 * answers within a second, far inside the 60 s budget.
 */
public final class Main {

  private Main() {}

  /**
   * Starts one worker.
   *
   * @param args unused
   */
  public static void main(String[] args) {
    String sqlitePath = System.getenv("SQLITE_PATH");
    if (sqlitePath == null || sqlitePath.isEmpty()) {
      throw new IllegalStateException("SQLITE_PATH must name an existing database file");
    }
    String secret = System.getenv("JWT_SECRET");
    if (secret == null || secret.isEmpty()) {
      throw new IllegalArgumentException("JWT_SECRET must not be empty");
    }
    String hostRaw = System.getenv("HOST");
    if (hostRaw == null || hostRaw.isEmpty()) {
      hostRaw = "0.0.0.0";
    }
    String portRaw = System.getenv("PORT");
    if (portRaw == null || portRaw.isEmpty()) {
      portRaw = "80";
    }
    final InetAddress host;
    try {
      host = InetAddress.getByName(hostRaw);
    } catch (Exception e) {
      throw new IllegalStateException("HOST unparseable: " + hostRaw, e);
    }
    final int port;
    try {
      port = Integer.parseInt(portRaw.trim());
    } catch (NumberFormatException e) {
      throw new IllegalStateException("PORT unparseable: " + portRaw, e);
    }
    if (port < 1 || port > 65535) {
      throw new IllegalStateException("PORT out of range: " + port);
    }

    Jwt.init(secret);
    // Opens writer plus reader handles and asserts every pragma, including
    // foreign_keys=ON. Any drift throws here, before the socket binds.
    Db.init(sqlitePath);

    final long startNanos = System.nanoTime();
    final IoHandler io = new IoHandler(startNanos);
    final Server server = new Server(host, port, io);
    io.attach(server);
    final Checkpointer checkpointer = new Checkpointer(server, sqlitePath);
    checkpointer.start();

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    checkpointer.stop();
                  } catch (RuntimeException ignored) {
                    // Best effort on the way out.
                  }
                  server.requestStop();
                  try {
                    Db.close();
                  } catch (RuntimeException ignored) {
                    // Best effort on the way out.
                  }
                }));

    // Single-threaded by design: accept, readiness and eviction share this
    // thread, so the handler below needs no locks.
    server.run();
  }

  /**
   * Dispatch seam for the event loop. Reads one request at a time out of
   * per-connection accumulation, answers synchronously, and frames with
   * {@link Out}. Each response waits for its own {@code COMMIT} inside
   * {@link Http}, so a failed commit answers {@code 500} for its op.
   *
   * <p>Kept deliberately small on purpose: per-request commit (no cross-loop
   * batching), blocking writes, and no server-initiated close. The close side
   * stays with {@link Server} (client FIN, HUP/ERR reap, LRU eviction with
   * RST). Idle connections therefore hold no heap here — the accumulation
   * entry is dropped as soon as its bytes are consumed — and idle capacity
   * stays bounded by descriptors, not memory.
   */
  static final class IoHandler implements Server.Handler {

    /** Largest single read from the kernel per wakeup. */
    private static final int READ_CHUNK = 16 * 1024;

    /** Bounded spins on a full send buffer before dropping to protect the loop. */
    private static final int WRITE_SPINS = 1_000;

    /** Accumulated unread bytes per fd. Only connections with a partial request hold one. */
    private final Map<Integer, Acc> pending = new HashMap<>();

    private final byte[] readTmp = new byte[READ_CHUNK];
    private final MemorySegment readSeg = MemorySegment.ofArray(readTmp);

    private final long startNanos;
    private Server server;

    IoHandler(long startNanos) {
      this.startNanos = startNanos;
    }

    void attach(Server server) {
      this.server = server;
    }

    @Override
    public void onReadable(int fd) {
      if (server == null) {
        return;
      }
      server.setInFlight(fd, true);
      try {
        for (; ; ) {
          long r = Sys.read(fd, readSeg, READ_CHUNK);
          if (r > 0) {
            append(fd, readTmp, 0, (int) r);
            if (r < READ_CHUNK) {
              break;
            }
            continue;
          }
          if (r == 0) {
            // Client FIN. Drop our bytes; the loop reaps its side on HUP.
            pending.remove(fd);
            server.setInFlight(fd, false);
            return;
          }
          int e = Sys.errno();
          if (e == Sys.EAGAIN) {
            break;
          }
          if (e == Sys.EINTR) {
            continue;
          }
          pending.remove(fd);
          server.setInFlight(fd, false);
          return;
        }

        Acc acc = pending.get(fd);
        if (acc == null || acc.len == 0) {
          server.setInFlight(fd, false);
          return;
        }
        for (; ; ) {
          final Http.Parsed parsed;
          try {
            parsed = Http.tryParse(acc.buf, 0, acc.len);
          } catch (Http.TooLargeException e) {
            sendError(fd, 400, Http.ERR_BODY_TOO_LONG, false);
            pending.remove(fd);
            server.setInFlight(fd, false);
            return;
          } catch (Http.MalformedException e) {
            sendError(fd, 400, "bad request", false);
            pending.remove(fd);
            server.setInFlight(fd, false);
            return;
          }
          if (parsed == null) {
            // Partial request: keep the bytes and stay marked so eviction skips us.
            return;
          }
          Http.Request req = parsed.request();
          int consumed = parsed.consumed();
          int remaining = acc.len - consumed;
          if (remaining > 0) {
            System.arraycopy(acc.buf, consumed, acc.buf, 0, remaining);
          }
          acc.len = remaining;
          if (acc.len == 0) {
            pending.remove(fd);
          }
          long uptimeS = (System.nanoTime() - startNanos) / 1_000_000_000L;
          final Http.Reply reply;
          try {
            reply = Http.handle(req, uptimeS, Jwt.CHECK);
          } catch (RuntimeException e) {
            Http.Reply fallback = new Http.Reply(500, Json.error(Http.ERR_INTERNAL));
            writeFully(fd, Out.response(fallback.status(), fallback.body(), req.keepAlive()));
            if (!req.keepAlive()) {
              pending.remove(fd);
              server.setInFlight(fd, false);
              return;
            }
            continue;
          }
          writeFully(fd, Out.response(reply.status(), reply.body(), req.keepAlive()));
          if (!req.keepAlive()) {
            pending.remove(fd);
            server.setInFlight(fd, false);
            return;
          }
          if (acc.len == 0 && pending.get(fd) == null) {
            break;
          }
          acc = pending.get(fd);
          if (acc == null || acc.len == 0) {
            break;
          }
        }
        Acc after = pending.get(fd);
        if (after == null || after.len == 0) {
          server.setInFlight(fd, false);
        }
      } catch (RuntimeException e) {
        try {
          server.setInFlight(fd, false);
        } catch (RuntimeException ignored) {
          // Never let a handler failure escape into the loop.
        }
      }
    }

    @Override
    public void onWritable(int fd) {
      // No deferred writes: every response is flushed inside onReadable with a
      // bounded spin, so there is nothing to resume here.
    }

    private void append(int fd, byte[] src, int off, int n) {
      Acc acc = pending.get(fd);
      if (acc == null) {
        acc = new Acc();
        pending.put(fd, acc);
      }
      acc.append(src, off, n);
    }

    private void sendError(int fd, int status, String message, boolean keepAlive) {
      try {
        writeFully(fd, Out.response(status, Json.error(message), keepAlive));
      } catch (RuntimeException ignored) {
        // Client already gone; the loop reaps its side.
      }
    }

    /**
     * Flushes one framed response. Spins briefly on a full send buffer, then
     * drops rather than stalling the only loop thread behind one slow reader.
     * Drops are rare — responses are a few kilobytes and readers drain
     * promptly — and the client retry path, not a hung worker, carries them.
     */
    private void writeFully(int fd, byte[] framed) {
      MemorySegment msg = MemorySegment.ofArray(framed);
      long off = 0;
      int spins = 0;
      while (off < framed.length) {
        long w = Sys.write(fd, msg.asSlice(off, framed.length - off), framed.length - off);
        if (w > 0) {
          off += w;
          spins = 0;
          continue;
        }
        int e = Sys.errno();
        if (e == Sys.EINTR) {
          continue;
        }
        if (e == Sys.EAGAIN) {
          if (++spins > WRITE_SPINS) {
            return;
          }
          Thread.onSpinWait();
          continue;
        }
        return;
      }
    }
  }

  /** Growable per-connection accumulation. Lives only while a partial request is pending. */
  static final class Acc {
    byte[] buf = new byte[4096];
    int len;

    void append(byte[] src, int off, int n) {
      if (len + n > buf.length) {
        int size = buf.length;
        while (size < len + n) {
          size <<= 1;
        }
        byte[] grown = new byte[size];
        System.arraycopy(buf, 0, grown, 0, len);
        buf = grown;
      }
      System.arraycopy(src, off, buf, len, n);
      len += n;
    }
  }
}
