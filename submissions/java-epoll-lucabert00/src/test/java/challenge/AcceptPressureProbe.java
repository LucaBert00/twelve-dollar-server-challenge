package challenge;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Child JVM: allocation probe for the {@code EMFILE} accept-pressure branch.
 *
 * <p>Runs under a small {@code RLIMIT_NOFILE}, fills every descriptor, then makes the
 * server's listening socket readable with no idle connection to evict. {@code accept4}
 * must fail with {@code EMFILE}, which is the branch {@link Server#acceptAll} shares
 * with {@code ENFILE} and {@code ENOMEM}. The probe invokes that method directly so the
 * measured window contains nothing but the failure path.
 *
 * <p>Every class, call site and method handle is warmed before the descriptor table is
 * filled: a first-time class load under {@code EMFILE} would fail for a reason that has
 * nothing to do with allocation. The measured window starts after a 50,000-pass warmup,
 * so JIT compilation is outside it. The counter is
 * {@code com.sun.management.ThreadMXBean.getThreadAllocatedBytes}; the assertion is an
 * exact zero, with no tolerance, because the ticket forbids weakening it.
 */
final class AcceptPressureProbe {
  private static final int WARMUP = 50_000;
  private static final int MEASURED = 50_000;

  private AcceptPressureProbe() {}

  public static void main(String[] args) throws Throwable {
    com.sun.management.ThreadMXBean threads =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    if (!threads.isThreadAllocatedMemorySupported()) {
      throw new IllegalStateException("thread allocation counters are not supported");
    }
    if (!threads.isThreadAllocatedMemoryEnabled()) {
      threads.setThreadAllocatedMemoryEnabled(true);
    }
    long tid = Thread.currentThread().threadId();

    InetAddress loopback = InetAddress.getLoopbackAddress();
    int port = freePort();
    Server server = new Server(loopback, 0, null);
    int listenFd = Sys.listen(loopback, port, Server.BACKLOG, false);
    int epFd = Sys.epollCreate();
    setIntField(server, "listenFd", listenFd);
    setIntField(server, "epFd", epFd);

    MethodHandle acceptAll =
        MethodHandles.privateLookupIn(Server.class, MethodHandles.lookup())
            .findVirtual(Server.class, "acceptAll", MethodType.methodType(void.class));

    // Warm the call site and the EAGAIN branch with no pending connection.
    for (int i = 0; i < 1_000; i++) {
      acceptAll.invokeExact(server);
    }
    // Warm the accept/register/close path, including Server$Conn and evictIdle's get().
    try (SocketChannel warm = SocketChannel.open()) {
      warm.connect(new InetSocketAddress(loopback, port));
      acceptAll.invokeExact(server);
      closeAccepted(server);
    }
    // Warm string concatenation and PrintStream: both are called after the table is full.
    System.out.println("warm");

    SocketChannel client = SocketChannel.open();
    List<FileInputStream> fillers = new ArrayList<>();
    try {
      try {
        while (true) {
          fillers.add(new FileInputStream("/dev/null"));
        }
      } catch (FileNotFoundException expected) {
        // EMFILE: the process descriptor table is full.
      }

      // connect() needs no new descriptor: the client fd already exists, and the
      // connection waits in the accept queue without spending a process fd.
      client.connect(new InetSocketAddress(loopback, port));

      // accept4 must fail with EMFILE while the pending connection stays queued.
      int accepted = Sys.accept(listenFd);
      if (accepted >= 0) {
        Sys.close(accepted);
        throw new AssertionError("a descriptor was free; the pressure was not EMFILE");
      }
      if (Sys.errno() != Sys.EMFILE) {
        throw new AssertionError("expected EMFILE, errno=" + Sys.errno());
      }
      System.out.println(
          "fd-limit=" + Server.fdLimit() + " fillers=" + fillers.size()
              + " errno=" + Sys.errno() + " (EMFILE) with no idle connection");

      for (int i = 0; i < WARMUP; i++) {
        acceptAll.invokeExact(server);
      }
      long before = threads.getThreadAllocatedBytes(tid);
      for (int i = 0; i < MEASURED; i++) {
        acceptAll.invokeExact(server);
      }
      long delta = threads.getThreadAllocatedBytes(tid) - before;
      System.out.println(
          "allocated bytes: " + delta + " over " + MEASURED
              + " accept-pressure passes (warmup " + WARMUP + ")");
      if (delta != 0) {
        throw new AssertionError("the accept-pressure path allocated " + delta + " bytes");
      }
    } finally {
      for (FileInputStream filler : fillers) {
        try {
          filler.close();
        } catch (Exception ignored) {
          // Cleanup only.
        }
      }
      client.close();
      Sys.close(listenFd);
      Sys.close(epFd);
    }
  }

  private static int freePort() throws Exception {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static void closeAccepted(Server server) throws Exception {
    var field = Server.class.getDeclaredField("table");
    field.setAccessible(true);
    Server.Conn[] table = (Server.Conn[]) field.get(server);
    for (int fd = 0; fd < table.length; fd++) {
      if (table[fd] != null) {
        server.closeConn(fd);
      }
    }
  }

  private static void setIntField(Server server, String name, int value) throws Exception {
    var field = Server.class.getDeclaredField(name);
    field.setAccessible(true);
    field.setInt(server, value);
  }
}
