# SPRING_BOOT_CODE_REVIEW — PR33 deep dive (pinned to `7a9cb99`)

- Ticket: #4 `[Wayfinder-4/11] SPRING_BOOT_CODE_REVIEW` (OPEN at writing time)
- PR: https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33 —
  `feat: add Java 25 Spring Boot 4 submission`, author `nb-99`
- Pinned head: `7a9cb99` (`build(java): add Kubernetes benchmark mode`, 2026-10-12)
- Local ref: `reference/spring-boot-pr33` (read-only; inspected via `git show` /
  `git grep` only, never checked out, never merged)
- Scope rule: this review covers **only** the pinned head. The PR head has since
  moved (§9); post-freeze commits and comments are recorded as drift, not reviewed.

## 0. Reuse statement (no re-derivation)

- Identity, file inventory (35 files), commit list (4), stack snapshot, comments
  1–7 verbatim, and §8 claim table: reused from
  `research/sources/SPRING_BOOT_PR33.md` (§1–§8), re-verified fresh this session
  where marked.
- Verdicts V9–V12 reused from `research/analysis/FACT_CHECK.md` (no
  re-scoring; drift notes appended, classifications unchanged for pinned head).
- Video-vs-PR time-bounds reused from `research/analysis/VIDEO_ANALYSIS.md`
  §8.3 (PR33 opened Oct 10, unmerged/unbenchmarked at video snapshot).
- Truth hierarchy applied throughout: `SPEC.md` + `test/test.sh` >
  `REPORT_COMPARISON`/`FACT_CHECK`/`VIDEO_ANALYSIS` > PR33 discussion >
  AI reports > external.

## 1. Head status (re-verified 2026-10-14, fresh commands)

| Check | Result |
|---|---|
| PR state | OPEN, `mergeable: MERGEABLE`, `reviewDecision: ""` (no ruling) |
| Inline review comments (`pulls/33/comments`) | **0** |
| Formal reviews (`pulls/33/reviews`) | **0** |
| Conversation comments (`issues/33/comments`) | **13** — 7 at source-record freeze (2026-10-13) + 6 new (§9) |
| Maintainer (`arjaythedev`) comments | none observed |
| Benchmark numbers posted in discussion | none (one author-stated "~20%" in comment 11, §9 — no method, no raw data) |
| Commenter associations | all `NONE` (per source record; no maintainer voice) |

Ticket-scope census (the 7): comments 1–7 below, §2. Drift census (the 6 new):
comments 8–13, §9. Total verified statement: **13 / 0 / 0**
(conversation / inline / reviews) at writing time vs **7 / 0 / 0** at freeze.

## 2. The 7 in-scope comments (with URLs + verdicts)

Base URLs: `https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33#issuecomment-<id>`

| # | Author / date (UTC) / link id | Claim in one line | Verdict |
|---|---|---|---|
| 1 | zitreF, 2026-10-13T17:53, `6151765247` — "you forgot: `spring.threads.virtual.enabled=true`" | Flag absent from config | **VERIFIED** (observation true; §5). Effect of enabling: unproven |
| 2 | stohirov, 2026-10-13T19:02, `6152991472` — "@nb-99 configs..." | Terse prompt, no technical claim | No claim to classify |
| 3 | nb-99, 2026-10-13T19:09, `6153132205` — "Have not done much optimizing so far, will run a bench" | Submission is an unoptimized baseline | **VERIFIED** as author statement; consistent with code (defaults everywhere, §4/§5) |
| 4 | nb-99, 2026-10-13T19:11, `6153154430` — "any suggestions for config adjustments?" | Solicitation, no claim | No claim to classify |
| 5 | zitreF, 2026-10-13T19:11, `6153154645` — "it will surely bring optimizations, … try spring-webflux" | Virtual threads will surely help; WebFlux as next step | **UNSUPPORTED** — certainty ("surely") with zero posted numbers; WebFlux conflicts with comment 7 + SQLite single-writer reality, also unmeasured |
| 6 | nb-99, 2026-10-13T19:14, `6153220267` — "Sure, I'll run both" | Commitment to test vthreads + WebFlux | Commitment, not a claim; outcome reported in drift comments 8/11 (§9) |
| 7 | stohirov, 2026-10-13T19:31 (edited), `6153521191` — "better to go with nio … raw Netty … framework will add too much latency … you have not enabled virtual threads" | (a) NIO better here; (b) raw Netty beats framework on latency; (c) vthreads flag off | (a)(b) **UNSUPPORTED** — "currently testing", competing submission pending, no profile/diff/numbers; directionally plausible for connection-count only. (c) **VERIFIED** true (§5) |

