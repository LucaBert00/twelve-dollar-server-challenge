package challenge;

import com.sun.management.UnixOperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Single-threaded epoll event loop over raw Linux epoll reached through {@code java.lang.foreign}.
 *
 * <p>One {@code Server} is one worker process. Two workers each bind the same port with
 * SO_REUSEPORT and the kernel spreads connections across them, so descriptors scale with the
 * worker count instead of stopping at one process's ceiling. The bind uses backlog 4096 and every
 * accepted socket gets TCP_NODELAY.
 *
 * <p>Bytes live in worker-shared buffers wherever the worker can share them. Each live
 * connection costs one small {@link Conn} (tens of bytes) plus the kernel socket; the
 * per-request accumulation buffer is 4 KiB and lives only while a request is partial.
 *
 * <p>The connection budget has two ceilings, and they are coupled: the descriptor budget and
 * the RAM budget. The high watermark is the lower of the two, so eviction fires before
 * whichever ceiling binds. Accept triggers LRU eviction of idle connections on the budget
 * check and on EMFILE/ENFILE/ENOMEM, and evicted connections close with SO_LINGER=0 so the
 * close sends RST instead of accumulating TIME_WAIT. Only connections with no request in
 * flight are ever evicted.
 *
 * <p>The epoll wait timeout derives from the checkpointer pause deadline (E05 contract), so held
 * writes wake on time. This ticket owns sockets only and never produces responses.
 */
final class Server {
  /** Listen backlog: a 60 s ramp to tens of thousands of sockets overruns a small one. */
  static final int BACKLOG = 4096;

  /** Events drained per epoll_wait wakeup; the events buffer is worker-shared. */
  static final int MAX_EVENTS = 1024;

  /** LRU entries scanned per eviction; bounds eviction cost on the accept path. */
  static final int EVICT_SCAN = 64;

  /** epoll_wait timeout with no pause deadline outstanding. */
  static final int DEFAULT_WAIT_MILLIS = 1000;

  /**
   * Kernel and page-cache headroom reserved for the box, in bytes.
   *
   * <p>An estimate, not a measurement. The database's own page cache is reclaimable - the
   * kernel drops clean pages before it kills anything - but the kernel's slab, the page
   * tables and the socket structures are not.
   */
  static final long KERNEL_RESERVE_BYTES = 192L * 1024 * 1024;

  /**
   * Kernel cost of one idle TCP socket, in bytes.
   *
   * <p>An estimate: the socket structure plus its default receive and send buffers. This is
   * the number the environment probe's measurement refines, and it is the term the capacity
   * argument is most sensitive to.
   */
  static final long BYTES_PER_IDLE_SOCKET = 5L * 1024;

  /**
   * JVM overhead beyond the heap, per worker, in bytes.
   *
   * <p>An estimate: the class metadata, the code cache, the thread stacks and the direct
   * buffers.
   */
  static final long JVM_OVERHEAD_BYTES = 96L * 1024 * 1024;

  /** Worker-shared I/O scratch size in bytes; E03 parses out of this, not per-conn buffers. */
  static final int SHARED_BUF_BYTES = 1 << 16;

  /** {@link Conn} flag bit: a request is being processed, so the connection is not evictable. */
  static final int F_IN_FLIGHT = 1;

  /**
   * Dispatch seam for readable connections and for the end of an event-loop pass.
   *
   * <p>E03/E04 implement this; the loop only owns sockets and never parses or responds itself.
   */
  interface Handler {
    /**
     * Handles a readable connection.
     *
     * @param fd the ready connection descriptor, never negative
     */
    void onReadable(int fd);

    /**
     * Runs after every event-loop pass.
     *
     * <p>The write flush lives here: queued write ops are batched into one transaction at
     * the end of the pass, so every answer in the batch follows the commit that covers it.
     */
    void onIdle();
  }

  /**
   * Per-connection state: descriptor, intrusive LRU links, activity stamp, flags.
   *
   * <p>Four ints plus a long plus the object header is tens of bytes, far inside the 2 KB
   * per-connection budget. Request bytes stay in the worker-shared scratch buffer.
   */
  static final class Conn {
    /** Connection descriptor, also the index into the fd-indexed table. */
    final int fd;

