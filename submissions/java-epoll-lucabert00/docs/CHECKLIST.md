# CHECKLIST - the rule 5 and rule 6 code read

Build: `submissions/java-epoll-lucabert00` on `main`, commit `bcaff56`.
Read against `src/challenge/` only. Re-sign per variant.

## Rule 5 - no cross-request caching

- [x] **No response caches.** Every response is rendered per request by
      `Json` (`Json.postObject`, `Json.feed`, `Json.singlePost`, `Json.like`,
      `Json.health`, `Json.error`). Nothing stores a rendered response.
- [x] **No query-result caches.** `Db.feed`, `Db.getPost`, `Db.checkHealth`
      step a statement per call and return the rows as they are. The prepared
      statement is a plan, not a result.
- [x] **No table copies.** `Db` holds two connection handles and seven
      prepared statements. No in-memory collection of rows, users, posts or
      likes exists anywhere in the server.
- [x] **No remembered verifications.** `Jwt.verify` checks the signature and
      `exp` on every authed request. The only reused state is the
      `ThreadLocal<Mac>` primitive and the immutable key, both of which the
      architecture names as allowed. No verdict is kept.

Allowed reuse, and it is plan or state, never data: the per-connection
prepared statements, the `ThreadLocal<Mac>`, the `Json` string builders, the
OS page cache and `mmap`.

## Rule 6 - durability

- [x] `journal_mode=WAL` on every connection, asserted at startup.
- [x] `synchronous=NORMAL` on every connection, asserted at startup. Never
      OFF, never MEMORY, never an in-memory database.
- [x] `foreign_keys=ON` on every connection, asserted at startup. This is the
      one three forks leave off.
- [x] `busy_timeout=0` on the writer and the reader, asserted at startup. A
      sleeping busy handler would turn the sibling worker's lock hold into a
      multi-second stall of this worker's whole loop.
- [x] `wal_autocheckpoint=0` on every connection, asserted at startup, and a
      dedicated thread runs a `PASSIVE` checkpoint once per second. Both
      halves are mandatory: automatic checkpointing off without a schedule
      grows the WAL unbounded.
- [x] **Commit before respond.** A write op is prepared, queued, and executed
      inside one `BEGIN IMMEDIATE` .. `COMMIT`. The answer for an op is
      written only after the commit that covers it has returned. Group commit
      is legal here because every response still waits for its own commit.
- [x] **A failed commit answers `500` for every op in the batch** and closes
      nothing. A closed connection with no answer becomes a client timeout,
      and `bench/load.js:53` turns a failed feed into a fast retry - load added
      exactly when the box is saturated.
- [x] **A busy write lock is waited for, not failed.** The op stays queued,
      the loop retries within a millisecond, and only an op that has waited
      past five seconds is failed with `500`.
- [x] **The write path never blocks the loop.** No sleep, no spin, no
      `busy_timeout` on the writer.
- [x] **No schema change.** Three tables and three indexes, exactly as
      `schema.sql` declares them. The application issues no DDL. Statements
      are prepared **without** `SQLITE_PREPARE_PERSISTENT`, because a
      persistent statement is written into the schema - which would make the
      reader take the write lock.
- [x] The pragma block is asserted **per role**, so a drift on the checkpoint
      connection fails fast at startup too.

## JWT strictness

- [x] HS256 only. `alg` must equal `HS256`; `none` and any other algorithm
      are rejected, and so are the `crit` and `b64` headers.
- [x] `exp` is enforced per request: numeric and in the future.
- [x] `sub` must be a positive-integer string and `username` must be a
      string, or the answer is `invalid token payload`.
- [x] The three 401 shapes are byte-exact: `missing bearer token`,
      `invalid or expired token`, `invalid token payload`.
- [x] **Auth before id.** `Http.prepareWrite` calls `requireAuth` before it
      validates the id or the body, so `POST /posts/abc/like` answers `401`
      without a token and `400` with one.

## The connection budget

- [x] The high watermark is the lower of the descriptor budget and the RAM
      budget, with a floor: a server that accepts nothing scores nothing.
- [x] Eviction only ever sheds connections with no request in flight.
- [x] Evicted connections close with `SO_LINGER=0`, so the close sends RST
      and no `TIME_WAIT` accumulates against the budget just freed.
- [x] `TCP_NODELAY` on every accepted socket; backlog 4096.
- [x] A peer half-close releases the descriptor immediately. `EPOLLHUP` never
      fires for a clean FIN, because the server never shuts its write side.

## Verdict

- [x] **PASS** - no prohibition violated. The standing posture (two
      `SO_REUSEPORT` workers, SerialGC, `-Xms128m -Xmx128m`, 16 MiB page cache
      per connection, the pragmas above) is explicitly **not** an optimum: the
      memory constants are estimates and the bench phase owns the tuning.

Method: minimal-diff senior-dev mode.