Net for the 7: two true observations (flag absent ×2), one accepted author
statement (unoptimized baseline), three unproven performance claims, two
non-claims. No benchmark numbers, no profiles, no config diffs posted.

## 3. Code inspection at `7a9cb99` (all paths under `submissions/java-spring-boot-nb-99/`)

### 3.1 Bootstrap — `src/main/java/challenge/Application.java`

- `@SpringBootApplication` + `SpringApplication.run`: standard Boot entry.
  Constructor injection throughout; `final` classes; no field injection.
- `DataSource` bean: fails fast if `SQLITE_PATH` names no regular file
  (`IllegalArgumentException`); `JWT_SECRET` empty rejected in `Authentication`.
  **VERIFIED** hygiene.
- Pragmas per pooled connection: `WAL` + `synchronous=NORMAL` + `FK ON` +
  `busy_timeout=5000` (`Application.java:27-30`). Rule 6 wiring **VERIFIED**.
- Pool: HikariCP via Boot defaults, `maxPoolSize=4`, `minimumIdle=1`,
  `autoCommit=true`, `connectionTimeout=10000`. Pool-4 is **VERIFIED** as the
  current setting and explicitly **not** an optimum (consistent with
  FACT_CHECK V10 + `OPTIMIZATIONS.md` "Change the JDBC pool size" candidate).
- Single-writer reality: nothing serializes writers in Java at this head;
  concurrent writes queue in SQLite's busy handler (5 s timeout), not in app
  code. Unmeasured under load — needs-experiment E-pool, §8.

### 3.2 Configuration — `src/main/resources/application.properties` (6 lines)

```properties
server.address=${HOST:127.0.0.1}
server.port=${PORT:3000}
server.tomcat.keep-alive-timeout=70s
spring.web.resources.add-mappings=false
spring.jackson.deserialization.fail-on-trailing-tokens=true
spring.main.banner-mode=off
```

- `spring.threads.virtual.enabled` **absent** — re-verified this session by full
  file read (§5). Platform (NIO-connector, platform-thread pool) Tomcat applies.
- `keep-alive-timeout=70s` satisfies the SPEC behind-Nginx rule (≥ 65 s).
  **VERIFIED** (`SPEC.md:18-19`).
- `HOST/PORT` env indirection matches the SPEC runtime contract
  (`127.0.0.1:3000` behind Nginx). **VERIFIED** (`SPEC.md:8-14`).
- `fail-on-trailing-tokens=true` makes `'{"body":"x"} trailing'` a 400; the
  challenge suite never sends such bodies, so this is allowed strictness, not a
  SPEC conflict (§6).

### 3.3 Controllers — `FeedController.java`, `FallbackController.java`

- Routes: `GET /health`, `GET /feed`, `GET /posts/{id}`, `POST /posts`,
  `POST /posts/{id}/like` — the five SPEC endpoints, no extras.
- Auth-first ordering **VERIFIED**: `create()` authenticates before touching the
  body; `like()` authenticates before `postId(id)` — matches the SPEC
  auth-before-id rule (`SPEC.md:72`, `test/test.sh:83`).
- Body validation mirrors the suite: missing/non-string/whitespace-only →
  `body is required`; post-trim `codePointCount > 500` → 400; oversize
  (`> 16 KiB`) → 413. The 16 KiB behavior sits in SPEC's explicit out-of-scope
  zone (`SPEC.md:83`) — allowed, not a conflict.
- `like()` semantics: existence `SELECT count(*)` then
  `INSERT … ON CONFLICT DO NOTHING`; returns 201 first / 200 repeat / 404
  missing — byte-exact vs `test/test.sh:110-112`. Two round-trips and
  non-atomic check-then-insert: benign here (no delete endpoint exists), so a
  confirmed inefficiency, not a correctness bug. `OPTIMIZATIONS.md` already
  lists combining the two statements as a measure-first candidate.