    /** Previous fd in the LRU chain, or -1 at the eviction end. */
    int prev = -1;

    /** Next fd in the LRU chain, or -1 at the recent end. */
    int next = -1;

    /** Last activity stamp from {@link System#nanoTime}. */
    long lastActiveNanos;

    /** Bitmask; see {@link #F_IN_FLIGHT}. */
    int flags;

    /**
     * Creates state for an accepted connection.
     *
     * @param fd the accepted descriptor, never negative
     */
    Conn(int fd) {
      this.fd = fd;
      touch();
    }

    /** Refreshes the activity stamp to now. */
    void touch() {
      lastActiveNanos = System.nanoTime();
    }

    /**
     * Reports whether a request is in flight.
     *
     * @return true while E03/E04 own the connection, so eviction must skip it
     */
    boolean inFlight() {
      return (flags & Server.F_IN_FLIGHT) != 0;
    }
  }

  private final InetAddress host;
  private final int port;
  private final Handler handler;

  /** Evict down to {@code low} once live connections reach {@code high}. */
  private final int high;

  /** Hysteresis floor for budget eviction. */
  private final int low;

  /** The high watermark the descriptor budget alone allows. */
  private final int fdHigh;

  /** The high watermark the memory budget alone allows. */
  private final int memoryHigh;

  /** The box's memory limit in bytes, 0 when it could not be read. */
  private final long boxBytes;

  private Conn[] table;
  private int lruHead = -1;
  private int lruTail = -1;
  private int activeCount;
  private int epFd = -1;
  private int listenFd = -1;
  private volatile boolean running;
  private volatile long pauseDeadlineNanos;
  private volatile long writeRetryDeadlineNanos;
  private MemorySegment events;
  private MemorySegment sharedBuf;

  /**
   * Creates a worker bound later by {@link #run}.
   *
   * @param host bind address; production passes the 0.0.0.0 wildcard
   * @param port bind port; production serves 80 directly with no proxy
   * @param handler dispatch seam, may be null until E03/E04 provide one
   */
  Server(InetAddress host, int port, Handler handler) {
    this.host = Objects.requireNonNull(host, "host");
    this.port = port;
    this.handler = handler;
    long limit = fdLimit();
    this.fdHigh = highMark(limit);
    long boxBytes = boxMemoryBytes();
    int workers = Math.max(1, Integer.getInteger("challenge.workers", 1));
    this.memoryHigh = memoryHighMark(boxBytes, workers);
    // The score is connection capacity, and the two ceilings are coupled: the descriptor
    // budget and the RAM budget. The lower one binds, so take the lower one.
    this.high = (int) Math.min(fdHigh, memoryHigh);
    this.low = lowMark(high);
    this.boxBytes = boxBytes;
    int size = (int) Math.min(limit, 1 << 20);
    if (size < 16) {
      size = 16;
    }
    this.table = new Conn[size];
  }

  /**
   * Reads the box's memory limit.
   *
   * <p>The cgroup limit first, because that is what the box is actually held to; the host
   * total only as a fallback.
   *
   * @return the limit in bytes, or 0 when it cannot be read
   */
  static long boxMemoryBytes() {
    Long cgroup = readLongFile(Path.of("/sys/fs/cgroup/memory.max"));
    if (cgroup == null) {
      cgroup = readLongFile(Path.of("/sys/fs/cgroup/memory/memory.limit_in_bytes"));
    }
    if (cgroup != null && cgroup > 0 && cgroup < Long.MAX_VALUE / 2) {
      return cgroup;
    }
    Long total = readMemInfoTotal();
    return total == null ? 0L : total;
  }

  /**
   * Derives the memory-based connection high-water for one worker.
   *
   * <p>What the box can hold for sockets, after the kernel reserve and every worker's own
   * fixed reservation, divided by the kernel cost of one idle socket.
   *
   * @param boxBytes the box's memory limit, 0 when unknown
   * @param workers the number of workers sharing the box, at least 1
   * @return the live-connection count one worker may hold on memory grounds
   */
  static int memoryHighMark(long boxBytes, int workers) {
    if (boxBytes <= 0) {
      // The box limit could not be read. The descriptor budget alone is the ceiling, and
      // the eviction watermark stays dormant rather than guessing a RAM budget.
      return Integer.MAX_VALUE;
    }
    long fixed = KERNEL_RESERVE_BYTES + (long) workers * perWorkerFixedBytes();
    long forSockets = boxBytes - fixed;
    if (forSockets <= 0) {
      return 0;
    }
    return (int) Math.min(Integer.MAX_VALUE, forSockets / BYTES_PER_IDLE_SOCKET);
  }

