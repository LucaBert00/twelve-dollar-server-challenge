# java-jdk-lucabert00 (Phase 3 baseline + T04 reads + T05 authed writes + T06 gate)

Plain-Java baseline for Epic #13. Phase 3: durable SQLite layer with WAL +
`synchronous=NORMAL` + foreign-keys-on + `busy_timeout=5000` on every pooled
connection, HikariCP pool of 4, live-DB `GET /health` and reference SQL.
T04 #17: live `GET /feed` + `GET /posts/:id` with byte-exact JSON
(`challenge.Json`). T05 #18: HS256 per-request JWT (`challenge.Auth`) plus
`POST /posts` and `POST /posts/:id/like` with auth-first routing.
T06 #19: official correctness (Gate D) — JUnit gate harness + fresh-DB
discipline + signed rule 5/6 checklist + oracle spot-diff vs C-pin `7a9cb99`.
Handlers from #17/#18 stay byte-exact (untouched).

## Stack

| Component | Pinned version |
|---|---|
| Language | Java 25 (`maven.compiler.release=25`) |
| JDK distribution | Eclipse Temurin `25.0.1+8` (Java 21+ is the floor, 25 is the approved line) |
| Build tool | Maven `3.9.11` (chosen because it matches the reference build shape) |
| SQLite driver | `org.xerial:sqlite-jdbc:3.53.4.0` (wired via `challenge.Db` in Phase 3) |
| Pool | `com.zaxxer:HikariCP:6.2.1` (wired, pool of 4: starting point, not optimum, per OPT-07) |
| Fat-jar plugin | `org.apache.maven.plugins:maven-shade-plugin:3.6.0` (build-time only) |
| JSON library | hand-rolled stdlib writer (`challenge.Json`, T04 #17 decision below; no Jackson) |

## Pins + checksums

| Artifact | Source | Checksum |
|---|---|---|
| Temurin JDK `25.0.1+8` x86_64 (`OpenJDK25U-jdk_x64_linux_hotspot_25.0.1_8.tar.gz`) | `https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.1%2B8/OpenJDK25U-jdk_x64_linux_hotspot_25.0.1_8.tar.gz` | SHA-256 `8daf77d1aacffe38c9889689bc224a13557de77559d9a5bb91991e6a298baa0d` |
| Temurin JDK `25.0.1+8` aarch64 (`OpenJDK25U-jdk_aarch64_linux_hotspot_25.0.1_8.tar.gz`) | same release, `aarch64` asset | SHA-256 `5c83b7d2121ed482fd06831a1eba1633dbab818aba6addddf48e075b97e6e9b8` |
| Maven `3.9.11` (`apache-maven-3.9.11-bin.tar.gz`) | `https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.tar.gz` | SHA-512 `bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978` |
| `org.xerial:sqlite-jdbc:3.53.4.0` jar | Maven Central `org/xerial/sqlite-jdbc/3.53.4.0/` | SHA-1 `47e21dd53efe1524fe0df6965cb65003ffe17172` |
| `com.zaxxer:HikariCP:6.2.1` jar | Maven Central `com/zaxxer/HikariCP/6.2.1/` | SHA-1 `2d60b91e74ee4ff10fc9a395d10b3b9c7061ef87` |

Re-print pins with one command (from the repo root):

```bash
/opt/java-challenge-25.0.1/bin/java -version; /opt/maven-challenge-3.9.11/bin/mvn -version; grep -E 'sqlite-jdbc.version|hikaricp.version|maven-shade-plugin|<artifactId>maven-shade-plugin</artifactId>' submissions/java-jdk-lucabert00/pom.xml
```

## Scripts contract

| File | Runs as | What it does |
|---|---|---|
| `install.sh` | root, once, clean Ubuntu 24.04 | installs the pinned JDK + Maven above, verifying archive checksums; idempotent via `.installed` markers |
| `build.sh` | normal user | `mvn clean package` with the pinned toolchain; downloads only pinned Maven Central deps; produces `target/server.jar`; no prebuilt binaries |
| `start.sh` | normal user, foreground | `exec java -Xmx512m -Dsun.net.httpserver.idleInterval=70 -jar target/server.jar`; honors `SQLITE_PATH` (required, fresh DB copy) / `JWT_SECRET` (required, consumed by `challenge.Auth` on every authed request) / `HOST` (default `127.0.0.1`) / `PORT` (default `3000`); `JAVA_OPTS` is the append point for later JVM flags; echoes `-Xmx` + all `sun.net.httpserver.*` flags to the startup log |

`start.sh` binds `HOST:PORT` via `challenge.Main` with a 5-path router
(`GET /health` live since Phase 3, `GET /feed` and `GET /posts/:id` live since
T04 #17, `POST /posts` and `POST /posts/:id/like` live since T05 #18) plus a byte-exact
`{"error":"not found"}` (`Content-Type: application/json`, status 404)
fallback. `SIGTERM` stops the server via a shutdown hook
(server stop + pool shutdown + `Db.close`) and exits `0`.

## SQLite durability posture (Phase 3, Roadmap Phase 3)

Single owner `challenge.Db`: HikariCP pool of 4 (starting point, not optimum;
sizing belongs to later phases), driver config plus explicit per-connection
pragma block (`journal_mode=WAL`, `synchronous=NORMAL`, `foreign_keys=ON`,
`busy_timeout=5000`) applied on every connection acquisition. Startup asserts
all 4 conns and fails fast when any pragma does not hold (session-only
reversion guard); the HTTP layer starts only after `4/4 ok`.

Prepared-statement posture (OPT-06): prepare at first checkout, reset + rebind
per use. Statements are created per checkout and closed per use, which reuses
the query plan without caching results. No response caches, no query-result
caches, no in-memory table copies; schema diff stays empty.

Reference SQL (verbatim SPEC shapes, immutable fallback for later phases) lives
in `Db` as constants: feed correlated-count with
`ORDER BY p.created_at DESC, p.id DESC LIMIT 20` on `posts_created_at_id_idx`,
single-post lookup `WHERE p.id = ?`, `INSERT ... RETURNING id, created_at` and
like upsert `ON CONFLICT (user_id, post_id) DO NOTHING`.

`GET /health` runs `SELECT 1` via the pool: 200
`{"status":"ok","db":"ok","uptime_s":<int>}` on success, 503
`{"status":"degraded","db":"unreachable","error":<string>}` when the probe
throws. Plan artifact: `docs/EXPLAIN_QUERY_PLAN.md` (fresh-DB baseline for DX-1).

Per-variant rule carried forward: `cache_size`-class session settings drift
per session like all pragmas, so every later variant re-asserts its pragmas
per connection and never assumes inherited values. No `cache_size`/`mmap`/
checkpoint knobs, no `FULL`-vs-`NORMAL` change and no pool-size sweep in this
ticket.

## JDK HttpServer keep-alive posture (Phase 2, planning doc section K)

| Setting | Default | Standing in this ticket | Why |
|---|---|---|---|
| `sun.net.httpserver.idleInterval` | 30 s | `70` (changed in `start.sh`) | SPEC requires idle ≥ 65 s behind Nginx (`keepalive 64`); JDK default closes too early; same value class as the reference 70 s pin |
| `sun.net.httpserver.maxIdleConnections` | 200 | 200 (recorded, unchanged) | Covers the 64-idle envelope with headroom; direct-mode ~15k sizing deferred to Phase 10/DX-3 |
| `sun.net.httpserver.nodelay` | false | false (recorded, unchanged) | Experimental latency knob; enabling it is unmeasured optimization, deferred to Phase 10 |

Only the first row changed; the other two are recorded in `start.sh` echo +
`Main` startup log and left at their defaults. No thread/pool/GC tuning here
(fixed pool 4 + backlog 128 are the starting posture; tuning belongs to
Phases 9-11).

## Router + logging hooks

Router is stdlib-only with minimal-diff senior-dev mode (no framework, no
abstractions): exact matches for `/health`, `/feed`, `/posts`, prefix shapes
for `/posts/:id` and `/posts/:id/like`, method-gated, 404 fallback otherwise.
Id validation for `GET /posts/:id` is live since T04 #17; auth-first routing
for both `POST` routes is live since T05 #18 (`POST /posts/abc/like` without a
token is 401, with a valid token is 400).

## Unauthenticated reads (T04, #17)

`GET /feed` runs the unchanged reference feed SQL per request (20 newest,
`ORDER BY p.created_at DESC, p.id DESC`) and `GET /posts/:id` runs the
unchanged single-post lookup; both render exact golden bytes via
`challenge.Json`. `created_at` passes through as stored and `like_count` is
the correlated count evaluated per request, so it reflects the database as of
the request (read-back `like_count:1` and top-of-feed proof land with T05).

## Authed writes (T05, #18)

`challenge.Auth` verifies HS256 per request with stdlib `javax.crypto.Mac`
plus URL-base64: exact 401 shapes (`missing bearer token` / `invalid or
expired token` / `invalid token payload`), `alg` rejection including `none`,
`exp` enforcement, `sub` positive-integer-string + `username` string checks.
Per-request `Mac` init is the simplest correct shape (no pooling cleverness).

Per-request HMAC cost note: verification pays the full HMAC on every authed
request by design (compliance price, never cached).

`POST /posts` trims before validating, counts 500 code points (not UTF-16),
and answers `malformed JSON body` / `body is required` /
`body must be at most 500 characters`; success is 201 with the trimmed body,
`like_count:0`, author from the token username, and `created_at` from the DB
default. `POST /posts/:id/like` checks existence first (the app checks),
answers first 201 `already_liked:false` and repeats 200 `true`, missing posts
404, and `abc` 400 after auth. Both writes run single statements in
auto-commit, so each commits before the response is sent (group commit is
explicitly not here).

JSON serialization decision: hand-rolled stdlib-only writer, not Jackson.
The suite payloads are plain ASCII plus a UTF-8 round-trip check; the writer
escapes `"`, `\` and control characters and passes everything else (including
`<>&/`, café/emoji) through raw UTF-8, which is all the goldens need. No new
dependency, smallest diff; Jackson (Apache-2.0, manifest-noted) stays the
correctness-first fallback if a later profile (Phase 11 OPT-23) indicts the
writer — no serialization tuning in this ticket.

Id validation: digits-only fitting in a `long` with value ≥ 1 wins
(`0`, `-1`, `abc`, `1.5` → 400 `invalid post id`; well-formed but absent →
404 `post not found`). Out-of-scope choices (SPEC: do whatever the framework
does): leading zeros resolve numerically (`01` reads post 1), digit strings
overflowing `long` read as invalid, and wrong methods on known paths plus
unknown paths keep the T02 404 `{"error":"not found"}` fallback unchanged.

Request-logging hooks for later benchmark columns (§3.4): total-request
counter, per-status counters, and a one-line `METHOD path -> status` log.
Accept-queue depth and refused connections are observed at the OS/executor
level; this ticket adds the response-code hook only, with no profiling agent.

## Serve mode

Nginx-first (`127.0.0.1:3000` behind `bench/nginx.conf`, `keepalive 64`).
Direct `0.0.0.0:80` is not served in Phase 2; mode comparison belongs to a
later phase (DX-3 owns it in Phase 10).

Backlog stays 128 behind Nginx (starting posture). The box grants
`LimitNOFILE=65535`; sizing for ~15k direct-mode idles is deferred to
Phase 10/DX-3 (notes only here).

## Benchmark readiness (Gate E, #20)

Nginx-first deploy. Everything the first benchmark assumes, verified before
it runs. No load numbers are produced here (first measurements belong to
#21); flags below are standing posture, explicitly not optima.

### `start.sh` final

- `-Dsun.net.httpserver.idleInterval=70` retained and echoed (SPEC needs idle
  ≥ 65 s behind Nginx `keepalive 64`; JDK default 30 s is insufficient).
- JVM posture `-Xmx512m`, default collector (no `-XX` flag; collector x heap
  tuning belongs to DX-7, Phase 11).
- Foreground `exec` (replaces the shell; `SIGTERM` stops the server via the
  shutdown hook and exits `0`).
- Env-only configuration: `SQLITE_PATH` (required, fresh DB copy) /
  `JWT_SECRET` (required) / `HOST` (default `127.0.0.1`) / `PORT` (default
  `3000`). `JAVA_OPTS` is the append point for later JVM flags.
- Healthy start well inside the ≤ 60 s rule (`test/run.sh` waits up to 60 s
  for `/health`; dev starts answer in seconds).
- Startup log proves the posture: `-Xmx` + `sun.net.httpserver.*` lines from
  `start.sh` plus live-property lines from `challenge.Main`.

### Nginx smoke + 65 s idle

- App behind a `bench/nginx.conf`-equivalent (`upstream keepalive 64`)
  serves the suite's health + feed correctly (smoke: `GET /health` 200,
  `GET /feed` 200 through the proxy).
- 65 s idle rule holds through Nginx: one client connection kept idle 66 s
  through the proxy answers 200 before and after (`IDLE-65s-HOLD: PASS`).
  App side is guaranteed by `idleInterval=70` (> 65); the 200-default
  `maxIdleConnections` covers the 64-idle envelope with headroom.
- Direct `0.0.0.0:80` serving stays out of scope here (mode comparison
  belongs to DX-3, Phase 10).

### Seed state

- `seed/feed.db` content sha matches the `seed/make-seed.sh` `EXPECTED`
  value (50k users / 500k posts / 2M likes + `seed/tokens.json`).
- Fresh-copy-per-run discipline: never serve `seed/feed.db` directly — every
  gate or bench run starts from a private copy
  (`cp seed/feed.db /tmp/bench.db`, `SQLITE_PATH=/tmp/bench.db`). The suite
  fails goldens on a reused DB by design (freshness proof in `docs/GATE.md`).

### Resource / limit notes (2 GB box, no swap)

- `LimitNOFILE=65535` is granted by the box service file; backlog stays 128
  behind Nginx (starting posture; direct-mode ~15k-idle sizing belongs to
  DX-3, Phase 10).
- Heap-vs-RSS: `-Xmx512m` caps the Java heap, not the process. RSS also
  carries SQLite page cache / `mmap`, pool + handler threads, direct
  buffers, and the Nginx worker resident beside the app — all inside the
  same 2 GB with no swap, so OOM is loss. Heap and RSS are recorded as
  separate columns (below), never conflated.
- JFR plan for #21: continuous low-overhead recording stays on per run with
  a disk cap + rotation (2 GB box), plus one JFR-off falsifier per lane to
  prove p99 is not a profiling artifact.

### Monitoring columns (Roadmap §3.4 template — no numbers here)

| VUs | k6 avg/med/p95/p99/max | err% | k6 exit | srv CPU% | srv RSS/free | gen CPU/RAM | mode (nginx/direct) | gate (suite line) | verdict |
|---|---|---|---|---|---|---|---|---|---|
| ... | ... | ... | ... | ... | ... | ... | nginx | `passed=42 failed=0` | ... |

Every #21 row carries the gate line, the full k6 command, pass AND fail+1
rows, pragma declaration, JFR on/off + artifact path, and drain-wait record.

### #21 preconditions (not run here)

- k6 runs on a SEPARATE machine (never co-located with the 1-vCPU server).
- Drain-wait discipline between runs: wait for ephemeral ports to drain
  (or use a fresh generator), and record generator count, ephemeral range,
  drain wait, and retry policy in every run log.

### Rehearsal record

- `install.sh` unchanged (no gaps found): pinned JDK 25 + Maven 3.9.11 with
  archive checksum verification, idempotent via `.installed` markers.
- `build.sh` unchanged (no gaps found): pinned-toolchain `mvn clean verify`,
  `target/server.jar`, no prebuilt binaries.
- Re-gate after script changes: JUnit green + suite line pasted on #20
  (authoritative `passed=42 failed=0` on Ubuntu/CI; Windows dev shows the
  known single special-char `curl` quirk, unchanged from #19).

Method: minimal-diff senior-dev mode (smallest change serving later phases).

## Optimizations

Empty until measured. No tuning in Phase 3 (minimal-diff senior-dev mode:
smallest change that serves later phases). Pool 4, backlog 128, `-Xmx512m`
and the pragma floor above are starting postures, explicitly not optima.

## Official correctness (T06, #19, Gate D)

JUnit harness under `src/test/java/challenge/` (`*Test`, AAA,
`@ParameterizedTest` id + auth matrices, `@Tag("gate")` vs `@Tag("bench")`);
`mvn test` runs the gate tags green (report: `target/surefire-reports/`).
Fresh-DB discipline, signed rule 5/6 checklist and oracle spot-diff live in
`docs/GATE.md`, `docs/CHECKLIST.md`, `docs/ORACLE_DIFF.md`. Verification is
`bash test/run.sh submissions/java-jdk-lucabert00` (what CI runs). No
benchmarking, no tuning, no lane changes here; handlers stay byte-exact.

## License notices

- This submission's own code is under the repo `MIT` license on merge (per challenge submitting rules).
- `org.xerial:sqlite-jdbc` — Apache-2.0 (manifest-noted direct dependency).
- `com.zaxxer:HikariCP` — Apache-2.0 (manifest-noted direct dependency).
- Reference shape precedent only: `reference/spring-boot-pr33:submissions/java-spring-boot-nb-99/{pom.xml,install.sh,build.sh,start.sh}` (Maven + script shape, read via `git show`, never merged, nothing copied wholesale).
- Oracle pool/pragma + SQL shapes only: `reference/spring-boot-pr33:submissions/java-spring-boot-nb-99/src/main/java/challenge/{Application.java,PostRepository.java}` (pool-4 + WAL/NORMAL/FK-ON/busy-5000 block and reference SQL, read via `git show`, never merged; re-implemented in `challenge.Db` with per-acquisition guards and startup assertions).
- Oracle JSON/error shapes only: `reference/spring-boot-pr33:submissions/java-spring-boot-nb-99/src/main/java/challenge/{Responses.java,ErrorHandler.java}` (key-order discipline, error shapes, read via `git show`, never merged; re-implemented in `challenge.Json` with no Jackson dependency).