- `FallbackController` maps `/error` → 404 `not found`; unknown paths fall
  through to the same shape. Consistent with the suite (which never GETs
  `/error`) and with `ApiTest` expectations.
- Key order: records + `@JsonPropertyOrder` (`Responses.java`) emit SPEC-exact
  key sequences (`SPEC.md:26-27`); `ApiTest.expect` asserts `application/json`
  content type on every response.

### 3.4 Auth — `Authentication.java`

- Manual HS256: 3 non-empty segments; header must parse with `alg == "HS256"`
  and no `crit`/`b64`; constant-time signature compare
  (`MessageDigest.isEqual`); `exp` numeric and `> now`; `sub` positive-integer
  string + `username` string, else three distinct 401 shapes that match SPEC's
  `invalid or expired token` vs `invalid token payload` split
  (`SPEC.md:64-66`, `test/test.sh:74-82`). **VERIFIED** against both suites.
- `iat` unchecked (SPEC does not require it); `typ` ignored (suite signs with
  `"typ":"JWT"`, accepted). No deviation.
- Per-request `Mac.getInstance/init` + `String.matches("[0-9]+")` (regex
  compiled per call): confirmed allocation shape, unmeasured cost — already
  covered by the `OPTIMIZATIONS.md` "reduce JSON/auth allocation" candidate.
- Failure mode noted, not a bug: `exp` compared via `asDouble()`; at current
  epoch magnitudes precision is exact, and the `exp == now` boundary is
  untested by either suite (rejected as expired — unspecified, allowed).

### 3.5 JDBC/SQL — `PostRepository.java` (via `JdbcTemplate`)

- Feed/get SQL is the SPEC reference query verbatim
  (`SELECT … (SELECT count(*) …) … JOIN …` + `ORDER BY created_at DESC, id DESC
  LIMIT 20` / `WHERE p.id = ?`), served by the fixed
  `posts_created_at_id_idx` (`schema.sql:24`, `SPEC.md:46-53`). **VERIFIED** —
  characterization, not a finding: any correlated-subquery cost is the SPEC's
  own reference shape.
- `create()` uses single-statement `INSERT … RETURNING id, created_at` on an
  auto-commit connection: one round-trip, committed before the 201 — Rule 6
  **VERIFIED** by reading.
- No `@Transactional` anywhere: correct for single-statement writes; the
  two-statement like is intentionally non-atomic (see §3.3).
- No app-level prepared-statement reuse at this head (`JdbcTemplate`
  re-prepares per call; driver-level caching not configured in code) —
  `OPTIMIZATIONS.md` lists per-connection reuse as a candidate. Observation,
  not defect.

### 3.6 Error/serialization — `ApiException.java`, `ErrorHandler.java`, `Responses.java`

- `@RestControllerAdvice` maps `ApiException → status + {"error":msg}`,
  unknown throwables → 500 `internal server error` with a stack-trace log.
  Matches the SPEC error table (`SPEC.md:59-70`).
- Jackson 3 throughout (`tools.jackson.databind`); `com.fasterxml` annotations
  package retained for `@JsonPropertyOrder` — normal Jackson 3 layout, no
  finding.
- `created_at` sent as stored (TEXT, millis + `Z`); never reformatted.
  **VERIFIED** vs `SPEC.md:30-31` + `ApiTest` format assertion.

### 3.7 Threading

- Request path: Tomcat NIO connector (Boot default) on platform threads; no
  virtual-threads flag (§5); no `@Async`, no custom executors; blocking JDBC on
  request threads.
- Effective concurrency gate is the Hikari pool (4 connections) against
  Tomcat's default thread pool (~200): a ~50:1 thread-to-connection ratio means
  most request threads would park on pool checkout under write pressure — a
  **VERIFIED** configuration shape with **unmeasured** effect (needs-experiment
  E-pool, §8). This is exactly why comment 1's flag cannot be accepted on
  faith: extra request threads do not create extra SQLite writers
  (`OPTIMIZATIONS.md` says the same).

### 3.8 JVM / build / deploy / bench / tests

- JVM: `-Xmx512m` (`start.sh`, `Makefile run`); no GC/heap-ratio/JIT flags.
  Leaves ~1.5 GiB of the 2 GiB budget for SQLite page cache, OS, and Nginx —
  sizing note, unmeasured.