  /**
   * The fixed memory one worker reserves, in bytes.
   *
   * <p>Its heap, its two SQLite page caches and its JVM overhead. The checkpoint
   * connection gets no page cache, so it adds nothing here.
   *
   * @return the reservation in bytes
   */
  static long perWorkerFixedBytes() {
    long heap = Runtime.getRuntime().maxMemory();
    long sqlite = 2L * -Db.CACHE_SIZE * 1024L;
    return heap + sqlite + JVM_OVERHEAD_BYTES;
  }

  /**
   * Reads one whole-number file, ignoring an "unlimited" marker.
   *
   * @param path the file to read
   * @return the value, or null when the file is missing or not a number
   */
  private static Long readLongFile(Path path) {
    try {
      String text = Files.readString(path).trim();
      return Long.parseLong(text);
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  /**
   * Reads the host's total memory from {@code /proc/meminfo}.
   *
   * @return the total in bytes, or null when it cannot be read
   */
  private static Long readMemInfoTotal() {
    try (BufferedReader reader = Files.newBufferedReader(Path.of("/proc/meminfo"))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("MemTotal:")) {
          String[] parts = line.split("\\s+");
          if (parts.length >= 2) {
            return Long.parseLong(parts[1]) * 1024L;
          }
        }
      }
    } catch (IOException | RuntimeException e) {
      return null;
    }
    return null;
  }

  /**
   * Creates a worker on the 0.0.0.0 wildcard for the given port.
   *
   * @param port bind port, 80 in production
   * @param handler dispatch seam, may be null until E03/E04 provide one
   * @return a worker sharing the port with its siblings through SO_REUSEPORT
   */
  static Server wildcard(int port, Handler handler) {
    try {
      return new Server(InetAddress.getByName("0.0.0.0"), port, handler);
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Reads the descriptor ceiling from the JVM instead of hardcoding it.
   *
   * @return the OS descriptor limit, or a fallback where the Unix bean is unavailable
   */
  static long fdLimit() {
    try {
      if (ManagementFactory.getOperatingSystemMXBean() instanceof UnixOperatingSystemMXBean unix) {
        return unix.getMaxFileDescriptorCount();
      }
    } catch (UnsupportedOperationException ignored) {
      // Fall through to the fallback below.
    }
    return 1 << 16;
  }

  /**
   * Reports the descriptor ceiling and the eviction watermarks in one line.
   *
   * <p>Printed at startup so the environment probe captures the numbers from the process that
   * will actually serve, rather than from a shell whose limits may differ.
   *
   * @return the line, never null
   */
  String startupLine() {
    return "fd-ceiling="
        + fdLimit()
        + " fd-high="
        + fdHigh
        + " mem-high="
        + memoryHigh
        + " high="
        + high
        + " low="
        + low
        + " box-bytes="
        + boxBytes
        + " nofile=["
        + procNoFile()
        + "]";
  }

  /**
   * Reads this process's own "Max open files" line from {@code /proc/self/limits}.
   *
   * <p>The JVM raises the soft {@code RLIMIT_NOFILE} to the hard limit at startup, so the soft
   * limit a shell reported is not the ceiling this process runs under. The kernel's own view,
   * read from inside the process, shows the limits actually in force.
   *
   * @return the line's contents, or a short reason when it cannot be read
   */
  static String procNoFile() {
    try (BufferedReader reader = Files.newBufferedReader(Path.of("/proc/self/limits"))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("Max open files")) {
          return line.trim();
        }
      }
      return "Max open files not found";
    } catch (IOException | RuntimeException e) {
      return "unreadable: " + e;
    }
  }

