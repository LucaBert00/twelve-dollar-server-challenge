# JAVA_ARCHITECTURE_DECISION — A vs B vs C (+D evidence-gated) (Wayfinder-7/11, closes #9)

- Ticket: #9 `[Wayfinder-7/11] JAVA_ARCHITECTURE_DECISION -- A vs B vs C` (OPEN at writing time)
- Output: this file only (`research/findings/JAVA_ARCHITECTURE_DECISION.md`). No app code.
- Blocked by Issues 1–6 (#2/#5/#3/#4/#6/#7, all CLOSED — precondition met, see §8).
- #8 BENCHMARK_STRATEGY is OPEN in a parallel session: NOT taken here. Harness method is
  consumed from `GITHUB_RESEARCH.md` §§F1–F3/D3 + `OPT-09` (see §0.6); #8 reconciliation is
  flagged for #10 (see §5.9).
- Direct upstream: `research/findings/OPTIMIZATION_CATALOG.md` @ `6523ba5` (44 rows OPT-01..OPT-44,
  splits 11/17/5/6+5, top experiments X1–X7, validated 0) — read in full, consolidated not re-derived.
- Reference pin: `reference/spring-boot-pr33` @ `7a9cb99` (read-only; inspected via `git show` /
  `git grep` only, never merged). Live-head drift facts via prior API evidence (#4 §9, #6 A1–A3),
  not via fetch.
- Truth hierarchy: `SPEC.md` + `test/test.sh` > prior analysis/findings docs (#2–#7) >
  PR33/PR58 discussion > AI reports > external.
- Every significant claim below carries one of `VERIFIED` / `PARTIALLY VERIFIED` / `INCORRECT` /
  `UNSUPPORTED` / `OUTDATED` / `EXPERIMENTAL HYPOTHESIS` with primary evidence. Needs-experiment
  rows carry method, never a conclusion. No benchmarks invented (validated Java count stays 0).

## 0. Method, skills, inputs, filters

### 0.1 Options provenance (ticket scope + evidence gate)

- Ticket #9 mandates minimum: Option A Plain Java (JDK HttpServer + virtual threads + direct JDBC),
  Option B Netty (event-loop + bounded DB executor), Option C Spring Boot (PR33 ref).
- Option D (PR58 single-thread-inline / epoll-via-FFM minimal extreme) is ADMITTED on research
  evidence only: #6 A4 (design presence PARTIALLY VERIFIED — page + `Db.java` 220 lines + `build.sh`
  39 lines + README 105 lines read beyond README) + #6 B3 (single-thread-inline hypothesis with
  sharp falsifier) + catalog OPT-29..OPT-33 (high-risk rows, falsifier-gated). Not pre-decided;
  gated to late-stage (§5.8).
- No further options: no other stack in #2–#7 evidence clears the "research evidence" bar.
  Javalin/Jetty/Helidon/Vert.x appear only as unmeasured mentions (claudeJava12 via #2 §2) —
  correctly left out until a profile or a reviewed submission puts them in.

### 0.2 Skills used (ticket mandate, in order)

Invoked via skill tool this session: `architecture-blueprint-generator` (§2 layer/component
structure + §3 split reading), `domain-modeling` (§1 term fixing), `java-springboot` (§2.3/§3
Spring-defaults assessment — what helps vs what taxes 1 vCPU), `java-docs` (reference-grade
precision of this doc: first-sentence claim summaries, exact file+line/SHA citations),
`verification-before-completion` (gate before every claim below and before commit/push/close).

Standing map skills — skipped with reason (ticket permits skip with justification, none claimed
as used): `grilling` (scope fixed by #9 body + handed-over leads; no open scoping questions —
proceed), `research` (no new discovery owned by #9 — consolidation of closed docs #2–#7 plus
pin re-verification only), `to-spec`/`to-tickets` (each §5 experiment row is a verifiable slice
with acceptance, but no new spec file or ticket set is owned by #9), `documentation-writer`
(`documentation-and-adrs` shape already inherited via catalog structure; second docs skill adds
no structure), `github`/`git-workflow` (standard linear ops — claim comment, single-file commit,
ff-only merge, close — each verified with fresh `gh`/`git` commands per §8).

### 0.3 Inputs verified (fresh this session)

- Issue #9 body (scope source of truth) + Epic #1 body (order + Decisions) + milestone list
  (#9 OPEN; #2/#3/#4/#5/#6/#7 CLOSED; #8/#10/#11/#12 OPEN untouched) — fresh `gh` commands (§8).
- `docs/WAYFINDER_MAP_PROMPT.md` §4 (global hard rules) — read in full.
- `research/analysis/REPORT_COMPARISON.md` (246 lines), `research/analysis/FACT_CHECK.md`
  (145 lines), `research/analysis/VIDEO_ANALYSIS.md` (590 lines),
  `research/analysis/SPRING_BOOT_CODE_REVIEW.md` (370 lines) — read in full.
- `research/findings/GITHUB_RESEARCH.md` (348 lines), `research/findings/OPTIMIZATION_CATALOG.md`
  (138 lines) — read in full.
- `SPEC.md` (84 lines), `schema.sql` (26 lines), `bench/load.js` (70 lines), `test/test.sh`
  (126 lines), `docs/PROJECT_CONTEXT.md`, `research/sources/SPRING_BOOT_PR33.md` — read in full.
- Pin re-verification (read-only, this session): `application.properties` 6 lines (no
  `spring.threads.virtual` key); `git grep spring.threads` 0 hits; `git grep -i webflux` 0 hits;
  `Application.java` pool block (Hikari 4/1, WAL+NORMAL+FK-on+busy-5000) — see §8.

### 0.4 Forbidden-word filter

Case-insensitive grep for the forbidden term (base prompt §4.4) run over the draft and again over
the final file before commit — empty both times (see §8). Where that concept is needed, this doc
writes `minimal-diff senior-dev mode`.

### 0.5 Classification discipline

Evidence cells open with the verdict + primary citation (repo URL + SHA + file + line, doc URL,
or measured output). `EXPERIMENTAL HYPOTHESIS` rows name a §5 experiment instead of a winner.
Quarantined figures (OPT-40..OPT-44) are never quoted as targets (§6).

### 0.6 #8-OPEN harness note (method consumed, not owned)

#8 BENCHMARK_STRATEGY is OPEN in a parallel session, so this doc does NOT write the runnable
harness. Every §5 experiment runs under the consumed method: correctness-first (`test/test.sh`
100% on a FRESH DB, OPT-01) → k6 closed-model `ramping-vus` (thresholds p95<500 / p99<1000 /
err<1%, VU = browsing loop with think/idle mix, F1) → warm-up/probe/narrow/confirm limit-search
(V8) → TIME_WAIT drain discipline (F2, `~32k` threshold NOT quoted as constant — OPT-44) →
pragma assertions + JFR per run (F3/D3, OPT-09). If the #8 session lands a different runnable
form, #10 reconciles; experiment definitions here are method-pinned enough to survive that
(§5.9).

## 1. Domain terms (fixed for #9–#10 consumers)

- `Option A (plain Java)`: JDK `HttpServer` + direct JDBC (starting posture Hikari-4 per OPT-07;
  single-owner/1W+3R only under E-pool) + blocking request handling; threading variant
  platform-vs-virtual decided by DX-4, starting posture platform threads (pin-consistent).
- `Option B (Netty)`: Netty/Reactor-Netty event loops own sockets only; every blocking JDBC call
  hops to a bounded DB executor (one thread per SQLite conn as the PR33-drift shape, pool sweep
  under E-stack); error bodies keep SPEC JSON shapes.
- `Option C (Spring Boot ref)`: PR33 lineage. `C-pin` = `7a9cb99` (MVC/Tomcat behind Nginx,
  pool-4, no prepared reuse — the line-reviewed correctness oracle). `C-live` = `845accd`
  (WebFlux-on-Netty + direct + serialized writer + SQLite-built JSON — message-verified intent,
  code uninspected, all figures UNSUPPORTED).
- `Option D (minimal extreme)`: PR58 shape (`3cc06f6`) minus nothing — epoll-via-FFM + FFM-SQLite
  + single-thread-inline + group commit; admitted as evidence-gated Option D, high-risk late-stage
  only (§5.8, OPT-29..OPT-33).
- Inherited statuses (#7 §1): `candidate` (needs its experiment), `adopted` (gate/posture in force),
  `validated` (measured on target box under harness — count 0, unchanged), `rejected` (prohibited
  or quarantined). Splits: safe-baseline / experimental / high-risk / prohibited (+ rejected
  guidance) — meanings unchanged from catalog §1.
- `Needs-experiment backlog` carried: E-vt/E-stack/E-pool/E-sql/E-jvm/E-edge (#4 §8, refined #6
  §8); catalog execution frontier X1–X7 (#7 §4); decision experiments DX-1..DX-8 defined in §5.

## 2. Options defined (layer structure)

Per-option layers (HTTP / concurrency / data access / pooling / serialization / observability /
deploy) so the §3 axes compare the same cuts:

### 2.1 Option A — Plain Java (JDK HttpServer + direct JDBC)

- HTTP: JDK `com.sun.net.httpserver.HttpServer` (blocking handlers, keep-alive; idle ≥65 s sized
  per SPEC envelope — E1 VERIFIED numbers). No framework DI/middleware/context objects on the path
  (video J5 strip lesson pre-applied by construction, not by profiling — profiling still required
  before further strips, OPT-21).
- Concurrency: blocking handlers on platform threads (start) vs one virtual thread per request
  (DX-4 toggle). Effective gate is the JDBC pool (OPT-07 shape: pool-4 start), not thread count.
- Data: direct JDBC via xerial `sqlite-jdbc` (Apache-2.0); SPEC reference SQL first (feed
  correlated-count + `ORDER BY created_at DESC, id DESC LIMIT 20` on `posts_created_at_id_idx`);
  prepare-once-per-conn baseline posture (OPT-06); pragmas WAL+NORMAL+FK-on+busy-5000 per pooled
  conn with pragma assertions (OPT-02).
- Serialization: singleton `ObjectMapper` (thread-safe after config — FasterXML Javadoc, #6 D2)
  with `@JsonPropertyOrder`-equivalent key discipline; SQLite-built JSON only under DX-1 with
  golden-byte re-proof (OPT-12).
- Observability: JFR per run + GC logs + pool-wait/queue counters (OPT-09/D3 method); no framework
  actuator surface — smaller to observe, all custom.
- Deploy: Nginx-first (`127.0.0.1:3000`, 64 idle upstream) then direct (`0.0.0.0:80`) under DX-3;
  `LimitNOFILE=65535` + non-root bind sized from the start (E1 envelope).
- Standing: [EXPERIMENTAL HYPOTHESIS as the build baseline — recommended starting lane, §4 — with
  every sizing claim gated on §5 runs; no Java bench exists].

### 2.2 Option B — Netty (event-loop + bounded DB executor)

- HTTP: Netty (or Reactor Netty) event loops own sockets; idle keep-alive holds minimal per-conn
  state (PR33 drift message claim — PARTIALLY VERIFIED, allocator-source-unverified, #6 B2).
- Concurrency: blocking Xerial calls NEVER on event loops — hop to a bounded executor (one thread
  per SQLite conn per PR33 `4b80bc1` message; 1-thread-per-conn vs small-pool sweep under DX-5).
- Data/pooling/serialization: same SQL/pragmas/pool-start discipline as A (single-variable rule:
  stack is the ONLY delta in DX-5); writer placement (Java queue vs busy-handler) under DX-2.
- Observability: Netty metrics + JFR; reactive debugging/profiling harder on 1 vCPU (accepted cost,
  #6 B2) — only paid if a profile shows thread-blocking as the bottleneck (E-stack gate).
- Deploy: same Nginx-first → direct sequence (DX-3 settles the mode once, reused by all lanes).
- Standing: [EXPERIMENTAL HYPOTHESIS — profile-gated lane, NOT the starting lane; pattern
  PARTIALLY VERIFIED (#6 B2), every magnitude UNSUPPORTED].

### 2.3 Option C — Spring Boot (PR33 lineage)

- C-pin (`7a9cb99`): Spring MVC on Tomcat NIO (platform threads; vthreads flag VERIFIED absent —
  re-verified this session) + `JdbcTemplate` (re-prepares per call, #4 §3.5) + Hikari 4/1 +
  keep-alive 70 s (satisfies ≥65 s rule — VERIFIED, `SPEC.md:18-19`) + WAL/NORMAL/FK-on/busy-5 s
  per conn (VERIFIED wiring, `Application.java:27-30`) + `ApiTest` 11 methods incl. pragma
  assertions on all 4 conns + 8-way concurrent-like idempotency + 503-on-closed-pool (#4 §3.8).
  Auth-first order, exact error table, SPEC reference SQL, `created_at` sent as stored — 0 SPEC
  conflicts at pin (VERIFIED, #4 §6; rule-4 execution unattempted by design).
- Known pin shapes (VERIFIED shape, unmeasured effect): ~50:1 thread-to-connection parking
  (Tomcat ~200 vs pool-4, #4 §3.7); 2-round-trip like (benign — no delete endpoint); per-request
  `Mac` init + per-call regex compile; no app-level prepared reuse.
- C-live (`845accd`): WebFlux-on-Netty + direct-60k + prepared-reuse + TIME_WAIT fix + serialized
  writer (one writer conn, Java queue) + SQLite-built JSON + generated-key lookup off; group commit
  explicitly declined (comments 12–13, Rule-6-conservative — PARTIALLY VERIFIED rationale,
  #6 A3). Technique existence PARTIALLY VERIFIED (messages + file list); `~20%` / 38k-vs-31.75k /
  interim "not that big" figures UNSUPPORTED (#4 §9, #6 A3, OPT-42).
- Standing: C-pin is the CORRECTNESS ORACLE (adopted reference — byte-compare + rule shapes reused,
  never merged, never copied verbatim without MIT notice); C-live is a comparison point for DX-5
  re-runs, not a result. Building the submission ON C-pin is NOT recommended (§4.2).

### 2.4 Option D — Minimal extreme (PR58 shape, evidence-gated admission)

- Admitted on: #6 A4 (13 files @ `3cc06f6`, `Db.java` prepare/bind/step/reset + `POST_JSON`/
  `FEED_SQL` + `BEGIN IMMEDIATE…COMMIT` + `walCheckpointPassive` read; `build.sh` SHA-pinned
  amalgamation + flags read; README rationale read) + #6 B3 (falsifier: loop-stall p99 under
  write burst) + OPT-29..OPT-33 rows.
- Shape: zero-dependency Java 25, hand HTTP/1.1 on epoll-via-FFM, direct FFM to SQLite 3.54.0
  amalgamation (`THREADSAFE=0`, `QPSG`, `-O2 -march=native`), one thread + one conn + everything
  inline, per-row `json_object` joined in Java, group commit per wake-up batch (responses after
  COMMIT), `wal_autocheckpoint=0` + ~1/s PASSIVE, `locking_mode=EXCLUSIVE`, `cache_size=-65536`,
  `mmap_size=0`, single reused `Mac`, SerialGC 256 m fixed heap.
- Liabilities (why gated, not starting): hand parser must re-prove EVERY SPEC edge (auth-before-id,
  id matrix, malformed JSON, 404 shapes, key order, trim/500-code-point rules) — uncertain rule
  compat pending #9-line-review of `Server.java`/`Jwt.java`/`Json.java` (OPT-29/30/32 `uncertain`);
  Linux-only; single-conn write ceiling; RST eviction reconnect churn vs <1% budget unanalyzed.
  Laptop figures (128k–416k users, µs/CPU deltas) UNSUPPORTED for the droplet (OPT-43).
- Standing: [EXPERIMENTAL HYPOTHESIS — high-risk late-stage probe DX-8 only, headroom-gated].

## 3. Eleven-axis evaluation (44 cells: 11 axes × A/B/C/D)

How to read: each cell = verdict + one-line rationale. Evidence paragraphs with primary citations
follow in §3.2. Option C is scored as C-pin for build questions (C-live deltas noted where they
change the cell).

| Axis | A Plain Java | B Netty | C Spring Boot (pin; live notes) | D Minimal extreme |
|---|---|---|---|---|
| 1 SPEC compat | VERIFIED posture — 5 endpoints + auth-first + error table + key order + stored-`created_at` re-proved per variant via OPT-01 gate; no framework edge behavior to fight | PARTIALLY VERIFIED — same gates pass in principle; reactive error-path + backpressure shapes add unreviewed edge surface (E-stack line review required) | VERIFIED (pin) — 0 SPEC conflicts by line review (#4 §6); allowed-strict extras (16 KiB→413, trailing-token 400) suite-consistent; C-live uninspected → PARTIALLY VERIFIED as compat-carry | PARTIALLY VERIFIED shape / uncertain edges — README claims full JWT verify + after-COMMIT responses, but `Server`/`Jwt`/`Json` edge paths NOT line-audited here; hand parser is the liability |
| 2 1-vCPU fit | EXPERIMENTAL HYPOTHESIS (favored) — no framework background threads; blocking-JDBC-on-few-threads matches single-writer reality; collector/pool knobs are the only CPU tax | EXPERIMENTAL HYPOTHESIS — event loops idle cheap, but loop + DB-executor + Netty allocator threads split one core; wins only if thread-blocking (not SQL) gates | PARTIALLY VERIFIED tax — Tomcat default pool (~200) vs pool-4 parking shape VERIFIED (#4 §3.7); Spring DI/middleware/context per-request work is video-J5-class CPU tax (Python 11k→22k, C# low-level Kestrel) | EXPERIMENTAL HYPOTHESIS — one thread removes context switches for µs lookups (PR58 rationale); single slow write stalls the WHOLE loop (falsifier, #6 B3) |
| 3 Memory (2 GB, no swap) | EXPERIMENTAL HYPOTHESIS (favored) — smallest baseline RSS (no container/pools beyond Hikari + driver); heap competes with page cache only | PARTIALLY VERIFIED cost — Netty slab/allocator + extra executor stacks raise floor; idle-conn state small per message but allocator-source-unverified | VERIFIED sizing note — pin `-Xmx512m` leaves ~1.5 GiB for page cache/OS/Nginx (#4 §3.8, unmeasured); container + Spring metadata is the heaviest floor of A/B/C | PARTIALLY VERIFIED floor — ~10 MB live heap claim at 256k idle conns is README-measured laptop-local (UNSUPPORTED droplet); SerialGC-256 m + shared native buffers is the leanest shape on paper |
| 4 HTTP concurrency (keep-alive envelope) | EXPERIMENTAL HYPOTHESIS — JDK HttpServer must be SIZED for 64 (Nginx) then ~15k–64k idle (direct); no prior art in evidence at 64k for this server | PARTIALLY VERIFIED pattern — event loops are the proven idle-conn shape (PR33 drift message + video Erlang/Cowboy single-scheduler note); droplet sizing unmeasured | VERIFIED behind-Nginx (pin 70 s ≥ 65 s rule); direct unproven at pin (MVC era); C-live claims direct-60k sizing — message-only (PARTIALLY VERIFIED) | PARTIALLY VERIFIED design — backlog 4096 + LRU + RST eviction + 2 ms park read in diff; droplet-unmeasured; RST churn vs error budget unanalyzed (OPT-32) |
| 5 SQLite interaction | VERIFIED discipline — same reference SQL + WAL/NORMAL + prepared-reuse + pragma-assertion gates as oracle (OPT-02/06); pool-4 start, 1W+3R/Java-queue under DX-2 | Same as A (stack is not the SQL variable) — bounded-DB-executor sizing is the only B-specific SQL-adjacent knob (DX-5 sub-sweep) | VERIFIED at pin (reference-SQL verbatim, single-stmt commit-before-201, `RETURNING`); C-live adds serialized-writer + SQLite-JSON (PARTIALLY VERIFIED intent, UNSUPPORTED magnitudes) | PARTIALLY VERIFIED shapes — atomic like (`INSERT…WHERE EXISTS…ON CONFLICT`, `changes()`→201/200/404) + per-row JSON + group-commit-with-guard read in `Db.java`; `EXCLUSIVE`+`THREADSAFE=0` forfeits multi-conn writes |
| 6 Tail latency (p99 < 1 s gate) | EXPERIMENTAL HYPOTHESIS — fewest queues on the read path; p99 decided by pool-wait + SQLite writer + GC pauses (all measured in DX runs, never assumed) | EXPERIMENTAL HYPOTHESIS — non-blocking loops protect p99 from thread parks ONLY if threads are the p99 source; extra executor hop ADDS a queue otherwise | PARTIALLY VERIFIED risk — pool-checkout parking under write pressure is the known p99 suspect (#4 §3.7 shape); C++ 910 ms vs Rust 224 ms at shared 64k is the video warning that p99 decides ties (J11) | EXPERIMENTAL HYPOTHESIS with falsifier — best-case p99 (no queues) vs worst-case (loop stall on checkpoint/WAL growth); DX-8 write-burst run kills or keeps it |
| 7 Complexity | VERIFIED lowest-framework — stdlib HTTP + one driver + (optionally) Hikari/Jackson; every line is submission code, nothing hidden | PARTIALLY VERIFIED higher — reactive model + operator chains + Netty tuning surface; debugging/profiling harder on 1 vCPU (#6 B2) | VERIFIED highest-structural — Boot autoconfiguration + MVC + Jackson 3 + Hikari + Tomcat knobs; most documented, most moving parts (35 files at pin) | VERIFIED highest-custom — hand HTTP parser + FFM bindings + amalgamation build + native flags; Linux-only, zero portability; every edge is hand-proved |
| 8 Observability | PARTIALLY VERIFIED — JFR + GC + pool counters suffice (OPT-09 method); no built-in dashboards, all wiring custom | PARTIALLY VERIFIED — Netty metrics are the richest conn-level signal; reactive-stack traces are the poorest debuggability | VERIFIED richest off-shelf — Boot/JFR/`ApiTest`-style gates exist at pin (pragma assertions, idempotency, 503-on-closed-pool); most signal per line written | PARTIALLY VERIFIED thinnest — README counters + JFR flag claimed; custom everything; profile-gated adoption means observability is built alongside |
| 9 Deploy | VERIFIED both-modes — Nginx-first then direct per SPEC options (OPT-08/20); smallest image, fastest start (≤60 s rule trivially met) | Same modes, heavier image (Netty jars) + more tuning knobs per mode; k8s/docker/direct scripts reusable from PR33 tooling shape | VERIFIED most-trodden — pin runs app+Nginx in one capped container as UID 10001 (`compose.yaml` 1.0 CPU/2 g); three bench modes described (docker/direct/k8s, #4 §3.8, unexecuted) | PARTIALLY VERIFIED narrowest — pinned-JDK + amalgamation rebuild per stepping (`-march=native` not portable); direct-only evidence; Nginx mode unshown |
| 10 Optimization potential | EXPERIMENTAL HYPOTHESIS (widest cheap headroom) — every catalog row applies with no framework veto: DX-1 (JSON), DX-2 (pool), DX-3 (direct), DX-6 (group commit), DX-7 (GC) | Same rows, narrower — stack-specific rows (executor sizing) replace generic ones; WebFlux operator overhead can veto µs-level wins | PARTIALLY VERIFIED bounded — OPTIMIZATIONS.md 13 candidates are real, but each pays framework tax first (J5: strip after profiling); C-live already spent the MVC→WebFlux + direct + JSON moves (direction of travel noted) | EXPERIMENTAL HYPOTHESIS (highest ceiling, highest variance) — PGO/QPSG/epoll shapes are the late-stage moves; each needs rebuild + byte-gate + falsifier |
| 11 Maintainability | VERIFIED simplest-to-own — stdlib + pinned driver, minimal-diff senior-dev mode changes, no major-version migrations | PARTIALLY VERIFIED — Netty/Reactor version coupling + reactive expertise required; smaller hiring surface than Spring, larger than stdlib | VERIFIED most-maintainable-by-convention — Boot 4.0.3 parent + `spotless check` + constructor injection + pinned archives checksummed; heaviest to tune, easiest to hand over | INCORRECT as maintainable — hand parser + FFM + native build is expert-only, unportable, every SPEC edge hand-carried; adopt only if DX-8 headroom forces it |

### 3.1 Axis notes with primary evidence (condensed)

- Ax1 SPEC: 5 endpoints + compact JSON + exact key order + live `like_count` + text-`created_at`
  passthrough VERIFIED (`SPEC.md:23-43`; `test/test.sh:52-57,95-123`); auth-before-id VERIFIED
  (`SPEC.md:72`; `test/test.sh:83,113`); error table VERIFIED (`SPEC.md:59-70`); trim/code-point
  VERIFIED (`SPEC.md:67-69`; `schema.sql:13`; `test/test.sh:85-93`); like 201/200 + existence +
  health-`SELECT 1` + feed-20 VERIFIED (`SPEC.md:38-42`; `test/test.sh:60-61,110-123`); schema
  fixed VERIFIED (`schema.sql:1-2,24-26`). Pin 0-conflict VERIFIED (#4 §6, re-cited not
  re-derived). D edge paths PARTIALLY VERIFIED at most (README + `Db.java` shapes; `Server`/
  `Jwt`/`Json` unaudited — #6 A4 verdict reused).
- Ax2 1-vCPU: box VERIFIED (`docs/PROJECT_CONTEXT.md:5-6`; `SPEC.md:8-14`; transcript §[01:01]).
  Framework-strip wins VERIFIED as observations (Python FastAPI→Uvicorn 11k→22k, transcript
  §[06:04]; C# no-middleware 32k, §[08:05]; Swoole long-running 40k, §§[10:07]–[11:08]); Java
  transfer EXPERIMENTAL HYPOTHESIS in every cell (no Java bench — FACT_CHECK V19–V20).
  Vthreads-help / WebFlux-next / NIO-better UNSUPPORTED (comments 1/5/7, no numbers — #4 §2,
  re-cited). "More threads ≠ more SQLite writers" VERIFIED shape (single-writer reality, #4
  §§3.1/3.7; #6 C2).
- Ax3 memory: 2 GB/no-swap VERIFIED (same box citations). Pin `-Xmx512m` sizing note VERIFIED as
  present (#4 §3.8, unmeasured). SerialGC single-CPU mechanism PARTIALLY VERIFIED via vendor docs
  (present in PR58, droplet-unmeasured — #6 D1). All optima EXPERIMENTAL HYPOTHESIS (DX-7).
- Ax4 concurrency: SPEC envelope VERIFIED (`SPEC.md:18-21`: Nginx 64 idle / ≥65 s; direct ~15k /
  `LimitNOFILE=65535` / `CAP_NET_BIND_SERVICE`); pin 70 s VERIFIED. 64k-conntrack ceiling + Rust
  64k/P99-224 ms/CPU-50% vs C++ 64k/P99-910 ms/CPU-68% VERIFIED (transcript §§[15:11]–[19:14];
  FACT_CHECK V24) — headroom decides ties (J11, VERIFIED reporting policy). `~32k` port threshold
  PARTIALLY VERIFIED mechanism / UNSUPPORTED threshold (OPT-44 — never a target).
- Ax5 SQLite: durability boundary WAL+NORMAL VERIFIED (sqlite.org WAL + pragma pages; forum
  app-crash-vs-power-loss note — #6 C1); Rule-6 floor VERIFIED (`README.md:46-49` equivalents;
  #4 §7). Reference SQL is the SPEC's own shape VERIFIED (`SPEC.md:46-53`; pin verbatim — #4
  §3.5 characterization, not a finding). Group-commit-with-guard VERIFIED as legal observation
  (Go §[07:05] + rule guard); transfer EXPERIMENTAL HYPOTHESIS, second-order at ~100 writes/s
  under realistic mix (parent §4.4 arithmetic from `load.js` probabilities — #7 OPT-19 reused).
  PR33 group-decline rationale PARTIALLY VERIFIED (comments 12–13, code uninspected — #6 A3).
- Ax6 tail: thresholds VERIFIED (`bench/load.js:38-41`); closed-model semantics VERIFIED (k6 docs +
  `load.js` 70 lines — #6 F1; coordinated-omission caveat flagged for #8). Bottleneck catalog
  VERIFIED per-submission (video §6.1: CPU cliffs, conn churn, tail-only fails, C structure-notch).
  Every Java p99 claim EXPERIMENTAL HYPOTHESIS (DX runs report p95/p99/err + CPU + RSS at pass AND
  fail+1 — J11 policy).
- Ax7/11 complexity/maintainability: file counts VERIFIED (pin 35 under submission dir per source
  record §2; PR58 13 files per `pulls/58/files` — #6 A4); Boot hygiene VERIFIED (constructor
  injection, `spotless check`, pinned/checksummed archives — #4 §3.8); D expert-only posture
  VERIFIED by construction (hand parser + FFM + native build, Linux-only — #6 A4 disadvantages).
- Ax8 observability: JFR <1% overhead PARTIALLY VERIFIED (vendor-stated, box-unmeasured — #6 D3);
  pin `ApiTest` 11-method gate VERIFIED present (#4 §3.8); mapper thread-safety VERIFIED (FasterXML
  Javadoc — #6 D2); `Mac` non-thread-safe VERIFIED (Oracle docs + analysis — #6 D2; `ThreadLocal`
  with `remove()` is the documented-safe shape).
- Ax9 deploy: both modes are explicit SPEC options VERIFIED (`SPEC.md:8-21`; rule 9); pin
  container shape VERIFIED (`compose.yaml` cpus 1.0 / 2 g / no-swap-equivalent + UID 10001 +
  Nginx `worker_processes 1` — #4 §3.8, described-not-run); PR58 direct-only + `-march=native`
  non-portability PARTIALLY VERIFIED (#6 A4).
- Ax10 potential: catalog splits VERIFIED present (11/17/5/6+5, 44 rows — read this session);
  X1–X7 ranked with falsifiers VERIFIED present (#7 §4); validated-0 VERIFIED (#7 §0: no Java
  bench exists).

## 4. Recommendation (explicit baseline + why)

### 4.1 One-line decision

**Build the submission on Option A (Plain Java: JDK HttpServer + direct JDBC, Hikari-4 start,
Nginx-first), keep Option C-pin (`7a9cb99`) as the correctness oracle, gate Option B (Netty) on
a thread-blocking profile, and hold Option D (PR58 minimal extreme) as a headroom-gated
late-stage probe.**

### 4.2 Why A — principle by principle

- **Java-first**: the fork intention is a first-principles JDK + SQLite-driver + minimal-HTTP entry
  (`docs/PROJECT_CONTEXT.md:32-36`); A is that intention with the smallest dependency surface
  (stdlib + xerial + optionally Hikari/Jackson — all Apache-2.0/MIT-clean, §7).
- **SPEC-exact**: A carries no framework edge behavior to fight; every SPEC edge (auth-before-id,
  id matrix, malformed JSON, trim/500-code-point, 201/200/404 like, feed-20, golden bytes) is
  proved per variant through the OPT-01 gate, with C-pin shapes as the byte reference (not a merge
  source — MIT notice discipline, §7).
- **Durability preserved**: WAL + `synchronous=NORMAL` floor on every pooled conn + pragma
  assertions (OPT-02, C1 VERIFIED boundary); group commit only in the wait-guarded legal shape
  (OPT-19); FULL-cost probe DX-note without direction change (OPT-24).
- **No prohibited caching**: OPT-34..OPT-39 gates enforced by code read per variant (per-request JWT
  re-verify, per-request SQLite reads, no table copies, no schema change, no kernel tuning, no JWT
  downgrade); plan/state reuse (prepared stmts, mapper, `ThreadLocal<Mac>`) is explicitly allowed
  reuse, not result caching (OPT-03/06/11).
- **Minimal deps**: A adds nothing beyond driver (+ Hikari/Jackson if the profile keeps them);
  B adds Netty/Reactor coupling, C adds the Boot/Tomcat/Jackson/Hikari stack, D adds a native
  build chain — each heavier alternative must earn its place by measurement (DX-5/DX-8).
- **Measurable**: A is the cheapest lane to profile (one flame graph settles thread-vs-SQL first,
  #6 B3); every §5 run uses the OPT-09 harness with falsifiers, so A can lose honestly to B/C-live
  shapes in DX-5 if the profile indicts threads.

### 4.3 Why not B/C/D now (gated, not rejected)

- **Not B now**: pattern PARTIALLY VERIFIED, magnitudes UNSUPPORTED; reactive complexity + Netty
  tuning surface on 1 vCPU is paid ONLY after a profile shows thread-blocking (not SQL) as the
  bottleneck (E-stack gate, V18 stance reused). DX-5 is B's fair fight — same SQL/pragmas/pool.
- **Not C-pin as the build base**: the pin is the oracle, not the ceiling — Tomcat/pool-4 parking
  shape + framework per-request tax are known headwinds (#4 §3.7, video J5); C-live's own author
  already moved to WebFlux + direct + SQLite-JSON (direction of travel away from MVC). Rebuilding
  on C-pin would start behind the author's own evolution while paying the heaviest floor (Ax3/7).
- **Not C-live as the base**: intent PARTIALLY VERIFIED but code uninspected and figures
  UNSUPPORTED — adopting it means inheriting unreviewed reactive + writer-queue + SQLite-JSON
  semantics without the line review #4 gave the pin. It competes in DX-5 after review, not before.
- **Not D now**: highest ceiling AND highest variance — hand-parser SPEC liability + Linux-only +
  single-conn write ceiling + RST-churn budget risk; OPT-29..OPT-33 `uncertain` compat pending edge
  audit. DX-8 probe only if DX-1..DX-7 land and headroom still demands it.

### 4.4 What "baseline" does and does not mean

- Means: first correct build + first profiled numbers + lane in which DX-1..DX-4 run. A can be
  dethroned by DX-5 measurement — the recommendation is a starting lane with a falsifier, not a
  coronation (V17 non-conclusion stance, VERIFIED decision policy).
- Does not mean: vthreads on (DX-4 decides), direct-first (DX-3 decides, Nginx-first for
  comparability), group commit on (DX-6 decides; author-decline respected as default-off),
  GC tuned (DX-7 decides; pin `-Xmx512m` posture meanwhile).

## 5. Comparative experiments (single-variable; every insufficient-evidence point covered)

Harness for ALL (consumed, §0.6): OPT-01 gate → k6 closed-model thresholds → warm-up/probe/
narrow/confirm → drain discipline → pragma assertions + JFR. Report p95/p99/err + CPU + RSS at
pass AND fail+1 (J11). One variable per run; rollback criterion stated; maps to catalog X/E rows.

| ID | Variable (one) | Fixed | Gates per run | Falsifier / stop rule | Maps to |
|---|---|---|---|---|---|
| DX-1 | Serialization: reference SQL vs per-row `json_object`+Java join vs `json_group_array` aggregate | Same pool/pragmas/stack/mode | OPT-05 goldens + `ApiTest`-style byte compare + alloc profile | No alloc/p99 delta → keep reference query | X1 (OPT-12/13) |
| DX-2 | Writer placement: Hikari-4 vs 1W+3R split vs single Java writer queue | Same SQL/pragmas/stack | Commit-before-respond re-assert + busy-timeout expirations + queue-time + write p99 | Reader starvation or queue-p99 regression → keep pool-4 | X2 (OPT-15/16) |
| DX-3 | Deploy mode: Nginx vs direct, same build | Same SQL/pool/stack/JVM | Accept queue, `EMFILE`, eviction rate, p99 vs CPU; 65 s idle rule both modes | Descriptor/idle-memory blowup or cap hit → stay behind Nginx | X3 (OPT-20) |
| DX-4 | Threading flag: platform vs `spring.threads.virtual.enabled=true` (A-lane; C-lane repeats once) | Identical SQL/pragmas/pool | Threads, pool-wait, p99 spread | No threads/pool-wait/p99 delta → platform stands | X5 (OPT-17; E-vt) |
| DX-5 | Stack lane: A vs B vs C-live-shape, profile-gated | Same SQL/pragmas/pool/mode from DX-1..DX-3 winners | JFR allocation + pool-wait counters; C-live shapes line-reviewed BEFORE the run | No thread-blocking in profile → A stands, B/C-live closed | X4 (OPT-18/21; E-stack) |
| DX-6 | Group commit with wait-guard (+ atomic-like sub-row OPT-14) | Same lane as winner | Realistic + write-heavy mixes; 404/200/201 + idempotency gates; failure-atomicity designed | No write-p99 win at realistic mix, or atomicity cost → decline (author default respected) | X6 (OPT-19/14) |
| DX-7 | GC×heap: Serial/G1/Parallel/ZGC × 256/384/512 m + NMT, SQLite+Nginx resident | Same lane/SQL/load | p99 + GC logs + native-mem; stay well under 2 GB | No p99/GC-time delta → keep `-Xmx512m` default posture | X7 (OPT-22/23) |
| DX-8 | Minimal-extreme probe: D vs A-winner (late-stage, headroom-gated) | Same SQL semantics/mode | FULL SPEC-edge audit of hand parser first; error-budget + reconnect-rate gates | Loop-stall p99 under write burst, or any SPEC-edge miss → D closed permanently | High-risk (OPT-29..33) |

Order with blocking edges (inherited X-order, #7 §4): DX-1 + DX-2 first (independent SQL/pool)
→ DX-3 + DX-4 (need SQL/pool settled) → DX-5 (needs thread-blocking profile from DX-1..DX-4) →
DX-6 (needs pool shape from DX-2) → DX-7 (needs alloc profile) → DX-8 (headroom-gated, #9-cleared).
Explicitly NOT scheduled without profile indictment: OPT-24..OPT-28 tails (DX-side notes only),
OPT-34..OPT-44 (never).

### 5.9 #8/#10 flags

- #8 owns the runnable harness form; if it diverges from §0.6, DX rows survive (variables, fixed
  sets, gates, falsifiers are harness-agnostic) — #10 reconciles the method text.
- #10 synthesis input: this decision + DX outcomes (when run) + #8 method; open questions carried:
  C-live line review, D `Server`/`Jwt`/`Json` edge audit, Tomcat-vs-JDK-HttpServer 64k-idle sizing,
  FULL-vs-NORMAL cost (OPT-24 single probe).

## 6. Prohibited + quarantined inheritance (verbatim from #7 §§2.4/5)

- Gates enforced by code read per variant, no experiment ever: OPT-34 (response/query caches, table
  copies, remembered verifications), OPT-35 (`synchronous=OFF` / DELETE / MEMORY / in-memory DB),
  OPT-36 (unguarded batching — respond-before-commit; OPT-19/DX-6 is the only legal shape),
  OPT-37 (schema changes), OPT-38 (kernel tuning / CPU pinning / box changes), OPT-39 (JWT
  downgrade). All [VERIFIED prohibitions] — rule citations in catalog §2.4, reused.
- Quarantined figures — never quoted as targets: OPT-40 (prior-video 5.1k/10.25k/11.75k/14.05k —
  OUTDATED), OPT-41 (`~0.77 ms`/request — UNSUPPORTED), OPT-42 (PR33 `~20%`/38k/31.75k/interim —
  UNSUPPORTED), OPT-43 (PR58 laptop table/µs/CPU deltas — UNSUPPORTED for droplet), OPT-44 (`~32k`
  port threshold — mechanism PARTIALLY VERIFIED, threshold UNSUPPORTED). This doc quotes none of
  them as results (mentioned only to quarantine).
- Minor correction carried: the suite is `test/test.sh` (V21 INCORRECT-minor fixed; no `test/run.sh`
  citation anywhere here).

## 7. License posture (inherited #6 §7 / #7 §6 — API-verified, no new burden here)

- Repo `arjaythedev/twelve-dollar-server-challenge` MIT: study + re-implement ideas freely; verbatim
  copies keep the notice (C-pin shapes reused as reference, never merged — §2.3 oracle rule).
- Per-lane dependencies: A — xerial `sqlite-jdbc` Apache-2.0 (+ HikariCP Apache-2.0 / Jackson
  Apache-2.0 if kept); B — + Netty/Reactor Netty Apache-2.0 (manifest-noted); C — same Apache-2.0
  set, heaviest manifest; D — SQLite amalgamation public domain + Temurin JDK pin
  (GPLv2+CE — flag/build use clean, no JDK redistribution planned).
- Harness-only: k6 AGPLv3 (binary use, not linked), Nginx 2-clause BSD (config reference), JFR/OpenJDK
  vendor notes. This decision doc introduces no dependency — nothing to clear beyond inheritance.
- Unsafe/obsolete/undocumented tricks adopted here: none (DX-8 high-risk rows carry falsifiers +
  edge-audit gates, same guard as #6 §7).

## 8. Verification appendix (commands run this session)

- `git status` clean on `research/java-analysis`; `HEAD = main = origin/main =
  origin/research/java-analysis = 6523ba5`; `backup/main-baseline-2026-10-13 = 026bdb6` intact;
  `reference/spring-boot-pr33 = 7a9cb99` untouched (fresh `git status` + `git branch -a --verbose`
  + `git log --graph` + `git remote -v` before work; rev-parse re-check before writing).
- Precondition: `gh issue list --milestone Task-2-Research` shows #9 OPEN, #2/#3/#4/#5/#6/#7
  CLOSED, #8/#10/#11/#12 + Epic #1 OPEN untouched; `gh issue view 9` body read (scope source).
  If #9 had not been OPEN the session would have stopped (it was OPEN).
- Claimed #9 via `gh issue comment 9` before any work (parallel-session guard,
  `issues/9#issuecomment-6171844182`).
- Pin re-verification (read-only, this session): `git show …:application.properties` 6 lines, no
  `spring.threads.virtual` key; `git grep spring.threads` 0 hits (exit 1); `git grep -i webflux`
  0 hits (exit 1); `Application.java` pool/pragma block matches #4 §3.1 (Hikari 4/1, WAL/NORMAL/
  FK-on/busy-5000). Live-head numbers NOT re-fetched (owned by #4/#6 API evidence; no fetch into
  reference branch per global rules).
- Base refs read in full (§0.3 list); catalog counts re-verified by reading (11/17/5/6+5 = 44 rows;
  X1–X7 present; validated 0).
- Citation-coverage check: §3 holds 44 cells (11 axes × A/B/C/D) each with verdict + rationale;
  §3.1 evidence paragraphs cite SPEC/test/schema pins + prior-doc sections; every EXPERIMENTAL
  HYPOTHESIS names a DX experiment.
- No-benchmark-invented check: no suite/load executed; every numeric figure attributed to
  SPEC/threshold/transcript/author/laptop source with UNSUPPORTED/OUTDATED quarantine where due (§6).
- Classification check: every significant claim carries one of the six verdicts (§§1–7).
- License check: §7 inherits #6 §7 API-verified table; no new dependency introduced.
- Options-axes coverage check: A/B/C mandated + D evidence-gated (admission record §0.1/§2.4);
  11 ticket axes all present as §3 rows (SPEC compat, 1-vCPU, memory, HTTP concurrency, SQLite
  interaction, tail latency, complexity, observability, deploy, optimization potential,
  maintainability).
- Experiments-defined check: DX-1..DX-8 single-variable with variable/fixed/gates/falsifier +
  order with blocking edges + #8/#10 flags (§5/§5.9); every insufficient-evidence cell maps to one.
- Forbidden-word grep over draft + final file before commit — empty both times.
- `git add` ONLY `research/findings/JAVA_ARCHITECTURE_DECISION.md`; commit `docs(research):
  java_architecture_decision closes #9`; push `origin research/java-analysis`; ff-merge to `main`;
  push `main` (or STOP on non-ff).

*No implementation started. Next: #10 FINAL_RESEARCH_REPORT still waits on #8 + #9-state
(wiring comment on Epic #1); #8/#11/#12 untouched for their sessions.*
