# GITHUB_RESEARCH — systematic GitHub-wide optimization discovery (Areas A–F)

- Ticket: #6 `[Wayfinder-5/11] GITHUB_RESEARCH` (OPEN at writing time)
- Baseline: `research/analysis/SPRING_BOOT_CODE_REVIEW.md` @ `cec9c0a` (PR33 pinned to `7a9cb99`, 13/0/0 census, 0 SPEC conflicts, E-vt/E-stack/E-pool/E-sql/E-jvm/E-edge as search targets, not conclusions)
- Truth hierarchy: `SPEC.md` + `test/test.sh` > prior analysis docs > PR33/PR58 discussion > AI reports > external
- Output: this file only (`research/findings/GITHUB_RESEARCH.md`, first file under `research/findings/`). No app code.
- Skills invoked via skill tool, in order: `research`, `deep-research`, `agent-browser`, `browser-automation`, `prototype`, `documentation-writer`, plus `verification-before-completion` before claim/commit/push/close. `prototype` used for throwaway reasoning only — zero repo code created. Browser skills satisfied by direct `gh` API + doc fetch per base prompt §3 (direct inspection preferred over screenshots; no page render needed browser automation materially).
- Forbidden-word filter (§4.4): grep-checked empty before commit (see §10).
- Every significant claim carries one verdict: VERIFIED / PARTIALLY VERIFIED / INCORRECT / UNSUPPORTED / OUTDATED / EXPERIMENTAL HYPOTHESIS with primary evidence (repo URL + commit SHA + file, doc URL, or measured output). Needs-experiment items carry method only, never a conclusion.

## 0. Method (what was actually searched, not asserted)

- Code+repo search: `gh api pulls/33/files`, `pulls/58/files`, `pulls/{33,58}/commits`, per-commit `commits/<sha>` messages + file lists, all via read-only `gh` API. Reference branch never fetched into, never merged (local `reference/spring-boot-pr33` untouched @ `7a9cb99`; live-head inspected via API only).
- Issues/PRs/discussions: `issues/33/comments` (13 bodies, 0 review comments, 0 formal reviews re-verified), `issues/58` + `pulls/58` (OPEN, 3 commits, 8 conversation comments, 13 files), upstream fork list reasoning.
- History: PR33 4 baseline commits (`fa5a0a2`, `83d3551`, `2879838`, `7a9cb99`) + 9 drift commits (`baf8550` → `845accd`); PR58 3 commits (`a216947`, `7d9861e`, `3cc06f6`).
- Dorking/web: `sqlite.org` pragma/WAL/JSON1 docs, HikariCP pool-sizing wiki, `xerial/sqlite-jdbc` usage, Spring virtual-threads docs, Tomcat connector docs, k6 open-vs-closed + ramping-vus docs, Jackson thread-safety docs, `javax.crypto.Mac` thread-safety sources, SerialGC single-CPU sources, JFR overhead docs. Source inspected beyond READMEs (PR58 `Db.java`/`build.sh` full diff, PR33 commit file lists, pragma/JSON1 reference pages).
- Licenses checked via `gh api repos/<owner>/<repo> --jq .license.spdx_id` + `LICENSE` path check (see §9).

## 1. Area A — forks / submissions / benchmark experiments

### A1. PR33 live-head re-baseline (the #4 baseline is OUTDATED for live head, current for pin) — PARTIALLY VERIFIED

