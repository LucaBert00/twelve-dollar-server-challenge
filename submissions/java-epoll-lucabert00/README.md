# java-epoll-lucabert00

Direct-mode Java submission: a hand-written HTTP/1.1 parser over raw Linux
`epoll` reached through `java.lang.foreign`, reading one shared SQLite file
through FFM. No web framework, no JDBC driver, no proxy.

## Language, framework, driver, versions

- **Language:** Java, Eclipse Temurin 27 (build 35, `install.sh` pins the
  tarball URL plus SHA256 `1cf69a48…bccbc`).
- **Framework:** none. One single-threaded event loop per worker process over
  raw `epoll` (`Sys.java`), a hand-written HTTP/1.1 parser (`Http.java`),
  a compact-JSON writer (`Json.java`, `Out.java`), and an HS256 verifier on
  `javax.crypto.Mac` (`Jwt.java`).
- **Driver:** no JDBC. `Sqlite.java` reaches `libsqlite3` directly through
  FFM downcalls; `Db.java` owns the two handles (writer + reader) plus a
  third on-demand handle for the checkpoint thread.
- **SQLite 3.54.0**, built from the SHA256-pinned amalgamation
  (`68e913b0…46b5aa4d`) with `-DSQLITE_THREADSAFE=2 -DSQLITE_ENABLE_QPSG`,
  `-O2 -march=native`, in `build.sh`. Never the distro `.so`.
- **SQLite posture:** `journal_mode=WAL`, `synchronous=NORMAL`,
  `busy_timeout=0` (writer), `wal_autocheckpoint=0`,
  `journal_size_limit=64 MiB`, `mmap_size=1 GiB`, `cache_size=-64 MiB`,
  `temp_store=MEMORY`. Every connection asserts every value at startup and
  fails fast on drift, including `foreign_keys=ON`.

## Serve mode: direct, no Nginx

`start.sh` binds `0.0.0.0:80` directly with two `SO_REUSEPORT` workers and no
proxy. An Nginx hop would cap us at its `worker_connections` budget while
adding nothing the loop does not already do; the score is connection
capacity, so we keep the full fd budget for the workers.

## Optimisations and why

Top-down, in the order that matters:

1. **`SO_REUSEPORT`, 2 workers.** `RLIMIT_NOFILE` is per process, so one
   process stops near 65,535 sockets. Two processes bind the same port and
   the kernel spreads connections across them. Two is the cap: beyond that
   the processes sharing one SQLite file stop finding a quiet moment to
   reset the WAL.
2. **Per-connection memory near zero.** Connections cost one small state
   object (tens of bytes); request bytes live in worker-shared scratch, and
   the wiring layer drops its accumulation entry as soon as it is consumed.
   Idle capacity is bounded by descriptors, not heap.
3. **Checkpoint off the `COMMIT` path.** `wal_autocheckpoint=0` on every
   connection; a dedicated thread with its own connection runs passive
   checkpoints once per second. The commit hook is a pure observer. The
   request thread never pays the checkpoint fsyncs.
4. **`busy_timeout=0` + fail-fast `BEGIN IMMEDIATE`.** A sleeping busy
   handler would convert the sibling worker's lock hold into a multi-second
   stall of this worker's whole loop. On `SQLITE_BUSY` the write answers
   `500`; the client retry path, not a stalled loop, carries it.
5. **Direct `:80`.** Removes the proxy connection cap (see above).
6. **JVM posture for 1 vCPU:** SerialGC (no G1/Parallel contention),
   `-Xms == -Xmx` (no resizing during the ramp), CompactObjectHeaders,
   AlwaysPreTouch, no perf data, capped direct memory, and
   `--enable-native-access=ALL-UNNAMED` for the FFM calls.
   The heap figure (`-Xms256m -Xmx256m -Xmn64m` per worker) is a documented
   **placeholder, not a measured optimum** — two workers plus SQLite cache plus
   mmap has to stay well inside 2 GB, and the value has never been tuned. Idle
   capacity is bounded by descriptors, so the heap buys a young generation, not
   retention. Tuning it belongs to bench phase, not to correctness.
7. **Persistent prepared statements.** Prepared once per handle with
   `PREPARE_PERSISTENT`, reset plus rebind per use. Plan cache, never data.
8. **LRU fd eviction with `SO_LINGER=0`.** On budget pressure and on
   `EMFILE`/`ENFILE`, idle connections are shed oldest-first and close with
   RST, so no `TIME_WAIT` accumulates against the budget just freed. Only
   connections with no request in flight are evicted. `TCP_NODELAY` on every
   accepted socket; backlog 4096 for the ramp.
9. **FFM straight to SQLite.** No JDBC layer between the loop and the
   amalgamation built for this box.
10. **Commit-before-respond.** Every write response waits for its own
    `COMMIT`; a failed commit answers `500` for its op. Batching several
    requests into one commit is permitted and left as future work — the
    current wiring commits per request, which is the correct slow baseline.
11. **Range-scan feed with the reference fallback.** `/feed` is 46 % of
    traffic. The fast path reads the 20 newest ids from the covering index,
    then takes a primary-key range only when those ids are exactly contiguous
    (`count == 20 && max - min == 19`); any gap runs the immutable reference
    SQL. The range path sorts its 20 rows, so the plan — not an assumed id
    order — is recorded in `docs/EXPLAIN_QUERY_PLAN.md`. The gate compares
    both paths byte for byte; correctness, not a speed claim, is what it
    proves.

Deliberately not done: GraalVM native image (OOM risk on the 2 GB build
box), a third worker (worsens WAL contention), kernel tuning or CPU pinning
(challenge rule 11).

## Run it

```bash
sudo bash submissions/java-epoll-lucabert00/install.sh  # once, as root
bash submissions/java-epoll-lucabert00/build.sh          # as a normal user
SQLITE_PATH=/tmp/feed.db JWT_SECRET=twelve-dollar-challenge \
  HOST=0.0.0.0 PORT=80 bash submissions/java-epoll-lucabert00/start.sh
```

Config is four environment variables only: `SQLITE_PATH`, `JWT_SECRET`,
`HOST`, `PORT`. No `.env` file is read. `Main.java` fails fast on a missing
database, an empty secret, or any pragma drift, and `/health` answers within
a second of `start.sh`. `start.sh` runs in the foreground; if either worker
dies, `wait -n` plus the kill-trap stops the set instead of serving half.
`WORKERS` defaults to 2 and must stay there (see point 1 above).

## Layout

- `src/challenge/`: `Main` (wiring), `Server` (epoll loop), `Sys`
  (libc/epoll FFM), `Sqlite` (SQLite FFM), `Db` (handles + statements),
  `Checkpointer` (passive schedule), `Http` (parser + routes), `Json`/`Out`
  (bodies + framing), `Jwt` (HS256 verifier).
- `install.sh` / `build.sh` / `start.sh`: root install, user build,
  foreground serve. Builders are `javac` + `gcc` only, both capped.
- Build output (`build/`, `target/`, `bin/`) is git-ignored and never
  committed.
