package challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Gate: the connection budget and the loop's wait timeout.
 *
 * <p>The score is connection capacity, and the two ceilings that bound it are coupled: the
 * descriptor budget and the RAM budget. The high watermark is the lower of the two, so these
 * tests pin the arithmetic that decides it.
 */
@Tag("gate")
@DisplayName("Server budget")
class ServerTest {

  @Test
  @DisplayName("the memory budget follows the named terms")
  void memoryBudgetArithmetic() {
    // Arrange: a 2 GiB box shared by two workers.
    long twoGib = 2L * 1024 * 1024 * 1024;
    long fixed = Server.KERNEL_RESERVE_BYTES + 2L * Server.perWorkerFixedBytes();

    // Act: the high watermark the memory budget alone allows.
    int memoryHigh = Server.memoryHighMark(twoGib, 2);

    // Assert: it is exactly the named terms, nothing hidden.
    long expected = (twoGib - fixed) / Server.BYTES_PER_IDLE_SOCKET;
    assertEquals((int) expected, memoryHigh, "the memory budget must follow the named terms");

    // Assert: a box with no room holds no sockets at all.
    assertEquals(0, Server.memoryHighMark(fixed, 2), "a box with no room holds no sockets");

    // Assert: an unreadable box limit leaves the descriptor budget in charge, rather
    // than guessing a RAM budget.
    assertEquals(
        Integer.MAX_VALUE, Server.memoryHighMark(0, 2), "an unknown box keeps the fd budget");
  }

  @Test
  @DisplayName("the effective high watermark is the lower budget, with a floor")
  void effectiveHighMarkIsTheLowerBudget() {
    // The descriptor budget always caps it.
    assertEquals(2000, Server.effectiveHighMark(2000, 100000), "the fd budget caps the watermark");
    // The memory budget can lower it.
    assertEquals(5000, Server.effectiveHighMark(10000, 5000), "the memory budget lowers it");
    // ... but never below the floor: a server that accepts nothing scores nothing.
    assertEquals(
        Server.MIN_HIGH_WATER,
        Server.effectiveHighMark(10000, 0),
        "the watermark must not fall to zero");
    assertEquals(
        Server.MIN_HIGH_WATER,
        Server.effectiveHighMark(10000, Integer.MAX_VALUE),
        "an unknown box keeps the fd budget");
  }

  @Test
  @DisplayName("the wait timeout is the earliest deadline")
  void waitTimeoutIsTheEarliestDeadline() {
    long now = 1_000_000_000L;
    assertEquals(1000, Server.epollTimeoutMillis(0, 0, now), "no deadline means the default");
    assertEquals(5, Server.epollTimeoutMillis(now + 5_000_000L, 0, now), "the pause deadline wins");
    assertEquals(1, Server.epollTimeoutMillis(0, now + 1_000_000L, now), "the retry deadline wins");
    assertEquals(
        1,
        Server.epollTimeoutMillis(now + 5_000_000L, now + 1_000_000L, now),
        "the earliest deadline wins");
    assertEquals(0, Server.epollTimeoutMillis(now - 1, 0, now), "a passed deadline waits no time");
  }
}