  /**
   * Derives the high watermark from the descriptor limit.
   *
   * @param limit the value from {@link #fdLimit}, always positive
   * @return the live-connection count that starts budget eviction
   */
  static int highMark(long limit) {
    return (int) Math.max(limit - 1024, limit * 9 / 10);
  }

  /**
   * Derives the low watermark from the high watermark.
   *
   * @param high the value from {@link #highMark}, always positive
   * @return the live-connection count that stops budget eviction
   */
  static int lowMark(int high) {
    return high - Math.max(high / 50, 16);
  }

  /**
   * Runs the event loop on the calling thread until {@link #requestStop}.
   *
   * <p>Single-threaded by design: accept, readiness and eviction share one thread, so the table
   * and LRU need no locks.
   */
  void run() {
    if (epFd >= 0) {
      throw new IllegalStateException("already running");
    }
    epFd = Sys.epollCreate();
    // reusePort=true sets SO_REUSEPORT before bind on every worker, so siblings share the port.
    listenFd = Sys.listen(host, port, BACKLOG, true);
    if (Sys.epollCtl(epFd, Sys.EPOLL_CTL_ADD, listenFd, Sys.EPOLLIN) != 0) {
      throw new IllegalStateException("epoll_ctl listen: errno " + Sys.errno());
    }
    events = Arena.global().allocate((long) Sys.EVENT_SIZE * MAX_EVENTS, 8);
    sharedBuf = Arena.global().allocate(SHARED_BUF_BYTES, 8);
    running = true;
    try {
      while (running) {
        int n = Sys.epollWait(epFd, events, MAX_EVENTS, epollTimeoutMillis());
        if (n < 0) {
          if (Sys.errno() == Sys.EINTR) {
            continue;
          }
          throw new IllegalStateException("epoll_wait: errno " + Sys.errno());
        }
        for (int i = 0; i < n && running; i++) {
          dispatch(Sys.eventFd(events, i), Sys.eventFlags(events, i));
        }
        if (handler != null) {
          handler.onIdle();
        }
      }
    } finally {
      shutdown();
    }
  }

  /**
   * Asks the loop to stop; the current epoll_wait wakes on its timeout at the latest.
   */
  void requestStop() {
    running = false;
  }

  /**
   * Arms the checkpointer pause deadline (E05 contract); 0 clears it.
   *
   * @param deadlineNanos the {@link System#nanoTime} instant held writes must wake by
   */
  void setPauseDeadlineNanos(long deadlineNanos) {
    pauseDeadlineNanos = deadlineNanos;
  }

  /**
   * Clears any pause deadline previously armed with {@link #setPauseDeadlineNanos}.
   */
  void clearPauseDeadline() {
    pauseDeadlineNanos = 0;
  }

  /**
   * Arms the write-retry deadline; 0 clears it.
   *
   * <p>Set when the write lock is busy, so the loop wakes in time to retry the queued
   * writes instead of sleeping out the default wait.
   *
   * @param deadlineNanos the {@link System#nanoTime} instant the retry is due by
   */
  void setWriteRetryDeadlineNanos(long deadlineNanos) {
    writeRetryDeadlineNanos = deadlineNanos;
  }

  /**
   * Clears any write-retry deadline previously armed.
   */
  void clearWriteRetryDeadline() {
    writeRetryDeadlineNanos = 0;
  }

  /**
   * Derives the epoll wait timeout from the pause deadline so held writes wake on time.
   *
   * @return milliseconds to wait: the earliest of the pause deadline, the write-retry
   *     deadline and the default
   */
  int epollTimeoutMillis() {
    return epollTimeoutMillis(pauseDeadlineNanos, writeRetryDeadlineNanos, System.nanoTime());
  }

  /**
   * Pure timeout computation for the deadline contract.
   *
   * <p>The wait is the earliest of the checkpointer pause deadline, the write-retry
   * deadline and the default. A held write and a busy write lock both have to wake the
   * loop on time.
   *
   * @param pauseNanos the pause deadline, 0 for none
   * @param retryNanos the write-retry deadline, 0 for none
   * @param nowNanos current {@link System#nanoTime} reading
   * @return wait milliseconds, 0 when a deadline already passed
   */
  static int epollTimeoutMillis(long pauseNanos, long retryNanos, long nowNanos) {
    long wait = DEFAULT_WAIT_MILLIS;
    long pause = millisUntil(pauseNanos, nowNanos);
    if (pause < wait) {
      wait = pause;
    }
    long retry = millisUntil(retryNanos, nowNanos);
    if (retry < wait) {
      wait = retry;
    }
    return (int) wait;
  }

