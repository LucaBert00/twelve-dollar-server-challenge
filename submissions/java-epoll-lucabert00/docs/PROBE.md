# PROBE - the environment probe and what each number changes

One command on the box that will serve, one output file, and a short list of
decisions that follow from it. Nothing here is an estimate: every number the
capacity analysis uses is measured by `probe.sh`, or is marked **not measured**.

## Run it

```bash
# 1. On the 1 vCPU / 2 GB box, as a normal user, after install.sh and build.sh:
SQLITE_PATH=/tmp/feed.db JWT_SECRET=twelve-dollar-challenge HOST=0.0.0.0 PORT=80 \
  bash submissions/java-epoll-lucabert00/start.sh > /tmp/server.log 2>&1 &

# 2. Capture the environment, including the server's own startup line.
bash submissions/java-epoll-lucabert00/docs/probe.sh \
  --server-log /tmp/server.log \
  --out /tmp/probe-output.txt

# 3. Stop the server.
kill %1
```

`probe.sh --help` lists the arguments. The script reads only: it changes no
kernel tunable, no cgroup setting and no box setting (challenge rule 11).

## What each number means, and what it decides

| Number | What it is | What it decides |
|---|---|---|
| `ulimit -Sn` | the soft descriptor limit the shell sees | the starting point for "how many descriptors can this process open" |
| `ulimit -Hn` | the **hard** descriptor limit | the real ceiling: the kernel refuses new descriptors above it, and no process can raise it |
| `/proc/self/limits` "Max open files" | the limits **in force** for the process reading it | whether the JVM raised the soft limit to the hard limit at startup |
| `startup: fd-ceiling=... high=... low=...` | what the server itself believes | whether the server's eviction watermark is right |
| `nf_conntrack_max` | the connection-tracking table size | a second, kernel-level ceiling on concurrent connections. Read only. |
| `cgroup memory.max` (or v1 `memory.limit_in_bytes`) | the box's memory limit | the RAM side of the capacity calculation |
| `MemTotal` / `MemAvailable` | the host's memory | the same, when there is no cgroup limit |
| seed database bytes | the size of the database the server will map | the reclaimable page cache the box must hold |

## The descriptor number: three readings, three consequences

The server reads its ceiling from `UnixOperatingSystemMXBean.getMaxFileDescriptorCount()`.
OpenJDK's `os::init_2()` raises the soft `RLIMIT_NOFILE` to the hard limit at
startup:

```c
nbr_files.rlim_cur = nbr_files.rlim_max;
status = setrlimit(RLIMIT_NOFILE, &nbr_files);
```

So the value the bean reports and the hard limit are related, but the probe
captures both so the relationship is observed rather than assumed. Three
outcomes, and what each one changes:

1. **`fd-ceiling` equals `ulimit -Hn`.** The premise holds: the process is
   running under the hard limit. If the hard limit is 65,535, then one worker
   holds about 65,535 sockets and two workers about 131,070. The two-worker
   `SO_REUSEPORT` design is justified by the descriptor budget, and the
   submission README's "one process stops near 65,535 sockets" is confirmed.
2. **`fd-ceiling` is below `ulimit -Hn`.** The server's eviction watermark is
   **conservative**: it would start shedding connections before the kernel says
   it has to. That is safe, and it wastes capacity. The fix is to derive the
   watermark from the hard limit the process can actually reach, not from the
   number the bean reports.
3. **`ulimit -Hn` is above 65,535.** The premise is **false** and the
   submission README's sentence about stopping near 65,535 is wrong. The
   two-worker justification changes: the binding ceiling becomes the RAM
   budget, and the descriptor budget stops being the reason for the worker
   count. Ticket 47 (the worker-count ADR) has to revisit its input.

Reading 3 is the one that would change the architecture, and nobody has
measured it.

## The memory number and the arithmetic that consumes it

The score is connection capacity, so the two ceilings are the descriptor
budget and the RAM budget, and they are coupled.

```
fd_ceiling     = workers x fd_limit_per_process
socket_budget  = (box_bytes
                   - kernel_reserve_bytes
                   - workers x per_worker_fixed_bytes)
                 / socket_bytes_per_idle_socket
score_ceiling  = min(fd_ceiling, socket_budget)
```

| Term | What it covers | Where its value comes from |
|---|---|---|
| `box_bytes` | the box's memory limit | the probe's cgroup or `MemTotal` line |
| `kernel_reserve_bytes` | the kernel's own memory and the page cache headroom | a named constant, to be confirmed by the measured RSS at the scored VU count |
| `per_worker_fixed_bytes` | one worker's heap, its SQLite page cache and its JVM overhead | `Runtime.maxMemory()`, the `cache_size` pragma, and a named overhead constant |
| `socket_bytes_per_idle_socket` | the kernel cost of one idle TCP socket | a named constant, to be refined by the measured RSS delta per connection |
| `workers` | the number of worker processes | `start.sh` |

The database's page cache is **reclaimable**: the kernel drops clean pages
before it kills anything, so the 238 MiB seed database is not a hard reserve.
It still shows up in the cgroup's accounting until it is reclaimed, which is
why the measured RSS at the scored VU count is the check on the arithmetic.

## The conntrack number

`nf_conntrack_max` is a **kernel** parameter. Challenge rule 11 forbids
changing it, so the probe reads it and changes nothing. Its value decides
whether the conntrack table is a third ceiling: at 65,536 it would bite at
roughly the same point as a 65,535 descriptor ceiling, and at 262,144 it
would not. PR #83 holding 90,000 users suggests it is not the 65k wall, but
that is an inference from a competitor's run, not a measurement.

## Which numbers go where

| Number | Consumer |
|---|---|
| the descriptor ceiling | Ticket 42 (memory re-budget): the descriptor side of the high-water. Ticket 47: the worker count. |
| the memory limit and free memory | Ticket 42: the memory side of the high-water and the reserve constants. |
| the conntrack table size | Ticket 46 (the benchmark harness): a ceiling to check when a run goes red. |
| the seed size | Ticket 46: the reclaimable page cache term in the budget. |
| the startup line | Ticket 45 (the truth pass): the README's descriptor sentence. |
| the whole file | Ticket 46: the `BENCHMARK_BENCH.md` runbook, and Ticket 47: the ADR. |

## What is still not measured

State it plainly rather than filling the gap with an estimate:

- the kernel cost of one idle TCP socket on this box
- the JVM's own overhead beyond the heap
- the generator-side ceiling, which belongs to the benchmark harness and to the
  maintainer's generator setup, not to this box
- the reachable VU count, which needs the scored hold
