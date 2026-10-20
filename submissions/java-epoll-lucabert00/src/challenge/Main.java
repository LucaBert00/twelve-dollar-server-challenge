package challenge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
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
    // The environment probe reads the descriptor ceiling from here: the numbers come from the
    // process that will serve, not from a shell whose limits may differ.
    System.out.println("startup: " + server.startupLine());
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
   * batching) and blocking writes. The close side lives in {@link Server} —
   * peer half-close, HUP/ERR reap, LRU eviction with RST — and this handler
   * asks for the close when the peer has gone or the response could not be
   * written. Idle connections therefore hold no heap here — the accumulation
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
    // A downcall rejects a heap segment: SharedUtils.checkNative throws "Heap segment not allowed"
    // for anything that is not native, and there is no Linker.Option to opt out. So the kernel
    // fills native memory and the bytes are copied into readTmp for append(). The copy is bounded
    // by READ_CHUNK and costs nothing that matters: the falsifier in AGENTS.md section 2 puts
    // per-request CPU nowhere near binding at any VU count this can serve.
    private final MemorySegment readSeg = Arena.global().allocate(READ_CHUNK, 8);

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
      boolean peerClosed = false;
      try {
        for (; ; ) {
          long r = Sys.read(fd, readSeg, READ_CHUNK);
          if (r > 0) {
            MemorySegment.copy(readSeg, ValueLayout.JAVA_BYTE, 0L, readTmp, 0, (int) r);
            append(fd, readTmp, 0, (int) r);
            if (r < READ_CHUNK) {
              break;
            }
            continue;
          }
          if (r == 0) {
            // Peer FIN: the client will send no more bytes. Keep the bytes already buffered
            // - a request can arrive in the same wakeup as the FIN - answer them, and then
            // release the descriptor. EPOLLHUP never fires for this, because the server never
            // shuts its write side, so the half-close is seen exactly here.
            peerClosed = true;
            break;
          }
          int e = Sys.errno();
          if (e == Sys.EAGAIN) {
            break;
          }
          if (e == Sys.EINTR) {
            continue;
          }
          // A hard read error: the connection is dead. Close it rather than leave the
          // descriptor in the table until the eviction watermark is reached.
          pending.remove(fd);
          server.closeConn(fd);
          return;
        }

        serveBuffered(fd);
      } catch (RuntimeException e) {
        // Never let a handler failure escape into the loop.
      } finally {
        if (peerClosed) {
          pending.remove(fd);
          server.closeConn(fd);
        }
        server.setInFlight(fd, false);
      }
    }

    /**
     * Answers the requests already buffered for one connection.
     *
     * <p>Reads one request at a time out of the per-connection accumulation, answers
     * synchronously, and frames with {@link Out}. The bytes already read stay put: a peer
     * half-close must not discard a request that arrived in the same wakeup as the FIN.
     *
     * @param fd the connection descriptor
     */
    private void serveBuffered(int fd) {
      Acc acc = pending.get(fd);
      if (acc == null || acc.len == 0) {
        return;
      }
      for (; ; ) {
        final Http.Parsed parsed;
        try {
          parsed = Http.tryParse(acc.buf, 0, acc.len);
        } catch (Http.TooLargeException e) {
          sendError(fd, 400, Http.ERR_BODY_TOO_LONG, false);
          pending.remove(fd);
          return;
        } catch (Http.MalformedException e) {
          sendError(fd, 400, "bad request", false);
          pending.remove(fd);
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
          if (!writeFully(fd, Out.response(fallback.status(), fallback.body(), req.keepAlive()))) {
            pending.remove(fd);
            server.closeConn(fd);
            return;
          }
          if (!req.keepAlive()) {
            pending.remove(fd);
            return;
          }
          continue;
        }
        if (!writeFully(fd, Out.response(reply.status(), reply.body(), req.keepAlive()))) {
          pending.remove(fd);
          server.closeConn(fd);
          return;
        }
        if (!req.keepAlive()) {
          pending.remove(fd);
          return;
        }
        acc = pending.get(fd);
        if (acc == null || acc.len == 0) {
          return;
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
        if (!writeFully(fd, Out.response(status, Json.error(message), keepAlive))) {
          pending.remove(fd);
          server.closeConn(fd);
        }
      } catch (RuntimeException ignored) {
        // Client already gone; the loop reaps its side.
      }
    }

    /**
     * Flushes one framed response.
     *
     * <p>Spins briefly on a full send buffer, then gives up rather than stalling the only
     * loop thread behind one slow reader. The errno read here is the write's own errno,
     * captured by the downcall, so a real {@code EAGAIN} is never confused with a stale
     * value left behind by an earlier call.
     *
     * @param fd the connection descriptor
     * @param framed the complete response bytes
     * @return true when every byte reached the kernel, false when the connection is unusable
     */
    private boolean writeFully(int fd, byte[] framed) {
      // Same reason as the read buffer: a downcall will not take a heap segment. Arena.ofAuto frees
      // the block once it becomes unreachable, so no response leaks and the early returns below
      // need no try/finally. One allocation per response is free at this duty cycle (falsifier,
      // AGENTS.md section 2); reusing a fixed buffer would need a size cap and a fallback path for
      // the day a response outgrows it, which is a bug waiting to happen for no measurable gain.
      MemorySegment msg = Arena.ofAuto().allocate(framed.length, 8);
      MemorySegment.copy(framed, 0, msg, ValueLayout.JAVA_BYTE, 0L, framed.length);
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
            return false;
          }
          Thread.onSpinWait();
          continue;
        }
        // EPIPE or ECONNRESET means the peer is gone; any other error leaves the connection
        // unusable too. A half-written response would corrupt the next request on a keep-alive
        // connection, so the connection goes rather than the bytes.
        return false;
      }
      return true;
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
