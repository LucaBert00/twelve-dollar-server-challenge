package challenge;

import java.net.InetAddress;

/**
 * Child JVM: allocation probe for the LRU eviction path, run with {@code -Xint}.
 *
 * <p>{@link Sys#lingerZero} is the only construct on the eviction path that can allocate
 * an object: it used to slice the scratch segment per call, and escape analysis removes
 * that allocation only under JIT. The interpreter is therefore the deterministic view of
 * what the source allocates, and the eviction path must be zero there too.
 *
 * <p>A {@code new Object()} control runs first. A zero result would be meaningless if the
 * counter could not see a known allocation.
 */
final class EvictionAllocProbe {
  private static final int WARMUP = 10_000;
  private static final int MEASURED = 10_000;

  private EvictionAllocProbe() {}

  public static void main(String[] args) throws Exception {
    com.sun.management.ThreadMXBean threads =
        (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
    if (!threads.isThreadAllocatedMemorySupported()) {
      throw new IllegalStateException("thread allocation counters are not supported");
    }
    if (!threads.isThreadAllocatedMemoryEnabled()) {
      threads.setThreadAllocatedMemoryEnabled(true);
    }
    long tid = Thread.currentThread().threadId();

    long beforeControl = threads.getThreadAllocatedBytes(tid);
    long sink = 0;
    for (int i = 0; i < 10_000; i++) {
      sink += new Object().hashCode();
    }
    long control = threads.getThreadAllocatedBytes(tid) - beforeControl;
    System.out.println(
        "control allocated bytes: " + control + " over 10000 new Object() (sink=" + sink + ")");
    if (control <= 0) {
      throw new AssertionError("the allocation counter did not see the control: " + control);
    }

    int fd = Sys.listen(InetAddress.getLoopbackAddress(), 0, 1, false);
    try {
      for (int i = 0; i < WARMUP; i++) {
        Sys.lingerZero(fd);
      }
      long before = threads.getThreadAllocatedBytes(tid);
      for (int i = 0; i < MEASURED; i++) {
        Sys.lingerZero(fd);
      }
      long delta = threads.getThreadAllocatedBytes(tid) - before;
      System.out.println(
          "allocated bytes: " + delta + " over " + MEASURED
              + " lingerZero calls (warmup " + WARMUP + ")");
      if (delta != 0) {
        throw new AssertionError("the eviction path allocated " + delta + " bytes");
      }
    } finally {
      Sys.close(fd);
    }
  }
}