  /**
   * Milliseconds from now until a deadline, or {@link Long#MAX_VALUE} when there is none.
   *
   * @param deadlineNanos the deadline in {@link System#nanoTime} units, 0 for none
   * @param nowNanos current {@link System#nanoTime} reading
   * @return the wait in milliseconds, 0 when the deadline already passed
   */
  private static long millisUntil(long deadlineNanos, long nowNanos) {
    if (deadlineNanos <= 0) {
      return Long.MAX_VALUE;
    }
    long dt = (deadlineNanos - nowNanos) / 1_000_000L;
    return dt < 0 ? 0 : dt;
  }

  /**
   * Marks a connection as holding a request or idle for eviction purposes.
   *
   * @param fd fd of a live connection
   * @param inFlight true while E03/E04 own the connection, false once it goes idle
   */
  void setInFlight(int fd, boolean inFlight) {
    Conn c = get(fd);
    if (c == null) {
      return;
    }
    if (inFlight) {
      c.flags |= F_IN_FLIGHT;
    } else {
      c.flags &= ~F_IN_FLIGHT;
      c.touch();
      moveToTail(fd);
    }
  }

  /**
   * Reports the live connection count.
   *
   * @return connections currently in the table
   */
  int activeCount() {
    return activeCount;
  }

  /**
   * Exposes the worker-shared scratch buffer for E03 parsing.
   *
   * @return the shared buffer, allocated on {@link #run}
   */
  MemorySegment sharedBuffer() {
    return sharedBuf;
  }

  private void dispatch(int fd, int flags) {
    if (fd == listenFd) {
      acceptAll();
      return;
    }
    Conn c = get(fd);
    if (c == null) {
      return;
    }
    if ((flags & (Sys.EPOLLERR | Sys.EPOLLHUP)) != 0) {
      closeConn(fd);
      return;
    }
    if ((flags & Sys.EPOLLIN) != 0) {
      c.touch();
      moveToTail(fd);
      // No handler yet (E03 owns parsing): close rather than leave bytes unread, which would
      // re-wake level-triggered epoll in a tight loop.
      if (handler != null) {
        handler.onReadable(fd);
      } else {
        closeConn(fd);
        return;
      }
      if (get(fd) != c) {
        // The handler closed this connection. Compare the state object rather than
        // the descriptor number: a later accept could reuse the number.
        return;
      }
    }
    if ((flags & Sys.EPOLLRDHUP) != 0) {
      // The peer half-closed its write side and will send nothing more. The handler has
      // already drained and answered whatever arrived with the FIN, so the descriptor is
      // released now rather than sitting in CLOSE_WAIT until the eviction watermark.
      closeConn(fd);
      return;
    }
  }

  /**
   * Accepts until EAGAIN, shedding idle connections first on both pressure paths.
   *
   * <p>Never throws or spins on exhaustion: when nothing idle remains the backlog waits for the
   * next wakeup instead of stealing the only core.
   */
  private void acceptAll() {
    for (;;) {
      if (activeCount >= high) {
        // Budget-check path with hysteresis: shed idle connections down to low.
        int freed = 0;
        while (activeCount > low && evictIdle()) {
          freed++;
        }
        if (freed == 0) {
          break;
        }
      }
      int cfd = Sys.accept(listenFd);
      if (cfd < 0) {
        int e = Sys.errno();
        if (e == Sys.EAGAIN) {
          break;
        }
        if (e == Sys.EMFILE || e == Sys.ENFILE || e == Sys.ENOMEM) {
          // Descriptor or memory pressure: shed one idle connection, retry once, then
          // back off. The accept loop must never stop silently - a server that stops
          // accepting looks exactly like a hang.
          if (evictIdle()) {
            cfd = Sys.accept(listenFd);
            if (cfd >= 0) {
              register(cfd);
              continue;
            }
            if (Sys.errno() == Sys.EAGAIN) {
              break;
            }
          }
          if (e == Sys.ENOMEM) {
            // Out of memory: the box cannot take another socket. Back off briefly rather
            // than spinning on accept.
            try {
              Thread.sleep(1);
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              break;
            }
          }
          break;
        }
        if (e == Sys.EINTR || e == Sys.ECONNABORTED) {
          continue;
        }
        break;
      }
      register(cfd);
    }
  }

