# Building a Java entry for the $12 Server Challenge

*Research report · October 13, 2026 · Based on the challenge repo, PR #33 and Arjay McCandless's published benchmark write-ups*

## 1. Summary

**The challenge.** Arjay McCandless (arjaythedev) asks people to implement one small Twitter-style API (5 endpoints, JSON, HS256 JWT auth) on SQLite, in any language. Entries run on a $12 DigitalOcean droplet (1 vCPU, 2 GB RAM, no swap) with k6 on a separate machine. The score is the largest number of simulated users that survive a 5-minute hold with p95 under 500 ms, p99 under 1 s and under 1% errors.

**What the numbers say.** In the original video (read via a third-party summary), a basic Spring Boot app held about 5,100 users on Postgres and 10,250 on SQLite; Go reached 11,750 and Rust 14,050 on SQLite. In his Oct 13 database post, a Go app reached 13,400 on SQLite. Rust's 14,050 users meant about 1,300 requests/s, so the whole box (kernel, Nginx, runtime, SQLite) has roughly 0.77 ms of CPU per request. This is a CPU-efficiency contest.

**PR #33** is the only Java entry I saw: Java 25 + Spring Boot 4 by nb-99, still open, called an unoptimized baseline by its author and wrapped in a lot of Docker, Kubernetes and benchmark tooling. Good for ideas, not a model to copy.

**Recommendation.** Write JWT, JSON and SQLite access as plain Java classes with no framework dependency. Pass the 42 checks with the simplest transport, measure under a 1 vCPU / 2 GiB limit, then swap in a lean non-blocking transport (Vert.x or Netty) and decide Nginx vs. direct. Sensible goals: beat the video's 10,250, then reach Go-class numbers (12–13k). These are goals, not predictions.

**Gaps.** I could not open the YouTube video (QGWKCaglSBI) and could read only 7 of PR #33's 35 files, none of them Java. Details in section 2.

## 2. What I could and couldn't read

| Source | Status | Notes |
| --- | --- | --- |
| YouTube QGWKCaglSBI | Not readable | YouTube blocked automated access (HTTP 429). The README links a different video, sQXFhh\_PiG4 (“Which Programming Language Can Handle the Most Users on a $12 Server?”), which I read via a third-party AI summary. Arjay's newsletter post of Oct 13, 2026 (Postgres vs MySQL vs MariaDB vs SQLite) may be the written companion of your video; I can't confirm that. |
| README, SPEC.md, schema.sql, test/test.sh, bench/nginx.conf | Read in full | Basis of sections 3 and 4. |
| bench/load.js, seed/make-seed.sh, test/run.sh | Not read | load.js is what you are scored on; read it first (request mix, think time, write share). |
| PR #33 | Partly read | Conversation, 4 commit messages and the first 7 files (.env-sample, .envrc, .gitignore, Dockerfile, Dockerfile.dockerignore, Makefile, OPTIMIZATIONS.md). Java sources, pom.xml, application.properties and README did not load, so claims about its code come from file names and OPTIMIZATIONS.md only. |

If you paste the video transcript (or its key numbers) and the PR's FeedController, PostRepository, Authentication, application.properties and pom.xml, I can close both gaps.

## 3. Rules and facts that shape the design

