package challenge;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
   * <p>Kept deliberately small on purpose: blocking writes and a write queue. A write op
   * is prepared here and handed to {@link #flushWrites}, which batches the queued ops
   * into one transaction and answers each of them only after the commit that covers it.
   * A busy write lock never fails a request: the op waits for the lock. The close side
   * lives in {@link Server} — peer half-close, HUP/ERR reap, LRU eviction with RST — and
   * this handler asks for the close when the peer has gone or the response could not be
   * written. Idle connections therefore hold no heap here — the accumulation entry is
   * dropped as soon as its bytes are consumed — and idle capacity stays bounded by
   * descriptors, not memory.
   */
  static final class IoHandler implements Server.Handler, Http.WriteExec {

    /** Largest single read from the kernel per wakeup. */
    private static final int READ_CHUNK = 16 * 1024;

    /** Bounded spins on a full send buffer before dropping to protect the loop. */
    private static final int WRITE_SPINS = 1_000;

    /**
     * Retry interval for a busy write lock.
     *
     * <p>AGENTS.md section 4.2: a busy lock is never blocked on. The queued writes are
     * kept and retried on the next loop pass, at most a millisecond later.
     */
    private static final long WRITE_RETRY_NANOS = 1_000_000L;

    /** Accumulated unread bytes per fd. Only connections with a partial request hold one. */
    private final Map<Integer, Acc> pending = new HashMap<>();

    /** Write ops waiting for the write lock, in arrival order. */
    private final ArrayDeque<PendingWrite> writeQueue = new ArrayDeque<>();

    /** Connections with an outstanding queued write op; their answers have to come first. */
    private final Set<Integer> writeBlocked = new HashSet<>();

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

        if (writeBlocked.contains(fd)) {
          // A queued write op has to be answered before any later request on this
          // connection. The bytes are buffered so the socket drains, but no answer
          // goes out yet.
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

    @Override
    public void onIdle() {
      try {
        flushWrites();
      } catch (RuntimeException e) {
        // The write path is broken. Fail everything queued rather than leaving the
        // clients waiting for an answer that will never come.
        while (!writeQueue.isEmpty()) {
          answer(writeQueue.poll(), new Http.Reply(500, Json.error(Http.ERR_INTERNAL)));
        }
      }
    }

    @Override
    public Http.Reply run(int fd, Http.WriteOp op, boolean keepAlive) {
      writeQueue.add(new PendingWrite(fd, op, keepAlive, System.nanoTime()));
      writeBlocked.add(fd);
      return Http.DEFERRED;
    }

    /**
     * Runs the queued write ops.
     *
     * <p>One {@code BEGIN IMMEDIATE} per op. On {@code SQLITE_BUSY} the queue is kept, the
     * loop is asked to wake in a millisecond, and an op that has waited past the give-up
     * bound is failed with {@code 500}. The answer for an op goes out only after the
     * commit that covers it.
     */
    private void flushWrites() {
      while (!writeQueue.isEmpty()) {
        if (!Db.tryBeginImmediate()) {
          // The write lock is held elsewhere. Keep the queue, wake in a millisecond, and
          // fail only the ops that are past the give-up bound.
          long now = System.nanoTime();
          for (Iterator<PendingWrite> it = writeQueue.iterator(); it.hasNext(); ) {
            PendingWrite queued = it.next();
            if (Db.writeGiveUp(queued.queuedAtNanos)) {
              it.remove();
              answer(queued, new Http.Reply(500, Json.error(Http.ERR_INTERNAL)));
            }
          }
          server.setWriteRetryDeadlineNanos(now + WRITE_RETRY_NANOS);
          return;
        }
        PendingWrite queued = writeQueue.poll();
        Http.Reply reply;
        try {
          reply = Http.executeWrite(queued.op);
        } catch (RuntimeException e) {
          Db.rollbackWrites();
          answer(queued, new Http.Reply(500, Json.error(Http.ERR_INTERNAL)));
          return;
        }
        if (!Db.commitWrites()) {
          answer(queued, new Http.Reply(500, Json.error(Http.ERR_INTERNAL)));
          return;
        }
        answer(queued, reply);
      }
      server.clearWriteRetryDeadline();
    }

    /**
     * Sends the answer for a queued write op and releases the connection.
     *
     * <p>The answer goes out after the commit that covered the op. The connection is
     * unblocked afterwards, and any bytes that arrived meanwhile are served.
     *
     * @param queued the op that was answered, never null
     * @param reply the answer, never null
     */
    private void answer(PendingWrite queued, Http.Reply reply) {
      int fd = queued.fd;
      writeBlocked.remove(fd);
      if (!writeFully(fd, Out.response(reply.status(), reply.body(), queued.keepAlive))) {
        pending.remove(fd);
        server.closeConn(fd);
        return;
      }
      Acc acc = pending.get(fd);
      if (acc != null && acc.len > 0) {
        serveBuffered(fd);
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
          reply = Http.handle(fd, req, uptimeS, Jwt.CHECK, this);
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
        if (reply.status() == Http.DEFERRED_STATUS) {
          // The write op is queued. Its answer follows the commit that covers it, so
          // nothing else on this connection may be answered before it.
          return;
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

  /** One queued write op: the connection it belongs to, the op, and when it was queued. */
  static final class PendingWrite {
    final int fd;
    final Http.WriteOp op;
    final boolean keepAlive;
    final long queuedAtNanos;

    /**
     * Creates a queued write.
     *
     * @param fd the connection the op arrived on
     * @param op the prepared op
     * @param keepAlive the request's keep-alive decision
     * @param queuedAtNanos the {@link System#nanoTime} instant the op was queued
     */
    PendingWrite(int fd, Http.WriteOp op, boolean keepAlive, long queuedAtNanos) {
      this.fd = fd;
      this.op = op;
      this.keepAlive = keepAlive;
      this.queuedAtNanos = queuedAtNanos;
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