- Build: Maven 3.9.11, Boot 4.0.3 parent, `webmvc` + `jdbc` starters,
  `sqlite-jdbc` 3.53.4.0 pinned; `spotless check` (googleJavaFormat AOSP) bound
  to `validate`; `build.sh` runs `clean verify`. Pinned archives checksummed
  in `install.sh`. Submission-hygiene **VERIFIED** (rules 10 equivalents).
- Deploy: app + Nginx share one container capped `cpus: 1.0`, `mem_limit:
  2g`, `memswap_limit: 2g` (`compose.yaml`); entrypoint runs Nginx
  (`bench/nginx.conf` site) + app as UID 10001 (`container-start.sh`);
  Nginx `worker_processes 1 / worker_connections 16384`. Behind-Nginx mode at
  this head — matches the SPEC contract and README rule 9's Nginx option.
- Bench tooling (unexecuted — no blind reproduction): `benchmark.py`
  warm-up → doubling probes → 250-user refinement → 5-min confirmation
  (p95 < 500 ms, p99 < 1000 ms, < 1% errors); three modes
  (`scripts/docker.sh|direct.sh|k8s.sh`, `MODE=docker|direct|k8s`); k6 v2
  pinned/download checksummed (`scripts/lib.sh`). Described, not run; no
  benchmark figures are quoted from it anywhere in this review.
- Tests: `ApiTest` (11 test methods) covers golden bytes, auth-before-id,
  malformed JSON matrix, alg/exp/claim matrix, unicode + commit-before-respond
  (re-reads the row via raw JDBC), 8-way concurrent like idempotency,
  **pragma assertions on all 4 pool connections**
  (`journal_mode=wal, synchronous=1/NORMAL, foreign_keys=1, busy_timeout=5000`),
  feed order/limit-20, 503-on-closed-pool, and the 16 KiB bound. The
  pragma test is the executable counterpart of the Rule 6 wiring claim.

### 3.9 `OPTIMIZATIONS.md`