  private void register(int cfd) {
    ensureCapacity(cfd);
    if (table[cfd] != null) {
      Sys.close(cfd);
      return;
    }
    // TCP_NODELAY on every accepted socket: Nagle plus delayed-ACK lands on the p99 gate.
    Sys.setNoDelay(cfd);
    // EPOLLRDHUP as well as EPOLLIN: a peer half-close has to be reported, because the
    // server never shuts its write side and so EPOLLHUP never fires for a clean FIN.
    if (Sys.epollCtl(epFd, Sys.EPOLL_CTL_ADD, cfd, Sys.EPOLLIN | Sys.EPOLLRDHUP) != 0) {
      Sys.close(cfd);
      return;
    }
    table[cfd] = new Conn(cfd);
    linkTail(cfd);
    activeCount++;
  }

  /**
   * Evicts the least-recently-used idle connection.
   *
   * @return true when an idle connection was shed with SO_LINGER=0
   */
  private boolean evictIdle() {
    int fd = lruHead;
    for (int i = 0; i < EVICT_SCAN && fd >= 0; i++) {
      Conn c = get(fd);
      int next = c == null ? -1 : c.next;
      if (c != null && !c.inFlight()) {
        // SO_LINGER=0 with a zero timeout: close sends RST, so no TIME_WAIT accumulates
        // against exactly the descriptor budget this eviction frees.
        Sys.lingerZero(fd);
        closeConn(fd);
        return true;
      }
      fd = next;
    }
    return false;
  }

  /**
   * Closes one connection and releases its descriptor.
   *
   * <p>Package-private so the handler can release a connection as soon as the peer
   * half-closes or a write fails, instead of waiting for the eviction watermark: the
   * descriptor budget is the score.
   *
   * @param fd the connection descriptor
   */
  void closeConn(int fd) {
    Conn c = get(fd);
    if (c == null) {
      return;
    }
    unlink(fd);
    table[fd] = null;
    activeCount--;
    // The kernel drops fd from its epoll set, so EPOLL_CTL_DEL is never needed.
    Sys.close(fd);
  }

  private Conn get(int fd) {
    if (fd < 0 || fd >= table.length) {
      return null;
    }
    return table[fd];
  }

  private void ensureCapacity(int fd) {
    if (fd < table.length) {
      return;
    }
    int size = table.length;
    while (size <= fd) {
      size <<= 1;
    }
    Conn[] grown = new Conn[size];
    System.arraycopy(table, 0, grown, 0, table.length);
    table = grown;
  }

  private void linkTail(int fd) {
    Conn c = table[fd];
    c.prev = lruTail;
    c.next = -1;
    if (lruTail >= 0) {
      table[lruTail].next = fd;
    } else {
      lruHead = fd;
    }
    lruTail = fd;
  }

  private void unlink(int fd) {
    Conn c = table[fd];
    if (c.prev >= 0) {
      table[c.prev].next = c.next;
    } else {
      lruHead = c.next;
    }
    if (c.next >= 0) {
      table[c.next].prev = c.prev;
    } else {
      lruTail = c.prev;
    }
    c.prev = -1;
    c.next = -1;
  }

  private void moveToTail(int fd) {
    if (fd == lruTail) {
      return;
    }
    unlink(fd);
    linkTail(fd);
  }

  private void shutdown() {
    for (int fd = 0; fd < table.length; fd++) {
      if (table[fd] != null) {
        closeConn(fd);
      }
    }
    lruHead = -1;
    lruTail = -1;
    if (listenFd >= 0) {
      Sys.close(listenFd);
      listenFd = -1;
    }
    if (epFd >= 0) {
      Sys.close(epFd);
      epFd = -1;
    }
  }
}