| Rule or fact | Consequence for a Java build |
| --- | --- |
| SQLite in-process via SQLITE\_PATH; no other database, cache or service | Choose a binding: xerial sqlite-jdbc (JNI) or your own Java 25 FFM binding to a libsqlite3 you compile in build.sh. |
| No caching across requests: no response or query-result caches, no in-memory table copies, no remembered JWT verifications (prepared statements, SQLite's page cache and mmap are fine) | Verify the HMAC and read SQLite on every request. Keying a Mac once is fine; memoizing token → claims is not. |
| Durable before responding: WAL with synchronous=NORMAL or stronger; group commit allowed if each response waits for its commit | NORMAL is the floor; no OFF, no in-memory database. |
| Schema and data fixed (no new tables, columns, indexes, triggers) | Query shape is your only SQL-side lever. |
| 1 vCPU, 2 GB, no swap, Ubuntu 24.04 x86\_64; running out of memory loses | Fixed heap, bounded buffers and queues. |
| Nginx optional: behind the provided Nginx on 127.0.0.1:3000, or direct on 0.0.0.0:80 with up to \~15,000 keep-alive connections. Behind Nginx, idle connections must stay open at least 65 s | Direct needs an event-loop server or virtual threads. The README puts Nginx at 14–20% of CPU. Set your idle timeout explicitly. |
| install.sh (root), build.sh (normal user), start.sh (foreground, env vars only: SQLITE\_PATH, JWT\_SECRET, HOST, PORT); pin versions; no prebuilt binaries; healthy within 60 s | Official JDK tarball; pin Maven and every dependency. |
| No kernel tuning or CPU pinning; in-process settings (GC, threads, pragmas) are fair game | JVM flags are allowed. |

**Scoring.** Warm-up: 1,000 users for 2 minutes (not scored). Limit search: bench/load.js from 2,500 users, doubling until a run fails, then narrowing to within 250. Confirmation: one 5-minute hold; if it fails, step down 250 and retry. Merged entries wait for the next benchmark batch.

**The provided Nginx** proxies to 127.0.0.1:3000 over HTTP/1.1 with 64 upstream keep-alive connections and has gzip on for JSON of 1 KB or more. I haven't checked whether k6 asks for gzip; if it does, part of Nginx's 14–20% is compression you would avoid by going direct (my inference).

## 4. Contract details the 42 checks enforce

- **Exact bytes.** Compact JSON, keys in SPEC order (post = id, body, created\_at, author, like\_count). Golden files are compared byte for byte for GET /feed and every seeded post.
- **created\_at** is stored text like 2025-12-31T23:59:19.409Z. Send it as stored; new rows get it from the column default, so use INSERT ... RETURNING id, created\_at.
- **like\_count** is computed per request; the suite likes a seeded post and expects the count to change at once. **Feed:** the 20 newest posts by created\_at DESC, id DESC; a new post must be first.
- **Ids:** 0, -1, abc and 1.5 give 400 invalid post id; a well-formed missing id gives 404 post not found; an unknown path gives 404 not found. **Auth runs before id validation:** POST /posts/abc/like is 401 without a token and 400 with one.
- **Auth errors:** no header or no Bearer prefix → 401 missing bearer token; bad signature, expired, alg none or not HS256, malformed → 401 invalid or expired token; valid signature but sub not a positive integer string, or username not a string → 401 invalid token payload.
- **POST /posts:** malformed JSON → 400 malformed JSON body; body missing, not a string or blank after trimming → 400 body is required; over 500 characters after trimming → 400 body must be at most 500 characters. Text with escaped quotes, &, angle brackets, é and ✓ must come back trimmed and intact. Count code points, not UTF-16 units (the table CHECK uses SQLite's length()).
- **Likes:** 201 first time, 200 with already\_liked true on repeats, 404 for a missing post. SQLite doesn't enforce the foreign key by default, so check the post exists yourself.
- **GET /health:** 200 with status ok, db ok and integer uptime\_s after SELECT 1; a 503 degraded body if it fails.
- The suite needs a fresh database copy; a second run fails the golden checks.

## 5. What the published benchmarks say

**Language video** (third-party summary; Postgres first, then SQLite for the top three). Users held for 5 minutes:

| Stack | Postgres | SQLite |
| --- | --- | --- |
| Laravel + PHP-FPM | 750 | – |
| Python FastAPI / Uvicorn | 2,150 | – |
| Node.js / Express | 3,250 | – |
| Bun | 4,200 | – |
| C# ASP.NET Core | 4,400 | – |
| Java Spring Boot | 5,100 | 10,250 |
| Go | 6,500 | 11,750 |
| Rust Axum | 6,900 | 14,050 |

As summarized: 50k users, 500k posts, about 2M likes; each user averages about 0.1 requests/s (so users ≈ 10× requests/s, roughly); minimal tuning; about ±10% variation between runs. On Postgres the database used about 60% of the CPU at the Go/Rust limit, which is why SQLite roughly doubled results.

**Database post, Oct 13, 2026** (primary source; Go app on the same $12 server; max users held 5 minutes):

| Database | 1x realistic | 1x write-heavy | 10x realistic | 10x write-heavy |
| --- | --- | --- | --- | --- |
| SQLite (WAL, NORMAL) | 13,400 | 6,800 | 14,500 | 7,300 |
| MariaDB | 9,300 | 5,000 | 8,000 | 4,400 |
| MySQL | 6,650 | 3,150 | 6,050 | 3,000 |
| Postgres | 6,350 | 3,850 | 5,150 | 4,050 |
| SQLite, synchronous=FULL | 10,000 | 1,550 | – | – |

What this means for a Java entry:

- With SQLite about 70% of the CPU went to the app (SQLite included), so your code is the bottleneck and every microsecond counts.
- The realistic mix is about 8% writes (43% in the write-heavy variant). At roughly 1,300–1,400 requests/s that is only about 100 writes/s (my arithmetic), so group commit is second-order unless load.js turns out to be write-heavy.
- synchronous=FULL wrecked the write-heavy case. Stay at NORMAL, the minimum the rules allow.
- Ten times more data changed little, so tune CPU per request rather than cache size.
- The open PR list I saw is full of C, C++ (io\_uring, epoll, uWebSockets), Rust, Zig and Go entries, several going direct and using group commit. Treat beating them as a stretch; beating 10,250 is the realistic first goal.

## 6. PR #33 (Java 25 + Spring Boot 4)

**Facts.** Opened Oct 10, 2026 by nb-99; still open; 4 commits, 35 files, +3,232 lines; no checks or reviews shown. Commits: the API behind Nginx with Docker Compose, contract tests, a bounded k6 limit search and Markdown/HTML reports (Oct 10); a Makefile workflow (Oct 11); docker/direct benchmark modes with the app and Nginx in one container capped at 1 vCPU and 2 GiB (Oct 11); a Kubernetes mode with Nginx and the app in one pod and k6 on another node (Oct 12). The Dockerfile pins Ubuntu 24.04 by digest, installs JDK 25.0.1 and Maven 3.9.11 under /opt through install.sh, and gives Nginx one worker and 16,384 connections; the Makefile's run target uses -Xmx512m. OPTIMIZATIONS.md implies Tomcat with a JDBC pool. File names (Authentication, FeedController, PostRepository, Responses, ErrorHandler, FallbackController, ApiException) suggest a conventional MVC layout.

**Feedback so far.** One commenter said it forgot spring.threads.virtual.enabled=true and later suggested WebFlux; the author replied that he hasn't optimized yet and will benchmark.

**Worth borrowing**

- The OPTIMIZATIONS.md discipline: record commit, JDK, flags, pragmas, pool sizes, workload, run count, percentiles, CPU and memory; compare repeated runs; never accept faster runs that skip work or return errors. Its candidate list (pool size, SQLite cache and mmap, WAL checkpoints, prepared statements, reader/writer split, group commit, one-statement likes, keep-alive limits, heap and GC, serving direct) is a good checklist.
- Reproducing the VPS limits locally: 1 vCPU, 2 GiB, no swap, the real Nginx config, k6 outside the container. The PR itself warns this doesn't reproduce the real machine or the remote load generator.
- Toolchains pinned under /opt in install.sh.

**Not worth copying.** Docker, Kubernetes and Python reporting aren't required (a submission needs install.sh, build.sh, start.sh, README.md, code and .gitignore) and enlarge the review surface. The framework is the likeliest limit on its score.

**Virtual threads and WebFlux (my view, to be measured).** On one vCPU, virtual threads add concurrency, not parallelism, and JNI calls into SQLite pin a virtual thread to its carrier while they run. They matter mostly for a direct-mode server holding thousands of idle connections. WebFlux with blocking JDBC needs explicit offloading, an awkward fit. Neither replaces removing framework overhead.

## 7. Recommended Java design

*This section is my engineering judgment, not something the sources state. Confirm each item by measurement.*

**Principle.** The score is how many users fit in the CPU budget, so optimise CPU per request first and tail latency (GC pauses, checkpoints) second, and keep hot-path allocation near zero.

**Transport options**

| Option | Strengths | Costs |
| --- | --- | --- |
| Spring Boot 4 (Tomcat) | Familiar; fastest route to correctness | Heaviest per request; direct mode needs virtual threads. Baseline only |
| Javalin/Jetty or Helidon SE 4 | Simple blocking code on virtual threads | HTTP and JSON layers you don't control. Fine for milestone 1 |
| Vert.x Core | Event loop, small footprint, tunable idle timeout | Blocking calls on the loop need care. Lean without writing a parser |
| Netty + hand-written decoder for five routes | Lowest overhead, full buffer control | You own HTTP edge cases; most code. Use after profiling shows HTTP dominates |
| Java 25 FFM + a libsqlite3 you build | No JNI/JDBC overhead; your own compile flags (other entries use PGO SQLite) | Most complex; needs --enable-native-access. Late stage |

**Blueprint**

- Classes: Main, HttpServer (routing, I/O), Jwt, Json (writer and small parser), Db (connections, pragmas, statements), Writer (write queue). Domain classes take bytes in and write bytes out, with no framework imports, so you can swap transports freely.
- Threads: 1–2 event loops, each with its own read-only connection; reads run inline because a page-cache read takes microseconds and there is one core. One writer thread with a bounded queue and a read-write connection; it completes the response only after COMMIT, and the server answers 503 if the queue is full rather than growing memory.
- Static bytes for every error body and the 404; /health runs SELECT 1 on a reader connection.

**SQLite settings (starting point, per connection)**

```sql
PRAGMA synchronous = NORMAL;   -- the floor allowed by rule 6
PRAGMA foreign_keys = OFF;     -- make sure your wrapper doesn't turn it on
PRAGMA temp_store = MEMORY;
PRAGMA cache_size = -16384;    -- 16 MB per connection; tune
PRAGMA mmap_size = 268435456;  -- check the real size of seed/feed.db (1x data is ~155 MB per the post)
PRAGMA busy_timeout = 5000;
```

- The seed database is already in WAL mode. On the writer connection only, consider wal\_autocheckpoint = 0 plus a timed PASSIVE checkpoint so the request path never pays for an fsync, with a size-based fail-safe so the WAL can't grow without bound.
- Prepare each statement once per connection and reuse it. Check with EXPLAIN QUERY PLAN that the feed uses posts\_created\_at\_id\_idx and the count uses likes\_post\_id\_idx.
- Reference queries are in SPEC.md (post SELECT with a correlated count over likes; INSERT INTO posts ... RETURNING id, created\_at). A one-statement like, also listed in OPTIMIZATIONS.md:

```sql
INSERT INTO likes (user_id, post_id)
SELECT ?, id FROM posts WHERE id = ?
ON CONFLICT (user_id, post_id) DO NOTHING;
-- 1 row changed  -> 201, already_liked false
-- 0 rows changed -> SELECT 1 FROM posts WHERE id = ?  (rare path)
--                   found -> 200, already_liked true; missing -> 404
```

**JWT and JSON**

- JWT: split on dots, require HS256 in the header, compute HmacSHA256 over header.payload with a per-thread Mac keyed once, compare with MessageDigest.isEqual, then read exp, sub and username. Check signature and exp (invalid or expired token) before payload shape (invalid token payload).
- Output: write bytes straight into a pooled buffer with an escape table for quote, backslash and control characters; copy UTF-8 as is. No reflection-based serialization on the hot path.
- Input: only body is read, but use a real streaming parser (jackson-core) so truncated or invalid documents are rejected correctly.
- Every response carries Content-Type application/json and Content-Length; never close the connection.

**Writes.** Start with a validated hand-off to the writer thread. Add group commit (drain the queue, BEGIN IMMEDIATE, run the statements, COMMIT, then complete the responses) only if load.js or profiling shows write contention; under NORMAL in WAL a commit appends to the WAL without an fsync, so the gain is smaller than in the FULL case from the post.

**JVM and memory.** Starting flags, to measure rather than trust:

```
-XX:+UseSerialGC -Xms640m -Xmx640m
-XX:MaxDirectMemorySize=192m -XX:ReservedCodeCacheSize=64m
-XX:+UseCompactObjectHeaders --enable-native-access=ALL-UNNAMED
-Xlog:gc:file=gc.log
```

The JVM should pick SerialGC by itself on one CPU (verify with -XX:+PrintFlagsFinal), but set it explicitly. -XX:+UseCompactObjectHeaders is a product flag on JDK 25; drop it on older JDKs. Budget the 2 GB explicitly (heap, metaspace, code cache, direct buffers, thread stacks, SQLite caches, the OS and Nginx) and stay well under it since there is no swap. The 2-minute warm-up before scoring lets the JIT settle.

**Nginx or direct.** Behind Nginx the app sees few connections and any server works, at the price of 14–20% of the CPU. Direct saves that and any gzip work, but you must hold about 15,000 keep-alive connections: use an event-loop server, set the idle timeout far above a user's average \~10 s gap between requests (and above 65 s to be safe), skip compression, and bind port 80 as a non-root user (the service gets CAP\_NET\_BIND\_SERVICE and LimitNOFILE=65535). Build behind Nginx first, then compare both on the real box.

## 8. Build plan

| Step | Work | Exit criterion |
| --- | --- | --- |
| M0 | Clone, build the seed (bash seed/make-seed.sh; needs Node 18+ and sqlite3), read bench/load.js | You know the request mix, think time and write share |
| M1 | Correctness baseline with the module split above | `bash test/run.sh submissions/<folder>` passes all 42 checks |
| M2 | Measurement rig: 1 vCPU / 2 GiB container, then a real $12 droplet plus a second droplet for k6 | Repeatable users figure (about ±10%), p95/p99, CPU split app vs Nginx |
| M3 | SQLite tuning: pragmas, prepared statements, mmap, checkpoint policy | Measured gain, tests green |
| M4 | Transport swap (Vert.x or Netty), precomputed responses, hand-written JSON | Measured gain, tests green |
| M5 | JVM flags and memory budget | Stable 5-minute hold with margin; no GC pauses near p99 |
| M6 | Direct vs Nginx | Winner measured on the real box, stated in the README |
| M7 | Packaging and PR | CI green |

## 9. Packaging checklist

- Folder `submissions/java-<framework>-<github-username>/` with install.sh, build.sh, start.sh, README.md, source and a .gitignore (no target/ output).
- install.sh runs as root on clean Ubuntu 24.04 (official JDK tarball, pinned version); build.sh runs as a normal user with pinned dependencies; start.sh runs the server in the foreground (exec java ...) and takes config only from env vars.
- README: language, framework, SQLite driver and versions, Nginx or direct, optimizations and why.
- Touch nothing outside your folder. CI runs install.sh and test/run.sh and must be green. Submitting licenses your code under MIT.

## 10. Risks and open questions

- Rule violations disguised as optimizations: caching a feed for a few milliseconds, memoizing JWTs, keeping a table in memory.
- Idle keep-alive shorter than 65 s behind Nginx can produce 502s; test with a long pause.
- No swap: an out-of-memory kill ends the run. Cap heap, direct memory and queues.
- Run-to-run noise is about ±10%; single runs can't justify small tweaks.
- Unknown to me: the load.js request mix, the contents of video QGWKCaglSBI, PR #33's actual code, whether k6 sends Accept-Encoding gzip, and when the maintainer will publish results.

## 11. Sources

- https://github.com/arjaythedev/twelve-dollar-server-challenge (README, SPEC.md, schema.sql, test/test.sh, bench/nginx.conf)
- https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33 (conversation, commits, first 7 files)
- https://youtubesummary.com/summary/sQXFhh\_PiG4 (third-party summary of the language video)
- https://blog.arjaythedev.com/p/postgres-mariadb-mysql-or-sqlite-which-one-is-actually-fastest (database post, Oct 13, 2026)