- Explicit measure-first policy ("correctness baseline … measure changes …
  do not accept faster runs that skip work") — the document the author cites
  in comment 3. 13 candidates, each with what-to-measure + cost/risk; the
  SQLite-wait-queue caution directly answers comment 1.
- Non-negotiables restated: WAL + `synchronous=NORMAL`-or-stronger, no
  response/query caches, no table copies, no remembered JWT verifications, no
  schema changes — a faithful restatement of challenge rules 5/6. **VERIFIED**
  vs repo `README.md:42-49`.

## 4. Confirmed problems vs speculation (pinned head)

Confirmed by reading (impact unmeasured unless noted):

1. Virtual-threads flag absent; platform-thread Tomcat + pool-of-4 is the
   operative threading model (§3.7). — VERIFIED
2. Pool-4-vs-thread-count mismatch shape (§3.7). — VERIFIED shape /
   EXPERIMENTAL HYPOTHESIS for any sizing claim
3. Like path costs 2 SQL round-trips; non-atomic but benign. — VERIFIED
4. Per-request JWT `Mac` init + per-call regex compile. — VERIFIED shape,
   unmeasured cost
5. No app-level prepared-statement reuse. — VERIFIED observation
6. `exp == now` boundary unspecified + untested. — VERIFIED gap, trivial

Speculation (correctly left open by author + OPTIMIZATIONS.md, must be
measured, not asserted): vthreads gain (comments 1/5), WebFlux gain (comment
5), NIO/raw-Netty superiority (comment 7), pool/writer-split sizing,
page-cache/`mmap`/checkpoint tuning, JSON/auth allocation, direct-vs-Nginx.

## 5. Virtual-threads-absent re-verification (fresh, this session)

- Full read of `application.properties` (6 lines, §3.2): no
  `spring.threads.virtual` key. **VERIFIED absent.**
- `git grep -n 'spring.threads'` over the submission tree at `7a9cb99`: no
  matches. **VERIFIED.**
- `git grep -i -n 'webflux'` / `'reactor'` / `'netty'`: no matches in code.
  **VERIFIED absent** (the word "virtual" occurs only in
  `OPTIMIZATIONS.md:22` as a *candidate* and in `benchmark.py` /
  `benchmark_report.py` as k6 "virtual users" wording — unrelated).
- Comments 1 and 7(c) are therefore true observations; every *effect* claim
  built on them (comments 5, 7a/b) stays **UNSUPPORTED**.

## 6. SPEC / test-suite conflict count: **0**

Checked `FeedController`, `Authentication`, `PostRepository`, `ErrorHandler`,
`FallbackController`, `Responses` against `SPEC.md` (all sections) and
`test/test.sh` (126 lines, incl. golden reads, health/404/id matrix, auth
matrix, create/like/read-back, feed-20): **no contradictions found**.
Allowed-strict behaviors (SPEC-silent, suite-consistent): 16 KiB → 413;
trailing-token JSON → 400 (`fail-on-trailing-tokens`); JWT `crit`/`b64`
rejection. One untested boundary: `exp == now` (§4.6).
Rule 4 (pass `test/test.sh` 100%, exact bytes) cannot be confirmed without
executing the suite — **not attempted** (no blind reproduction); code shapes
are byte-consistent by reading (key order, exact messages, status codes).

## 7. Challenge rules 5 / 6 notes (repo `README.md:42-49`)

- Rule 5 (no cross-request caching): **no violation at pinned head** —
  every request re-queries SQLite; JWT signature re-verified per request
  (`Authentication.authenticate` has no memoization); no response/query
  caches, no table copies. `OPTIMIZATIONS.md:35-36` explicitly forbids them.
- Rule 6 (durable commit before response; WAL + NORMAL-or-stronger):
  **compliant by construction** — pragmas enforced per connection
  (`Application.java:27-30`), asserted on all 4 pool connections by
  `ApiTest.enablesRequiredDatabasePragmas`, single-statement auto-commit
  writes complete before the 201. Group commit unused (allowed, not required).
- Schema fixed (`schema.sql` untouched — submission adds no migrations):
  **no allegation, no evidence of change**.

## 8. Needs-experiment list (method only, no conclusions)

Transferred, not re-derived (parents: FACT_CHECK E-series / REPORT_COMPARISON
§4; PR-comment hypotheses V11–V12 owned by #2/#5):

- E-vt: platform threads vs `spring.threads.virtual.enabled=true`, identical
  SQL/pragmas/pool, 1 vCPU — threads, pool-wait, p99. (Settles comments 1/5.)
- E-stack: MVC vs WebFlux vs NIO/raw path — only after a profile shows
  thread-blocking (not SQL) as the bottleneck. (Settles comments 5/7.)
- E-pool: Hikari 4 vs candidates with reader/writer split; busy-timeout
  counters, queue time, write p99. (Comments 7 + OPTIMIZATIONS.md rows 1/6.)
- E-sql: like check+insert combine with 404/200/201 preserved; prepared reuse
  per connection; `EXPLAIN QUERY PLAN` on fresh + grown data.
- E-jvm: heap/GC sizing inside 2 GiB with SQLite + Nginx resident; allocation
  profiles for JSON/auth after SQL ruled out.
- E-edge: direct-vs-Nginx under ~15k keep-alive conns (SPEC rule 9 trade-off).

## 9. Post-freeze drift (OUT OF SCOPE — recorded, not reviewed)

Between source-record freeze (2026-10-13T19:31Z) and this session, the PR
gained **9 commits** (`7a9cb99 → 845accd`, per `gh api …/pulls/33/commits`)
and **6 comments** (ids `6156408428`, `6161794817`, `6161803969`,
`6163631335`, `6164487308`, `6164699637`):

- `baf8550` direct serving (drop Nginx, 60k keep-alive sizing) +
  per-connection prepared-statement reuse; `82b2b20` readme cleanup;
  `6684ebf` k6 TIME_WAIT fix; `4b80bc1` WebFlux-on-Netty variant;
  `9013ef7`/`2df5a26` serialized writer + SQLite-built JSON;
  `fb9120f` merge of the WebFlux variant; `eb50654`/`845accd` readme rewrites.
- Comment 8 (nb-99): vthreads/WebFlux "not that big of a difference … so far"
  — interim author report, no method/numbers → underlying claim
  **UNSUPPORTED** here.
- Comments 9–10 (stohirov): framework+Nginx as bottleneck, pointer to impl
  `#58` — **UNSUPPORTED** + lead for #6 GITHUB_RESEARCH (blocked on #4).
- Comment 11 (nb-99): "webflux actually gained ~20%, virtual threads a bit
  less … Going with webflux … Lost nginx" + merge message `fb9120f`
  ("38,000 users … about 20% above 31,750") — first quantitative claims in
  the thread, **UNSUPPORTED** in this review (no method, no raw data, no
  review; post-freeze code uninspected).
- Comments 12–13: group-commit question; author declines (WAL+NORMAL +
  durability-queue rationale) — consistent with Rule 6; code uninspected.
- Consequence: statements in §§1–7 pinning "Nginx mode / MVC / pool-4 / no
  prepared reuse" describe **`7a9cb99` only** and are already
  **OUTDATED** for the live PR head. Follow-up ownership: #6 GITHUB_RESEARCH
  (re-baseline against `845accd`+) and #9 JAVA_ARCHITECTURE_DECISION.

## 10. Classification summary

| Verdict | Count | Items |
|---|---|---|
| VERIFIED | 14 | PR OPEN/unmerged/0 reviews/0 inline (§1); flag absent ×3 (§5); baseline-admission (§2.3); keep-alive 70 s; HOST/PORT contract; 5 endpoints; auth-first order; error table match; reference-SQL match; WAL/NORMAL/FK/busy wiring; single-stmt commit-before-respond; no rule-5/6 violation; no schema change |
| PARTIALLY VERIFIED | 1 | 13/0/0 live census (API-verified just now; associations/CI not re-checked) |
| UNSUPPORTED | 7 | vthreads-gain; WebFlux-gain; NIO-better; raw-Netty-faster; bottleneck-is-framework; ~20% figure; 38k/31.75k merge-msg figures |
| INCORRECT | 0 | — |
| OUTDATED (for live head; current for pin) | 4 | Nginx-mode, MVC-only, pool-4-no-reuse, no-prepared-reuse characterizations |
| EXPERIMENTAL HYPOTHESIS | 6 | E-vt, E-stack, E-pool, E-sql, E-jvm, E-edge (§8) |

SPEC conflicts at pin: **0**. Fabricated benchmarks in this review: **none**
(no suite or load test executed; no figures invented).

## 11. Verification appendix (commands run this session)

- `git status` clean; `git log --graph` linear; SHAs re-confirmed:
  `research/java-analysis = main = 2c5aeeb`, `backup/main-baseline-2026-10-13
  = 026bdb6`, `reference/spring-boot-pr33 = 7a9cb99` (never moved).
- `gh issue view 4` OPEN; #2/#3/#5 CLOSED (precondition met); claiming
  comment posted before any work.
- `git ls-tree -r 7a9cb99` file list (35 files) matches source record §2.
- Full `git show` of all 9 Java sources + `application.properties` +
  `OPTIMIZATIONS.md` + `pom.xml` + `Dockerfile` + `compose.yaml` +
  `Makefile` + `build/start/install/seed-local/container-start/lib.sh` +
  `.env-sample` at `7a9cb99` (read-only; reference branch never merged).
- `git grep` at pin: `spring.threads` 0 hits; `webflux/reactor/netty` 0 hits;
  pragma wiring hits in `Application.java` + `ApiTest` + docs only.
- `gh api pulls/33/{comments,reviews}` → 0/0; `issues/33/comments` → 13 with
  authors/dates/bodies; `pr view 33` → head now `845accd`, still OPEN,
  `reviewDecision ""`; commit list via API (9 post-freeze commits, messages
  recorded, code uninspected).
- `SPEC.md` + `test/test.sh` (full 126 lines) + repo `README.md` rules +
  `schema.sql` read; prior docs reused by reference (V9–V12, §8.3).
- Forbidden-word filter (§4.4): `grep` over this file + issue drafts — empty
  (this file contains no such word in any casing/spacing/hyphenation).
- Skills invoked via skill tool, in order: acquire-codebase-knowledge,
  map-codebase, code-review, code-review-and-quality, java-springboot,
  java-docs, domain-modeling, verification-before-completion. Standing map
  skills (grilling, research, to-spec, to-tickets, documentation-writer,
  github, git-workflow) not separately invoked: scope is fixed by the ticket
  body (no open questions to grill, no new tickets, doc written directly in
  final form, `gh`/git used inline without needing workflow skills) — noted
  here instead of claimed.