- Repo URL: `https://github.com/arjaythedev/twelve-dollar-server-challenge`
- Commit/version: PR `pull/33`, live head `845accd87117b4353dc706231d165a3fb9522830` (13 commits total), pinned baseline `7a9cb99` (4 commits). Verified this session: `gh api pulls/33 --jq head/commits` → `845accd` / 13 / OPEN; `pulls/33/commits --jq .[].sha` → 13 SHAs (`fa5a0a2 … 845accd`).
- Files: `pulls/33/files` → 34 files under `submissions/java-spring-boot-nb-99/` (list in §0; includes `Application.java`, `PostRepository.java`, `application.properties`, `ApiTest.java`, `benchmark.py`, `scripts/direct.sh|docker.sh|k8s.sh`, `compose.yaml`, `Dockerfile`).
- Technique: none (this finding is the re-baseline itself — what changed since the pin).
- Mechanism: 9 drift commits after `7a9cb99`:
  - `baf8550` direct serving + per-connection prepared reuse + JFR profiling flag;
  - `82b2b20` readme cleanup; `6684ebf` k6 TIME_WAIT fix;
  - `4b80bc1` WebFlux-on-Netty variant; `9013ef7`/`2df5a26` serialized writer + SQLite-built JSON + generated-key lookup off;
  - `fb9120f` merge of WebFlux variant; `eb50654`/`845accd` readme rewrites.
  - Commit messages verified via `gh api commits/<sha> --jq .commit.message` (4 sampled + 2 more, all match #4 §9 summary).
- Evidence: API outputs above (primary). Comment census re-verified: `pulls/33/comments` → 0, `issues/33/comments` → 13 (was 7 at #4 freeze + 6 new).
- Applicability: every #4 statement scoped "Nginx mode / MVC / pool-4 / no prepared reuse" now applies to `7a9cb99` only — follow-ups must test against `845accd+`, not the pin.
- Disadvantages: live head uninspected at code level here (file list only, no line review); numbers in merge/commit messages unmeasured (see A3).
- Rule compat: neutral (re-baseline, no behavior claim). No rule 5/6 statement made about live head.
- License: `arjaythedev/twelve-dollar-server-challenge` → `MIT` (`gh api repos/... --jq .license.spdx_id`), `LICENSE` present. Studying + re-implementing ideas in own code allowed; copying files verbatim requires preserving MIT notice.
- Test/adopt/reject: ADOPT as the new comparison point for #7/#9. Needs-experiment: re-run E-vt/E-stack/E-pool/E-sql/E-jvm/E-edge deltas against `845accd`, not `7a9cb99`.
- Verdict: **PARTIALLY VERIFIED** (head SHA, commit count, file list, messages API-verified; code semantics of drift NOT line-verified here).

### A2. PR33 drift techniques (direct 60k + prepared reuse + TIME_WAIT fix) — PARTIALLY VERIFIED mechanism, UNSUPPORTED numbers

- Repo URL + SHA: same PR33; `baf85509e801c096454eb60a8c12069ca16cb806` + `6684ebfeb4e24753a0ff322edfe67a9aa3c056bc`.
- Files: `baf8550` touches 21 files incl. `src/main/java/challenge/Application.java`, `PostRepository.java`, `application.properties`, `start.sh`, `scripts/direct.sh`, `Dockerfile`, `compose.yaml` (verified via `commits/baf8550 --jq files`); `6684ebf` message names k6 pod port drain.
- Technique: (i) drop Nginx, serve `0.0.0.0:80` directly sized to 60k keep-alive; (ii) own 4 SQLite connections, prepare each statement once per connection; (iii) `PROFILE=true` records JFR per run; (iv) wait for k6 ports to drain between runs (TIME_WAIT 60 s exhausted ports above ~32k users on a persistent k6 pod).
- Mechanism: (i) removes one hop + 64-conn Nginx multiplex on 1 vCPU, trades ~64 server-side conns for up to ~15k–60k direct keep-alive conns (SPEC §Runtime contract: Nginx keeps 64 idle, direct holds ~15k; `LimitNOFILE=65535`); (ii) avoids per-request re-prepare cost; (iv) avoids `connect() failed` false negatives from ephemeral-port exhaustion, not a server speedup.
- Evidence: commit messages (primary, quoted in #4 §9 + re-fetched this session verbatim). Code lines NOT reviewed here. Numbers: none posted for (i)/(ii); (iv) threshold "~32k users" is author-stated in message, no raw table → **UNSUPPORTED** as a benchmark figure.
- Applicability: high — E-edge (direct-vs-Nginx) and E-sql (prepared reuse) now have concrete upstream variants to replicate under controlled single-variable runs.
- Disadvantages: direct mode multiplies idle-conn memory + descriptor pressure (SPEC warns 15k conns); per-conn prepared reuse pins memory per conn; TIME_WAIT wait slows iteration cadence.
- Rule compat: allowed on its face (no cache, no skipped work, WAL+NORMAL retained per message; direct serving is an explicit SPEC option `0.0.0.0:80`). Final ruling needs line review of `Application.java`/`PostRepository.java` at `845accd` (#9 ownership).
- License: MIT (same repo). Test/adopt/reject: TEST — replicate each as one variable (direct toggle; prepared-reuse toggle; drain-wait in harness), do not adopt on message faith.
- Verdict: mechanism **PARTIALLY VERIFIED** (message + file list verified, lines not); every performance magnitude **UNSUPPORTED**.

### A3. PR33 WebFlux-on-Netty + serialized writer + SQLite-built JSON + group-commit decline — PARTIALLY VERIFIED mechanism, UNSUPPORTED figures

- Repo URL + SHAs: `4b80bc116d6d541e4aa89f00ec64833418d06c6b` (WebFlux variant), `9013ef76de8b22268b08d84ceec36eb7dc250978` + `2df5a26534e8ba22af37142f477239f953b57b2f` (serialized writer + SQLite JSON), `fb9120fe95c89fe61811dc8ff3ac2ecb9c83a499` (merge message with figures), comments 8–13 in `issues/33/comments`.
- Files: `pulls/33/files` at live head still lists `PostRepository.java`, `Application.java`, `ApiTest.java`, `benchmark.py`, `k8s/k6.yaml` (variant merged over same paths; per-file variant diff not line-reviewed here).
- Technique: Netty event loops for HTTP + one dedicated blocking thread per SQLite connection (blocking JDBC never on event loop); single writer connection queues writers in Java instead of SQLite busy handler; 3 read-only conns; feed/post JSON built by SQLite (`json_object`/`json_group_array` family), app sends bytes unchanged; `sqlite-jdbc` generated-key lookup off; group commit explicitly declined by author (WAL+NORMAL + durability-queue rationale, comments 12–13).
- Mechanism: event loop stays non-blocking (idle keep-alive holds no buffers per message); write serialization converts `busy_timeout` sleeps into an explicit Java queue (observable queue time, single-writer SQLite reality); JSON-in-SQL removes per-column Java string/escape work; declining group commit preserves commit-before-respond (Rule 6) at the cost of one fsync-ish WAL append per write under NORMAL.
- Evidence: commit messages re-fetched verbatim this session (see §0); comment 11 "webflux actually gained ~20% … Going with webflux … Lost nginx" + merge message "38,000 users … about 20% above 31,750" — first quantitative claims in thread, **no method, no raw data, no review** → **UNSUPPORTED** here (same ruling as #4 §9). Comment 8 interim "not that big of a difference … so far" likewise **UNSUPPORTED**. Group-commit decline rationale consistent with Rule 6 by reading of comments, code uninspected.
- Applicability: highest-value #7 candidates — E-stack (MVC vs WebFlux vs minimal, only after profile shows thread-blocking not SQL as bottleneck), E-pool (queue-in-Java vs busy-handler), E-sql (SQLite JSON shape).
- Disadvantages: WebFlux adds operator/concurrency-model complexity + Netty tuning surface on 1 vCPU; single writer caps write throughput (fine at 2% writes in `bench/load.js`, risky if write mix changes); SQLite JSON pushes CPU into the single-writer DB thread.
- Rule compat: allowed on its face (per-request JWT re-verified per message, no response/query cache claimed, commit-before-201 retained per message). Needs line review before ADOPT (unknown-path 404, auth-before-id, key order, `created_at` passthrough must be re-checked on the WebFlux tree).
- License: MIT. Test/adopt/reject: TEST under E-stack/E-pool/E-sql with identical SQL/pragmas/pool; do not quote 20%/38k anywhere as a result.
- Verdict: technique existence **PARTIALLY VERIFIED**; all magnitude claims **UNSUPPORTED**; group-commit decline rationale **PARTIALLY VERIFIED** (comment-verified, code-uninspected).

### A4. PR58 `java-nio-stohirov` — minimal epoll-FFM + FFM-SQLite reference design (strongest external idea source) — PARTIALLY VERIFIED

- Repo URL: `https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/58`
- Commit/version: OPEN, head `3cc06f612bd37ab68dea711c76c25e272770ff11`, 3 commits (`a216947` Add submission, `7d9861e` Speed up feed and reset evicted connections, `3cc06f6` Replace java.nio with epoll through FFM). Verified: `pulls/58 --jq head/commits/files` → `3cc06f6` / 3 / 13; `pulls/58/commits --jq .[].sha` → 3 SHAs; `issues/58/comments` → 8.
- Files (13, verified via `pulls/58/files --jq .[].filename`): `src/challenge/Server.java`, `Db.java`, `Sqlite.java`, `Sys.java`, `Json.java`, `Jwt.java`, `Out.java`, `Main.java`, `build.sh`, `install.sh`, `start.sh`, `README.md`, `.gitignore`.
- Technique: zero-dependency Java 25 (Temurin `jdk-25.0.4.1+1`, sha256-pinned per `install.sh`) — hand-written HTTP/1.1 on Linux `epoll` via FFM (`accept4`/`epoll_wait`/`epoll_ctl`/`read`/`write`/`close` downcalls, no `java.nio` channels/Selector); direct FFM bindings to SQLite `3.54.0` amalgamation built by `build.sh` (sha256 `68e913b0…be46b5aa4d`, flags `THREADSAFE=0 DEFAULT_MEMSTATUS=0 DEFAULT_WAL_SYNCHRONOUS=1 DQS=0 … ENABLE_QPSG`, `-O2 -march=native`); one thread + one SQLite connection, everything inline; shared native read/response buffers (own buffer only while partial/unsent; ~10 MB live heap at 256k idle conns per README); direct `0.0.0.0:80`; LRU idle eviction near `LimitNOFILE` with `SO_LINGER=0` RST reset (no FIN/TIME_WAIT); adaptive 2 ms park when a wake-up brings <32 events; backlog 4096; SQLite-built JSON per-row `json_object` (feed joined in Java with `{"posts":[`…`,`,…`]}` to avoid `json_group_array … ORDER BY` temp b-tree); `SQLITE_PREPARE_PERSISTENT` + `Linker.Option.critical` short calls; group commit (`BEGIN IMMEDIATE … COMMIT` per wake-up batch, responses after COMMIT); `wal_autocheckpoint=0` + ~1/s PASSIVE checkpoint between batches; pragmas `locking_mode=EXCLUSIVE journal_mode=WAL synchronous=NORMAL cache_size=-65536 temp_store=MEMORY journal_size_limit=64MB mmap_size=0`; single reused `javax.crypto.Mac`; `-XX:+UseSerialGC` 256 MB fixed heap + `AlwaysPreTouch` + compact headers.
- Mechanism (why each matters on 1 vCPU/2 GB/15k conns): removes per-conn NIO objects/locks/`fcntl`+`getsockname`×2/deferred deregistration (README-measured 64%→56% CPU, ceiling 330k→390k simulated users — laptop-local, see Evidence); single-thread inline removes context switches for microsecond index lookups; group commit amortizes WAL appends while preserving commit-before-respond; scheduled checkpoints avoid checkpoint-inside-commit stalls; `QPSG` stops re-prepare of `p.id=?1` + correlated count on every bind; per-row JSON avoids double-copy + sort b-tree (~13.9 µs→~9.7 µs feed per README — laptop-local).
- Evidence: full `pull/58/files` page fetched + `Db.java` (220 lines, `PRAGMAS`/`POST_JSON`/`FEED_SQL`/`INSERT…RETURNING json_object`/`LIKE…EXISTS…ON CONFLICT DO NOTHING`/`BEGIN IMMEDIATE`/`walCheckpointPassive`) + `build.sh` (39 lines, SHA + flags) + `README.md` (105 lines, table + pragmas + JVM flags) read beyond README as required. Local measurement table (128k–384k users, 11–176 ms p95/p99, 31–78% CPU, fail at 416k/~40k req/s core saturation) explicitly "measured on Docker on an Apple Silicon laptop, not the droplet … pinned `--cpus 1 --memory 2g`" → **UNSUPPORTED** as challenge-box figures (different CPU/arch/noise), **PARTIALLY VERIFIED** as code-present techniques.
- Applicability: highest — this is the concrete Option-A/minimal extreme for #9 (JDK+epoll-FFM vs JDK HttpServer vs Netty vs Spring). Every sub-technique maps to one E-item (E-stack/E-pool/E-sql/E-jvm/E-edge).
- Disadvantages: Linux-only (`epoll` FFM, no portability); hand-rolled HTTP parser must re-prove every SPEC edge (auth-before-id, `invalid post id` matrix, malformed JSON, 404 shapes, key order, `created_at` passthrough, 500-char/trim rules); `locking_mode=EXCLUSIVE` + `THREADSAFE=0` + single conn = no concurrent-write scaling story; `SO_LINGER=0` RST eviction past ~64k users means nearly every request reconnects (client-retry burden); group commit adds write batching latency; `-march=native` build not portable across droplet steppings without rebuild.
- Rule compat: plausibly allowed (JWT "verified in full on every request (rule 5)" per README; responses after COMMIT (rule 6); WAL+NORMAL preserved; no response/query cache; `mmap_size=0` avoids mmap durability questions) — **needs line review** of `Server.java`/`Jwt.java`/`Json.java` auth/validation/error paths before any ADOPT claim. `like()` single-statement `INSERT…SELECT…WHERE EXISTS…ON CONFLICT DO NOTHING` + `changes()==1 → 201 / exists → 200 / else 404` is an elegant atomic like shape worth a dedicated #7 row.
- License: "MIT, under the repo's license" per `README.md` + repo MIT verified (§9). Build pins Temurin JDK + SQLite amalgamation by SHA — re-use the technique, rebuild from pinned sources, keep MIT notices.
- Test/adopt/reject: TEST — do not copy-paste; replicate one variable at a time (epoll-vs-NIO; per-row-JSON vs `json_group_array`; group-commit on/off with commit-before-respond asserted; eviction on/off; SerialGC/heap sweep). Reject laptop numbers as targets.
- Verdict: design presence **PARTIALLY VERIFIED** (page+diff verified, full `Server.java`/`Jwt.java` semantics not line-audited here); all laptop magnitude figures **UNSUPPORTED** for the droplet; rule-compat **PARTIALLY VERIFIED** (README claims + `Db.java` shapes consistent, edge paths unreviewed) → explicit #7/#9 inputs.

## 2. Area B — servers / event loops / virtual threads

### B1. `spring.threads.virtual.enabled=true` execution-model switch — VERIFIED mechanism, UNSUPPORTED gain

- Repo URL / version: `https://github.com/spring-projects/spring-boot` (docs, not pinned SHA — behavior flag since Boot 3.2) + `https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html` + `https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html`.
- Files: `application.properties` key `spring.threads.virtual.enabled`; affects Tomcat/Jetty request threads + `applicationTaskExecutor` (`SimpleAsyncTaskExecutor` on vthreads) + scheduler + messaging listeners (Spring docs, verified by fetch).
- Technique: one virtual thread per request instead of pooled platform threads; custom `Executor` beans untouched.
- Mechanism: extra request threads do NOT create extra SQLite writers — with Hikari pool-4 vs Tomcat ~200 threads the gate is pool checkout (established #4 §3.7, re-asserted here as the reason comment-1 "surely" is unproven). Vthreads help only if threads (not pooled conns / SQLite single-writer) are the bottleneck.
- Evidence: Spring reference page (primary doc) + Oracle virtual-threads guide (primary) + #4 §5 re-verification (flag absent at pin, `git grep spring.threads` 0 hits). No posted profile showing thread-blocking as bottleneck → gain **UNSUPPORTED**.
- Applicability: E-vt single-variable run (identical SQL/pragmas/pool, 1 vCPU; measure threads, pool-wait, p99).
- Disadvantages: unbounded thread creation masks pool saturation as thread growth; pinning risks on synchronized/JNI paths; scheduler pool-size props ignored (surprise).
- Rule compat: allowed (no behavior change on its face). License: Spring Boot Apache-2.0, OpenJDK GPLv2+CE — using the flag + docs creates no license burden.
- Test/adopt/reject: TEST (E-vt), do not adopt on faith.
- Verdict: flag mechanism **VERIFIED**; any throughput/latency gain **UNSUPPORTED**; relevance conditioned on E-pool profile **EXPERIMENTAL HYPOTHESIS**.

### B2. Netty event-loop + dedicated blocking-DB threads (the PR33-WebFlux shape, generalized) — PARTIALLY VERIFIED pattern, UNSUPPORTED magnitude

- Repo URLs: `https://github.com/netty/netty` (Apache-2.0, `gh api` license verified) + `https://projectreactor.io/docs/netty/release/reference/` (Reactor Netty guide) + PR33 `4b80bc1` message ("Blocking SQLite calls run on one dedicated thread per connection, never on the event loops").
- Files: Reactor Netty HTTP-server + connection-provider docs (idle/timeout/pool sections fetched); PR33 `PostRepository.java`/`Application.java` at live head (paths verified, lines not reviewed here).
- Technique: event loops own sockets only; every blocking JDBC call hops to a bounded executor (here: one thread per SQLite conn); error bodies keep SPEC JSON shapes.
- Mechanism: keeps p99 from event-loop stalls (no JDBC park on I/O threads); idle keep-alive holds minimal per-conn state (PR33 message: "Netty holds no buffers for idle keep-alive connections" — consistent with shared-buffer reasoning in PR58, but Netty-internal slab/allocator behavior NOT line-verified here).
- Evidence: PR33 message (primary for intent) + Reactor Netty docs (primary for capability). No profile/diff/numbers posted → gain **UNSUPPORTED**; buffer claim **PARTIALLY VERIFIED** (message-verified, allocator-source-unverified).
- Applicability: E-stack gate — run only after a profile shows thread-blocking (not SQL) as bottleneck; otherwise adds operator/model complexity for no win.
- Disadvantages: reactive debugging/profiling harder on 1 vCPU; extra hop executor→DB adds queueing knobs; mis-sized blocking pool re-creates the Tomcat/pool-4 parking shape.
- Rule compat: allowed on its face (no caching/skipping implied). License: Netty + Reactor Netty Apache-2.0 — dependency-allowed, note in submission manifest.
- Test/adopt/reject: TEST under E-stack with bounded-DB-executor sweep (1 thread/conn vs small pool), JFR allocation + pool-wait counters.
- Verdict: pattern **PARTIALLY VERIFIED**; every latency/throughput delta **UNSUPPORTED**.

### B3. Single-thread-inline minimal server (PR58 shape) vs pooled frameworks on 1 vCPU — EXPERIMENTAL HYPOTHESIS

- Repo URL + SHA: PR58 head `3cc06f6` (`Server.java` + `Db.java` + `Out.java`), README "One thread, one SQLite connection, everything inline. On one vCPU, more threads only add context switches."
- Files: `submissions/java-nio-stohirov/src/challenge/Server.java`, `Db.java` (feed/post/insert/like inline, `begin()`/`commit()`/`checkpointIfDirty()` in same thread).
- Technique: no pool, no executor, no framework — the event loop calls SQLite directly (microsecond index lookups).
- Mechanism: at 2% writes / 98% microsecond reads (`bench/load.js` mix: feed→think→get→think→15% like→2% post→idle), syscall + queue overhead can dominate; removing them trades multicore scalability (irrelevant with `cpus: 1.0`) for minimal per-request path.
- Evidence: PR58 README rationale + `Db.java` single-conn structure (primary, read). No droplet measurement → hypothesis only. Counter-evidence to track: if a single slow write (checkpoint, WAL growth) stalls the loop, p99 explodes — needs E-stack/E-pool falsification run.
- Applicability: #9 Option-A extreme; cheapest to profile first (one flame graph settles it).
- Disadvantages: Linux-only, hand-parser SPEC risk (§A4), single-writer ceiling, no isolation between slow/fast paths.
- Rule compat: allowed on its face (see A4). License: MIT.
- Test/adopt/reject: TEST first (profile-gated), do not assume minimal always wins (microbenchmark confusion warning: laptop 40k req/s saturation ≠ droplet score).
- Verdict: **EXPERIMENTAL HYPOTHESIS** with sharp falsifier (loop-stall p99 under write burst).

## 3. Area C — SQLite / pooling / statements / PRAGMAs / plans

### C1. WAL + `synchronous=NORMAL` durability boundary — VERIFIED

- Doc URLs (primary): `https://www.sqlite.org/wal.html`, `https://www.sqlite.org/pragma.html#pragma_synchronous`, `https://www.sqlite.org/compile.html#default_synchronous` (+ forum/hipp clarification on app-crash vs power-loss durability).
- Files: N/A (engine semantics); challenge wiring at pin verified #4 (`Application.java:27-30` pragmas per pooled conn + `ApiTest` pragma assertions on all 4 conns).
- Technique: `PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;` (Rule 6 floor: "WAL + NORMAL-or-stronger").
- Mechanism: WAL appends are atomic/consistent across crashes at NORMAL; durability across *application* crash holds, across OS/power loss may roll back the last transaction(s) — NORMAL syncs WAL on checkpoint/header-reuse, FULL syncs per-commit WAL. Challenge box measures latency/errors, not power loss; NORMAL buys fewer fsyncs per write.
- Evidence: sqlite.org pragma table ("WAL mode is always consistent with synchronous=NORMAL, but WAL mode does lose durability … might roll back following power loss"), defaults (`SQLITE_DEFAULT_SYNCHRONOUS=2/FULL`, `SQLITE_DEFAULT_WAL_SYNCHRONOUS` inherits), Hipp forum note (app-crash durable, OS-crash maybe-not). All fetched this session.
- Applicability: keep NORMAL as the floor; test FULL only as a cost probe (E-sql), never below NORMAL.
- Disadvantages: NORMAL risks losing the last moment of work on power loss (accepted by challenge rules; group-commit batching widens that window slightly — author decline rationale in A3 is the conservative read).
- Rule compat: **allowed and required floor** (`README.md:42-49` rules 5/6 restated #4 §7). Anything weaker (OFF / DELETE mode) is prohibited.
- License: SQLite public domain (blessing, not a license burden); docs no reuse restriction for findings.
- Test/adopt/reject: ADOPT NORMAL as baseline; TEST FULL-vs-NORMAL delta once (E-sql).
- Verdict: durability boundary **VERIFIED**.

### C2. `busy_timeout` + pool sizing + serialize-writers-in-Java vs sleep-in-`busy_handler` — VERIFIED shape, EXPERIMENTAL sizing

- Repo URLs: `https://github.com/brettwooldridge/HikariCP` (Apache-2.0 verified) + `https://github.com/xerial/sqlite-jdbc` (Apache-2.0 verified) + `https://www.sqlite.org/pragma.html` (`busy_timeout`, `cache_size`, `journal_size_limit`, `mmap_size` sections fetched).
- Commits/files: pin `Application.java:27-30` (`WAL + synchronous=NORMAL + FK ON + busy_timeout=5000`, Hikari `maxPoolSize=4 minimumIdle=1 autoCommit=true connectionTimeout=10000` — #4 §3.1 re-verified); PR33 drift `9013ef7` (one writer conn, writers queue in Java); PR58 `Db.java` (`BEGIN IMMEDIATE … COMMIT` per batch, `wal_autocheckpoint=0` + `checkpointIfDirty()`).
- Technique: choose where concurrent writes wait — (a) SQLite busy handler (sleep/retry up to timeout), (b) explicit Java writer queue (single writer conn), (c) Hikari pool queue (threads park on checkout).
- Mechanism: SQLite has a single writer; more conns/threads do not parallelize writes. (a) hides queueing in native sleeps (invisible in Java metrics); (b) makes queue time observable + orderable (pipelined-behind-write answered after it, in order — PR58 `Db.java`); (c) with pool-4 vs ~200 Tomcat threads most request threads park on checkout under write pressure (#4 §3.7 — VERIFIED shape, unmeasured effect).
- Evidence: Hikari `About-Pool-Sizing` wiki (pool = workers, not magic; right-size queue to pool) + sqlite.org `busy_timeout` pragma page (alternative to `sqlite3_busy_timeout()`, max ~24.9 days) + pin code shapes + drift messages. Optimal N (4 vs split 1W+3R vs other) unmeasured → **EXPERIMENTAL HYPOTHESIS** (E-pool: Hikari 4 vs candidates with reader/writer split; busy-timeout counters, queue time, write p99).
- Applicability: direct E-pool runs; record `busy_timeout` expirations + pool-wait histograms, not just p95/p99.
- Disadvantages: single-writer queue adds p99 under write bursts; too-small pool starves readers; too-large pool multiplies prepared-statement + page-cache memory per conn.
- Rule compat: allowed (queue placement is not caching; commit-before-respond must be re-asserted per variant via `ApiTest`-style read-back).
- License: HikariCP + sqlite-jdbc Apache-2.0 (verified) — dependency-allowed.
- Test/adopt/reject: TEST (E-pool matrix), do not size by formula faith (`(cores*2)+spindles` is for networked DBs, not single-file SQLite on 1 vCPU — wiki formula explicitly not transferable without measurement).
- Verdict: queue-placement trade **VERIFIED** as a shape; every sizing number **EXPERIMENTAL HYPOTHESIS**.

### C3. Prepared-statement reuse per connection (+ `PERSISTENT` + QPSG plan stability) — PARTIALLY VERIFIED

- Repo URLs: `https://github.com/xerial/sqlite-jdbc` (Apache-2.0) + PR33 `baf8550` + PR58 `Db.java` + `https://www.sqlite.org/c3ref/prepare.html` / QPSG note (`SQLITE_ENABLE_QPSG`).
- Files: pin `PostRepository.java` via `JdbcTemplate` (re-prepares per call, driver cache unconfigured — #4 §3.5 observation); drift `baf8550` ("prepares each statement once per connection"); PR58 `Db.java` (`prepare()` with `PREPARE_PERSISTENT`, statements as fields `feed/post/insertPost/like/exists/ping/begin/commit`, `bindInt64/bindText/step/reset` cycle read in full).
- Technique: prepare once per SQLite connection, `reset()` + rebind per request; compile with `ENABLE_QPSG` so `p.id=?1` + correlated `like_count` keeps a stable plan across binds.
- Mechanism: removes parse/plan per-request CPU + per-call allocations; QPSG stops the "re-prepare on every bind because the bound value might change the plan" pathology PR58 README names for `GET /posts/:id`.
- Evidence: PR58 `Db.java` prepare/bind/step/reset cycle (primary, read); `xerial/sqlite-jdbc` USAGE/issues on prepared handling (capability-verified, perf-unmeasured); QPSG doc note (capability-verified). Droplet delta unmeasured → gain is hypothesis.
- Applicability: E-sql single-variable toggle (reuse on/off, same SQL/pragmas/pool) + `EXPLAIN QUERY PLAN` on fresh + grown data (feed `ORDER BY created_at DESC, id DESC LIMIT 20` on `posts_created_at_id_idx` per `schema.sql:24`).
- Disadvantages: per-conn statement memory; schema-change invalidation handling; `JdbcTemplate` path needs explicit per-conn config (not free).
- Rule compat: allowed (no skipped work; same SQL semantics). License: Apache-2.0 (driver) + MIT (PR diffs) — clean.
- Test/adopt/reject: TEST (E-sql), adopt only with plan + p99 evidence.
- Verdict: reuse shape **PARTIALLY VERIFIED** (code-present, droplet-unmeasured); QPSG pathology **PARTIALLY VERIFIED** (author-observed, independently unmeasured).

### C4. SQLite-built JSON: per-row `json_object` + Java-joined feed vs `json_group_array` aggregate — PARTIALLY VERIFIED mechanism, EXPERIMENTAL gain

- Doc URL (primary): `https://sqlite.org/json1.html` (`json_object()` §4.13, `json_group_array()` aggregate §4.23 — fetched).
- Commits/files: PR33 `9013ef7`/`2df5a26` ("SQLite builds the feed and post JSON, app sends unchanged"); PR58 `Db.java` (`POST_JSON=json_object('id',p.id,'body',p.body,'created_at',p.created_at,'author',u.username,'like_count',(SELECT count(*)…))`, `FEED_SQL` per-row, `INSERT…RETURNING json_object('post',json_object(…))`, `LIKE…` + `Out.head/copyNative` native-to-native copy, `FEED_OPEN/CLOSE` + commas in Java).
- Technique: one TEXT column per post object out of SQLite, copied native-to-native into the response buffer; feed = N rows in index order joined in Java, NOT one `json_group_array(… ORDER BY …)` aggregate.
- Mechanism: avoids per-column JNI/JDBC calls + Java strings + Java escaping; avoids aggregate's temporary sort b-tree + two extra JSON copies (PR58 README: feed ~13.9 µs→~9.7 µs, ~14% server CPU/request — laptop-local, hypothesis for droplet).
- Evidence: `json1.html` capability (primary) + `Db.java` SQL shapes (primary, read). Droplet delta + SPEC-byte equality (key order `id,body,created_at,author,like_count` per `SPEC.md:26-27`, `created_at` sent as stored per `SPEC.md:30-31`, compact JSON + `application/json` per `SPEC.md:25`) unmeasured here → gain hypothesis; byte-equality must be re-proved per variant via `test/test.sh` golden reads + `ApiTest` expectations.
- Applicability: E-sql high priority (single statement change, large alloc delta if SQL ruled in).
- Disadvantages: pushes formatting CPU into SQLite thread (single-writer contention); `json_group_array` ORDER BY semantics vs Java join order must be proved equal (`ORDER BY created_at DESC, id DESC`); escaping differences (`<>&/` optional per SPEC, ASCII load payloads) must be byte-checked.
- Rule compat: allowed on its face (stored `created_at` passthrough, no cross-request cache, per-request computation). License: SQLite public domain; idea reuse clean.
- Test/adopt/reject: TEST (E-sql: per-row vs aggregate, `EXPLAIN QUERY PLAN`, golden-byte diff, alloc profile).
- Verdict: capability + code shape **PARTIALLY VERIFIED**; every µs/% figure **UNSUPPORTED** for the droplet until E-sql measures it.

### C5. Page-cache / mmap / journal-cap / checkpoint-schedule / locking-mode knobs — EXPERIMENTAL HYPOTHESIS (measure, do not presume)

- Doc URLs: `https://www.sqlite.org/pragma.html` (`cache_size` negative-KiB form + session-only reversion, `journal_size_limit` bytes + truncate-on-commit/reset, `mmap_size` limit + `SQLITE_MAX_MMAP_SIZE` hard cap — all fetched).
- Files/values in the wild: PR58 `Db.java` `PRAGMAS` block (`locking_mode=EXCLUSIVE journal_mode=WAL synchronous=NORMAL cache_size=-65536 temp_store=MEMORY mmap_size=0 journal_size_limit=64MB wal_autocheckpoint=0`); pin JVM `-Xmx512m` leaves ~1.5 GiB for page cache/OS/Nginx (#4 §3.8 sizing note, unmeasured).
- Technique: size `cache_size` to working set (negative = KiB, session-only — must set per pooled conn like the pin does for WAL/busy); cap WAL via `journal_size_limit`; choose mmap on/off; move checkpoints out of the commit path (autocheckpoint 0 + ~1/s PASSIVE between batches); `locking_mode=EXCLUSIVE` skips shared-memory WAL-index + per-txn file locking.
- Mechanism: page cache avoids read syscalls for the 20-row feed working set; mmap trades syscalls for page faults (can win on reads, can stall on faults under memory pressure — hence PR58 sets `mmap_size=0` explicitly); journal cap bounds disk after bursts; scheduled PASSIVE checkpoints avoid commit-path stalls (FULL-or-above needed for durability-sync per forum note — PASSIVE alone does not durability-sync).
- Evidence: pragma semantics **VERIFIED** via sqlite.org; every sizing/mmap/schedule gain claim for this workload **EXPERIMENTAL HYPOTHESIS** (forum anecdote "large cache_size showed no noticeable gain on SSD/cache-sized workload" is a caution against presumption, not evidence for this box). Microbenchmark confusion warning applies: SSD-vs-memory anecdotes ≠ droplet 1-vCPU/2 GB result.
- Applicability: E-sql/E-jvm tail (after pool/statements/JSON settled); one knob per run, `EXPLAIN QUERY PLAN` + fault/c_checkpoint counters.
- Disadvantages: oversized cache steals from JVM heap/OS/Nginx inside 2 GiB; EXCLUSIVE locking + `THREADSAFE=0` forfeits multi-conn write concurrency; autocheckpoint-0 without a schedule grows WAL unbounded; mmap under cgroup limit can fault-stall p99.
- Rule compat: allowed if WAL+NORMAL-or-stronger preserved and no table copies/caches introduced (re-assert per variant). License: engine semantics, no burden.
- Test/adopt/reject: TEST in stated order (cache → journal-cap → checkpoint-schedule → locking-mode → mmap last, each with rollback criterion).
- Verdict: semantics **VERIFIED**, all local optima **EXPERIMENTAL HYPOTHESIS**.

## 4. Area D — single-core JVM / GC / heap / warmup / allocation

### D1. SerialGC + small fixed heap + pre-touch on 1 vCPU / 2 GiB — PARTIALLY VERIFIED mechanism, EXPERIMENTAL optimum

- Doc/source URLs: OpenJDK/Inside.java SerialGC note ("ideal for single processor / limited resources"), Datadog GC survey (Serial ideal <2 GB low-CPU containers), Red Hat GC chooser — all fetched; PR58 `README.md` JVM block.
- Files/values: PR58 `-XX:+UseSerialGC` fixed 256 MB heap `+AlwaysPreTouch` `+UseCompactObjectHeaders`, capped metaspace/code-cache/direct, ~1 MB/s steady-state alloc → young GC every couple minutes (README-measured laptop-local); pin `-Xmx512m` no GC flags (#4 §3.8).
- Technique: single-threaded stop-the-world collector, no GC threads competing for the one core; fixed heap + pre-touch avoids commit/fault stalls; compact headers trim per-object bytes (matters at 15k–256k conns).
- Mechanism: on `cpus: 1.0`, parallel/G1/ZGC background threads steal the only core from request/SQLite work; Serial trades shorter average throughput for no concurrent-GC CPU tax, with pauses bounded by small young gen at ~1 MB/s alloc.
- Evidence: collector properties **VERIFIED** via vendor docs; heap/flag values **PARTIALLY VERIFIED** as present in PR58 (read) but droplet-unmeasured; "best GC" claim **EXPERIMENTAL HYPOTHESIS** (needs E-jvm: heap/GC sizing inside 2 GiB with SQLite+Nginx resident, alloc profiles for JSON/auth after SQL ruled out).
- Applicability: E-jvm matrix (Serial vs G1 vs Parallel vs ZGC × heap 256/384/512m, same load, p99 + GC logs + native-mem tracking).
- Disadvantages: Serial pauses are stop-the-world (p99 risk if alloc spikes); fixed heap OOMs instead of growing (needs cap + metrics); pre-touch slows startup; compact headers is version-gated (JDK 25 path verified in PR58 `install.sh` Temurin pin).
- Rule compat: allowed (runtime tuning, no behavior change). License: OpenJDK GPLv2+CE / Temurin notices — using flags creates no burden; redistributing a JDK build would need notice care (not planned).
- Test/adopt/reject: TEST (E-jvm), never size by analogy.
- Verdict: mechanism **PARTIALLY VERIFIED**; optimum **EXPERIMENTAL HYPOTHESIS**.

### D2. Per-request crypto/JSON allocation shapes (Mac init, regex compile, Jackson) — VERIFIED shape, EXPERIMENTAL cost

- Source URLs: Oracle `javax.crypto.Mac` docs + StackOverflow/mac-thread-safety analysis ("`doFinal` not thread-safe, mutates internal state") + auth0 `java-jwt` thread-safety issue (new instance per verify as the safe pattern) + FasterXML `ObjectMapper` Javadoc ("fully thread-safe provided ALL configuration occurs before ANY read/write") — all fetched.
- Files: pin `Authentication.java` (per-request `Mac.getInstance/init` + `String.matches("[0-9]+")` regex compiled per call + Jackson 3 `tools.jackson.databind` with `com.fasterxml` annotation package — #4 §§3.4/3.6); PR58 `Jwt.java` + `Json.java` (small strict parser, one reused keyed `Mac`, base64url straight from digest, constant-time compare — README + file list verified, `Jwt.java` lines not audited here).
- Technique: reuse one `Mac` per thread (`ThreadLocal`) or per-request instance pool; precompile `Pattern`; reuse one configured `ObjectMapper` singleton; or skip Java JSON for reads via C4 SQLite-built bytes.
- Mechanism: `Mac` init per request repeats key-schedule work; per-call `String.matches` recompiles the regex automaton; new `ObjectMapper` per request repeats config + buffer allocs. None are thread-safe to share naively (`Mac` mutates state) — `ThreadLocal<Mac>` or new-instance-per-verify are the documented-safe shapes.
- Evidence: allocation *shapes* **VERIFIED** by pin reading (#4); thread-safety constraints **VERIFIED** via primary docs; cost magnitude unmeasured → **EXPERIMENTAL HYPOTHESIS** (E-jvm tail: alloc profile for JSON/auth only after SQL ruled out — measure-first order from `OPTIMIZATIONS.md` policy).
- Applicability: cheapest diffs in the tree (one-line `Pattern`, singleton mapper, `ThreadLocal<Mac>`) — but only after E-sql/E-pool profiles justify them.
- Disadvantages: `ThreadLocal` leaks on pooled-thread reuse without `remove()`; singleton mapper misconfigured after first use is a data race (config-before-use rule); hand-parser replacement re-opens SPEC edge risk.
- Rule compat: allowed (per-request verification preserved; constant-time compare retained; HS256-only + `exp` + `sub`/`username` shapes unchanged). License: JDK + Jackson Apache-2.0 — clean.
- Test/adopt/reject: TEST under E-jvm with allocation flame graph; reject if profile shows SQL/pool dominance.
- Verdict: shapes + constraints **VERIFIED**; cost/profit **EXPERIMENTAL HYPOTHESIS**.

### D3. JIT warmup + JFR profiling method (no invented targets) — VERIFIED method

- Source URLs: `https://dev.java/learn/jvm/jfr/` + Red Hat JFR guide ("typically <1% overhead") + BellSoft/Inside.java JFR notes — fetched; challenge files `benchmark.py` (warm-up → doubling probes → 250-user refinement → 5-min confirmation, p95<500 p99<1000 <1% errors — #4 §3.8 described-not-run) + `bench/load.js` ramp/hold + PR33 `baf8550` `PROFILE=true` JFR-per-run message.
- Files: `submissions/java-spring-boot-nb-99/benchmark.py`, `scripts/lib.sh` (k6 v2 pinned/checksummed), live-head `k8s/k6.yaml`.
- Technique: ramp (60 s) + hold (5 m) as JIT warmup; JFR continuous/low-overhead profiling per run; single-variable experiments.
- Mechanism: JIT needs warm iterations before top-tier compilation; cold-start numbers confuse interpreter/C1 with steady-state C2; JFR at <1% overhead can stay on without distorting p99 (unlike heavyweight agents).
- Evidence: JFR overhead property **VERIFIED** via vendor docs; warmup structure **VERIFIED** via `bench/load.js` stages + `benchmark.py` description (both read, neither executed here — no figures invented).
- Applicability: every E-item runs under this harness; correctness-first (`test/test.sh` 42 checks) before any bench per base prompt truth hierarchy.
- Disadvantages: JFR files add disk/I/O on 2 GB box (cap + rotate); warmup lengthens iteration cadence.
- Rule compat: allowed (observability, no behavior change). License: JFR/OpenJDK + k6 (AGPLv3 for k6 binary — harness use, not linked — no distribution burden for findings).
- Test/adopt/reject: ADOPT method for #8 (frontier, no encroachment — method noted here, strategy owned by #8).
- Verdict: method **VERIFIED**; overhead bound **PARTIALLY VERIFIED** (vendor-stated, box-unmeasured).

## 5. Area E — HTTP / keep-alive / Nginx / parsing / tail latency / JSON / JWT / event loop

### E1. Keep-alive + conn-limit envelope (the numbers that bound every server choice) — VERIFIED envelope, EXPERIMENTAL tradeoff

- Spec URLs/files (primary, read in full): `SPEC.md:8-21` (Nginx keeps 64 idle, ≥65 s idle hold; direct ~15k conns, `LimitNOFILE=65535`, `CAP_NET_BIND_SERVICE`), `bench/nginx.conf` site, `compose.yaml` (`cpus: 1.0 mem_limit: 2g`), pin `application.properties` (`server.tomcat.keep-alive-timeout=70s` satisfies ≥65 s — #4 §3.2 VERIFIED), Tomcat docs (`keepAliveTimeout`/`maxConnections` NIO default 10000 / `maxKeepAliveRequests` default 100 — fetched), PR58 (`listen backlog 4096`, LRU eviction + `SO_LINGER=0` RST, adaptive 2 ms park <32 events/wake-up).
- Technique: size idle handling to the envelope, not to microbenchmarks: behind-Nginx = 64 server conns; direct = 15k sustained (+60k sizing probe in PR33 drift, LRU+RST past descriptor limit in PR58).
- Mechanism: Nginx multiplexes 15k client conns into 64 server conns at the cost of one hop + 1-core Nginx CPU; direct removes the hop but forces the app to hold 15k+ idle conns cheaply (shared buffers, no per-idle buffers, fast eviction). Tomcat `maxConnections` counting + `maxKeepAliveRequests` pipelining caps must be raised/aligned for direct; backlog 4096 absorbs accept bursts.
- Evidence: SPEC numbers + pin 70 s + Tomcat defaults **VERIFIED** via primary reads; PR33-60k / PR58-RST behaviors **PARTIALLY VERIFIED** (message/diff-verified, droplet-unmeasured); "which wins" **EXPERIMENTAL HYPOTHESIS** (E-edge: direct-vs-Nginx under ~15k keep-alive conns).
- Applicability: E-edge is the last HTTP variable (after SQL/pool settled); monitor accept queue, `EMFILE`, eviction rate, p99 vs CPU.
- Disadvantages: direct mis-sized = `EMFILE`/OOM at 15k idle; Nginx mis-sized = `worker_connections 16384` cap ~16k users (PR58 note) + extra latency hop.
- Rule compat: both modes are explicit SPEC options (`127.0.0.1:3000` behind Nginx vs `0.0.0.0:80` direct) — either allowed if keep-alive + byte-exactness hold. License: Nginx 2-clause BSD, Tomcat Apache-2.0 — config use, no burden.
- Test/adopt/reject: TEST (E-edge), do not choose by default.
- Verdict: envelope **VERIFIED**; winner **EXPERIMENTAL HYPOTHESIS**.

### E2. JSON/JWT/event-loop triage (reuse mapper, verify-in-full, never block the loop) — VERIFIED constraints, EXPERIMENTAL ranking

- Spec/files: `SPEC.md:25-32` (compact JSON, `application/json`, exact key order, `created_at` as-stored millis+`Z`, new rows `strftime('%Y-%m-%dT%H:%M:%fZ','now')`), `SPEC.md:64-66` + `test/test.sh:74-83` (three 401 shapes: `missing bearer token` vs `invalid or expired token` vs `invalid token payload`; auth-before-id `test/test.sh:83`), pin `Responses.java` (`@JsonPropertyOrder` SPEC-exact — #4 §3.3), `ErrorHandler.java` (status+`{"error":msg}` vs `SPEC.md:59-70` — #4 §3.6), `Authentication.java` (3-segment HS256-only, no `crit`/`b64`, constant-time `MessageDigest.isEqual`, numeric `exp>now`, `sub` positive-int string + `username` string — #4 §3.4).
- Technique: one configured `ObjectMapper` (thread-safe after config — FasterXML Javadoc VERIFIED) or C4 SQLite-built bytes; JWT verified in full every request (signature + `exp`, HS256-only); blocking JDBC never on event loops (B2).
- Mechanism: JSON/JWT are per-request CPU after SQL; sharing a mapper removes config/alloc churn; constant-time compare avoids timing side-channel; auth-first ordering is a SPEC-mandated fast-reject (401 before 400/404) that also saves DB work on unauthed traffic.
- Evidence: SPEC/test shapes **VERIFIED** by full reads; mapper thread-safety **VERIFIED** via Javadoc; per-request-cost ranking unmeasured → **EXPERIMENTAL HYPOTHESIS** (profile order: SQL → pool → JSON/auth, per `OPTIMIZATIONS.md` measure-first policy).
- Applicability: correctness gate first (golden-byte + auth-matrix green), then alloc-profile triage.
- Disadvantages: singleton-mapper-after-config race if violated; hand-parser/JWT rewrites re-open the 401-shape matrix (three shapes must stay byte-exact).
- Rule compat: strict — Rule 5 (no remembered verifications, no response/query caches) forbids memoizing JWT/auth/JSON across requests; per-thread reuse of *stateless* primitives (`Mac` via ThreadLocal, mapper singleton) is allowed, cross-request *result* caching is prohibited.
- License: Jackson Apache-2.0 — clean.
- Test/adopt/reject: ADOPT constraints as gates; TEST ranking via profile.
- Verdict: constraints **VERIFIED**; cost ranking **EXPERIMENTAL HYPOTHESIS**.

## 6. Area F — k6 / repro / monitoring / profiling / stats

### F1. `bench/load.js` closed-model semantics + pass thresholds — VERIFIED

- File (primary, read in full): `bench/load.js` (70 lines) + k6 docs `https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/open-vs-closed/` + `https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/ramping-vus/` (both fetched).
- Technique: `ramping-vus` executor, stages ramp 60 s → hold 5 m → ramp-down 30 s (`gracefulRampDown 10s`); one VU = one browsing human (feed → think 3–7 s → get-one-from-feed → think 3–8 s → 15% like → 2% post → idle 5–15 s → repeat); thresholds `http_req_duration p95<500 p99<1000`, `http_req_failed rate<0.01`, `summaryTrendStats avg,med,p95,p99,max`; `SharedArray` tokens; `__ITER==0` 0–5 s spread.
- Mechanism: `ramping-vus` is a **closed model** (next iteration starts only when the last finishes — k6 docs VERIFIED) — throughput is bounded by VU count × iteration latency; slow responses *reduce* arrival rate (coordinated-omission cousin: do not read closed-model p99 as open-arrival p99; compare only same-harness runs). Think/idle sleeps dominate the 40k-req/s-scale arithmetic (PR58 laptop 27k–40k req/s at 256k–384k users is consistent with ~0.1 req/s/user, not with open-loop flooding).
- Evidence: file + docs **VERIFIED** by reading; no run executed here (no figures invented).
- Applicability: every E-item uses identical `VUS/DURATION/RAMP_UP/BASE_URL/TOKENS` + thresholds; report `avg/med/p95/p99/max` + error rate together, never a single mean.
- Disadvantages: closed model hides open-arrival queue explosion (needs arrival-rate probe in #8 if owner wants it); `Math.random()` think times are unseeded (run-to-run variance — pin `__VU`/`__ITER` reasoning in #8).
- Rule compat: neutral (harness, not submission). License: k6 AGPLv3 (binary use) + repo MIT — findings carry no license burden.
- Test/adopt/reject: ADOPT semantics for #8; flag coordinated-omission caveat as a #8 requirement.
- Verdict: executor semantics + thresholds **VERIFIED**.

### F2. TIME_WAIT / ephemeral-port ceiling + drain-wait fix — PARTIALLY VERIFIED mechanism

- Commits: PR33 `6684ebfeb4e24753a0ff322edfe67a9aa3c056bc` ("The Kubernetes k6 pod persists between runs. Its closed connections hold their local ports in TIME_WAIT for 60 seconds, so consecutive runs above about 32,000 users exhausted the ports and failed to connect." — message re-fetched verbatim).
- Files: `k8s/k6.yaml`, `scripts/lib.sh` (k6 v2 pinned/checksummed per #4 §3.8), `benchmark.py` (doubling probes + refinement).
- Technique: wait for k6 ports to drain between runs (or fresh k6 pod / wider ephemeral range / `SO_LINGER` discipline on the load side only — server-side RST eviction in PR58 is a separate SERVER choice with client-retry cost, not a harness fix).
- Mechanism: persistent-pod + closed conns × 60 s TIME_WAIT × rising VU count = ephemeral exhaustion → `connect() failed` misread as server failure. Drain-wait converts false negatives into slower cadence.
- Evidence: message **VERIFIED** as author-stated; `~32k` threshold + 60 s TIME_WAIT consistent with Linux defaults but box-unmeasured here → scope **PARTIALLY VERIFIED** (mechanism sound, threshold box-specific).
- Applicability: mandatory repro hygiene for #8 (record pod reuse, ephemeral range, drain wait, retry policy).
- Disadvantages: slows iteration; masks real accept-queue issues if over-waited (record both).
- Rule compat: neutral. License: MIT.
- Test/adopt/reject: ADOPT drain discipline in #8 harness; TEST threshold on the droplet (do not quote 32k as a universal constant).
- Verdict: mechanism **PARTIALLY VERIFIED**; threshold **UNSUPPORTED** as a general figure.

### F3. Correctness-first + pragma-asserted + JFR-observed pipeline — VERIFIED existence, method-only

- Files (primary, read): `test/test.sh` (126 lines: golden reads, health/404/id matrix, auth matrix, create/like/read-back, feed-20/top-of-feed/entry-match, `passed/failed` gate), pin `ApiTest.java` (11 methods incl. pragma assertions on all 4 pool conns `journal_mode=wal synchronous=1/NORMAL foreign_keys=1 busy_timeout=5000`, 8-way concurrent-like idempotency, 503-on-closed-pool, 16 KiB bound — #4 §3.8), `benchmark.py` (warm-up → doubling → 250-user refinement → 5-min confirmation), PR33 `baf8550` `PROFILE=true` JFR flag, `seed/make-seed.sh` + `seed/tokens.json` (20k pre-signed tokens per SPEC auth §).
- Technique: `test/test.sh` 100% → k6 limit-search → JFR/alloc profile → single-variable E-runs → `ApiTest`-style pragma + idempotency re-gates.
- Mechanism: golden bytes catch JSON/key-order/`created_at`/status regressions that latency numbers hide; pragma assertions catch per-conn config drift (session-only `cache_size`-class knobs!); concurrent-like test catches check-then-insert races (PR58 atomic `INSERT…WHERE EXISTS…ON CONFLICT` vs pin two-round-trip like — #4 §3.3 benign-only-because-no-delete, still worth a combine probe in E-sql with 404/200/201 preserved).
- Evidence: suite/manifest existence **VERIFIED** by reading; no suite or load executed here (explicit no-blind-reproduction per base rules — zero invented pass/fail or latency figures in this file).
- Applicability: #8 owns the runnable form; this finding is the evidence checklist #8 must encode.
- Disadvantages: full gate per variant is slow (accept it — byte-exactness is the score gate).
- Rule compat: gates enforce Rules 4/5/6 (100% suite, no caches, durable commit). License: MIT + JFR/k6 notes as in D3/F1.
- Test/adopt/reject: ADOPT as the #8 skeleton.
- Verdict: pipeline existence **VERIFIED**; effectiveness unmeasured here by design (method, not result).

## 7. License table (checked, not assumed)

| Source | License (verified) | Evidence | Reuse posture |
|---|---|---|---|
| `arjaythedev/twelve-dollar-server-challenge` (PR33 + PR58 host) | MIT | `gh api repos/arjaythedev/twelve-dollar-server-challenge --jq .license.spdx_id` → `MIT`, `contents/LICENSE --jq .path` → `LICENSE` | Study + re-implement ideas freely; verbatim copies keep MIT notice |
| `brettwooldridge/HikariCP` | Apache-2.0 | `gh api repos/brettwooldridge/HikariCP --jq .license.spdx_id` | Dependency-allowed, notice preserved |
| `xerial/sqlite-jdbc` | Apache-2.0 | `gh api repos/xerial/sqlite-jdbc --jq .license.spdx_id` | Dependency-allowed (pin 3.53.4.0 per pin `pom.xml` or rebuild path) |
| `netty/netty` (+ Reactor Netty) | Apache-2.0 | `gh api repos/netty/netty --jq .license.spdx_id` | Dependency-allowed, manifest-noted |
| `FasterXML/jackson-databind` | Apache-2.0 | `gh api repos/FasterXML/jackson-databind --jq .license.spdx_id` | Singleton-reuse allowed |
| SQLite engine + `sqlite.org` docs | Public domain (blessing) | `sqlite.org` canonical pages | Semantics/SQL reuse clean, no notice burden |
| OpenJDK / Temurin 25 | GPLv2+CE (+ Temurin notices) | `install.sh` Temurin pin (PR58), OpenJDK docs | Flag use clean; redistributing a JDK build needs notice care (not planned) |
| k6 (harness binary) | AGPLv3 | k6 docs + `scripts/lib.sh` pin note | Harness use (not linked) — no distribution burden for findings |
| Nginx (config reference) | 2-clause BSD | `bench/nginx.conf` + docs | Config reference clean |
| No unsafe/obsolete/undocumented tricks adopted | — | This file adopts no trick, only TEST-gated candidates | `locking_mode=EXCLUSIVE`, `THREADSAFE=0`, `mmap`, RST eviction, group commit all carry explicit rollback criteria in §8 |

## 8. Needs-experiment handoff to #7 (E-series from #4, refined by this session — method only)

- E-vt (B1): platform vs `spring.threads.virtual.enabled=true`, identical SQL/pragmas/pool, 1 vCPU — threads, pool-wait, p99. Falsifier: no delta if pool/SQLite is the gate.
- E-stack (B2/B3/A3/A4): MVC vs WebFlux/bounded-DB-executor vs single-thread-inline/epoll-minimal — only after profile shows thread-blocking (not SQL) as bottleneck. Include PR58 atomic-like shape as a sub-row. Falsifier: loop-stall p99 under write burst kills minimal.
- E-pool (C2): Hikari 4 vs 1W+3R split vs single-writer-Java-queue — busy-timeout counters, queue time, write p99. Falsifier: reader starvation or queue p99 regression.
- E-sql (C3/C4/C5): prepared-reuse toggle + `EXPLAIN QUERY PLAN` fresh/grown; per-row-`json_object` vs `json_group_array`; like combine with 404/200/201 preserved; cache/journal/checkpoint/locking/mmap one-knob-at-a-time with golden-byte gates.
- E-jvm (D1/D2): GC×heap matrix (Serial/G1/Parallel/ZGC × 256/384/512m) with SQLite+Nginx resident + NMT; JSON/auth alloc profile only after SQL ruled out (`ThreadLocal<Mac>`, precompiled `Pattern`, mapper singleton).
- E-edge (E1): direct-vs-Nginx under ~15k keep-alive (accept queue, `EMFILE`, eviction rate, p99 vs CPU). Falsifier: descriptor/idle-memory blowup or Nginx-cap hit.
- Harness for all (F1/F2/F3/D3): closed-model `ramping-vus` + thresholds + think mix pinned; drain-wait + pod/ephemeral logging; correctness-first + pragma assertions + JFR per run; no invented targets.

## 9. Classification summary

| Verdict | Count | Items |
|---|---|---|
| VERIFIED | 7 | C1 durability boundary; F1 k6 closed-model + thresholds; F3 pipeline existence; D3 JFR/warmup method; E1 envelope numbers (SPEC+Tomcat defaults+70 s); E2 SPEC/test constraints + mapper thread-safety + auth-before-id; D2 shapes + thread-safety constraints |
| PARTIALLY VERIFIED | 8 | A1 re-baseline (head/files/messages, not lines); A2 drift mechanisms; A3 WebFlux/writer/JSON intent + group-decline rationale; A4 PR58 design presence + README/diff reads; B1 flag mechanism; B2 Netty pattern; C3 reuse+QPSG shapes; C4 JSON1 capability + SQL shapes; D1 GC mechanism + flag presence; F2 TIME_WAIT mechanism (counted once — mechanism half) |
| UNSUPPORTED | 6 | PR33 ~20%/38k-vs-31.75k + interim "not that big" + "~32k port" threshold as general figures; PR58 laptop table (128k–416k, µs/CPU deltas, 40k req/s ceiling) as droplet figures; every B/C/D gain magnitude without an E-run |
| INCORRECT | 0 | — (no finding asserted false; "surely faster"‐class certainties from PR33 comments 5/7 stay UNSUPPORTED, not promoted to INCORRECT, per #4) |
| OUTDATED (for live head; current for pin) | 4 | Carried from #4 §10: Nginx-mode, MVC-only, pool-4-no-reuse, no-prepared-reuse characterizations (now triply confirmed as pin-only by A1) |
| EXPERIMENTAL HYPOTHESIS | 9 | B3 minimal-wins; C2 sizing; C3/C4/C5 optima; D1 optimum; D2 cost ranking; E1 winner; E2 cost ranking; E-stack/E-edge gated choices (§8) |

Fabricated benchmarks in this file: **none** (no suite or load executed; every number is either SPEC/threshold, vendor-doc bound, or explicitly UNSUPPORTED author/laptop figure).

## 10. Verification appendix (commands run this session)

- `git status` clean; `git branch --show-current` → `research/java-analysis`; `git log --graph --all --decorate -15` linear (`research/java-analysis = origin/research/java-analysis = origin/main = main = cec9c0a`; `backup/main-baseline-2026-10-13 = 026bdb6` unmoved; `reference/spring-boot-pr33 = 7a9cb99` unmoved); `git remote -v` origin=fork, upstream=original, no upstream push.
- `gh issue list --milestone Task-2-Research` → #6 OPEN, #2–#5 CLOSED, #7–#12 OPEN (untouched); `gh issue view 6 --json` → OPEN + body len 1945; `gh issue view 1 --json` Epic body re-read; claiming comment posted before work (`issues/6#issuecomment-6171150763`).
- `SPEC.md` (84 lines), `bench/load.js` (70 lines), `test/test.sh` (126 lines), `SPRING_BOOT_CODE_REVIEW.md` (370 lines), `WAYFINDER_MAP_PROMPT.md` (§4) read in full.
- `gh api pulls/33 --jq head/commits` → `845accd`/13/OPEN; `pulls/33/commits --jq .[].sha` → 13 SHAs; `pulls/33/files --jq .[].filename` → 34 files; `commits/<baf8550|4b80bc1|9013ef7|2df5a26|fb9120f|6684ebf|845accd> --jq .commit.message` re-fetched; `pulls/33/comments --jq length` → 0; `issues/33/comments --jq length` → 13.
- `gh api pulls/58 --jq head/commits/files` → `3cc06f6`/3/13; `pulls/58/commits --jq .[].sha` → 3 SHAs; `pulls/58/files --jq .[].filename` → 13 files; `issues/58/comments --jq length` → 8; `pull/58/files` page fetched (README 105 + `Db.java` 220 + `build.sh` 39 + `install.sh` 22 lines read beyond README).
- Licenses: `repos/arjaythedev/twelve-dollar-server-challenge` → MIT; `brettwooldridge/HikariCP` → Apache-2.0; `xerial/sqlite-jdbc` → Apache-2.0; `netty/netty` → Apache-2.0; `FasterXML/jackson-databind` → Apache-2.0 (all via `gh api --jq .license.spdx_id`).
- Docs fetched: `sqlite.org/wal.html`, `pragma.html` (synchronous/busy_timeout/cache_size/journal_size_limit/mmap_size), `json1.html`, `compile.html` notes; Spring task-execution + Oracle virtual-threads; Tomcat 8.5 connector; k6 open-vs-closed + ramping-vus; Jackson `ObjectMapper` Javadoc + `Mac` thread-safety sources; SerialGC/vendor GC notes; JFR overhead guides.
- Forbidden-word filter: `grep -R -i` over `research/findings/GITHUB_RESEARCH.md` + issue drafts — empty (this file contains no such word in any casing/spacing/hyphenation).
- Citation-coverage check: every A–F finding carries URL + SHA-or-doc-version + file + mechanism + evidence + rule-compat + license + test/adopt/reject (§§1–6 + §7); no-benchmark-invented check: §9 confirms zero executed/invented figures; classification check: every significant claim carries one of the six verdicts; license check: §7 table.
- Skills: `research, deep-research, agent-browser, browser-automation, prototype, documentation-writer, verification-before-completion` all invoked via skill tool (prototype throwaway-only, zero repo code; browser pair satisfied by direct API+fetch per §3). Standing map skills not separately invoked (grilling: scope fixed by #6 body, no open questions; to-spec/to-tickets: no new spec/tickets owned by #6; domain-modeling/java-springboot/github/git-workflow: `gh`/git used inline, Java domain terms reused from #4 without new modeling) — justification recorded here and in closing comment, none claimed as used.
