# IMPLEMENTATION_ROADMAP — 13-phase Java build plan (Wayfinder-10/11, closes #11)

- Ticket: #11 `[Wayfinder-10/11] IMPLEMENTATION_ROADMAP -- phased build plan` (OPEN at writing time)
- Output: this file only (`docs/IMPLEMENTATION_ROADMAP.md`). No app code — planning doc only.
- Blocked by #10 FINAL_RESEARCH_REPORT (CLOSED @ `dba6448` — precondition met, see §8).
- #12 Verification is OPEN in a later session: this doc is its build-plan input. Nothing here starts implementation.
- Truth hierarchy observed throughout: `SPEC.md` + `test/test.sh` (42 checks) > transcript > AI reports > PR33/PR58 discussion > external.
- Every inherited factual claim below carries one of `VERIFIED` / `PARTIALLY VERIFIED` / `INCORRECT` / `UNSUPPORTED` / `OUTDATED` / `EXPERIMENTAL HYPOTHESIS` with its parent citation. Needs-experiment rows carry method, never a conclusion. No benchmarks invented (validated Java count stays 0). No new claims beyond Issues 1–8/#10 (new-claim count: 0).
- Plan discipline: one variable per experiment run; every phase measurable with falsifier/stop rule and rollback criterion; no invented targets (the only reference score is the agent's own Phase 7 measured baseline).

## 0. Method, skills, inputs, filters, parent-fidelity

### 0.1 Skills used (ticket mandate, in order)

Invoked via skill tool this session: `writing-plans` (bite-sized verifiable task shape per phase: objectives, tasks, deps, acceptance, verification, risks — applied as plan structure), `architecture-blueprint-generator` (lane/layer structure in §2: HTTP / concurrency / data / serialization / observability / deploy cuts per phase), `java-junit` (Phase 6 test architecture: `Test`-suffix classes, AAA, parameterized id/auth matrices, `@Tag` separation of suite-gate vs bench), `git-workflow` (single-file commit, ff-only bring-to-main, close), `verification-before-completion` (gate before every claim below and before commit/push/close).

Standing map notes skills — used or skipped with reason (none claimed unless invoked):
`grilling` skipped (scope fixed by #11 body + #10 hand-over; no open scoping questions),
`domain-modeling` skipped (no new domain terms — VU, closed-model, p95/p99, group commit, WAL, conntrack, drain all reused from #2–#9 with identical meaning),
`to-spec`/`to-tickets` skipped (output is the plan itself, not a new spec file or ticket set; §4 rows are verifiable slices, not new tickets),
`research`/`deep-research` satisfied through consolidation of closed docs only (no new discovery owned by #11),
`java-springboot` skipped (no Spring code judged; C-pin facts reused from #4 with SHA re-verification in §8),
`github` satisfied by standard linear ops verified with fresh `gh`/`git` commands per §8,
`documentation-writer` covered by `writing-plans` + `architecture-blueprint-generator` structure (second docs skill adds no shape).

### 0.2 Inputs read (in full, fresh this session)

- `docs/WAYFINDER_MAP_PROMPT.md` §4 (global hard rules) + §6 Issue 10 spec (phase shape reference).
- Issue #11 body + 1 comment (Depends: #10, now closed) + Epic #1 body (order + Decisions so far) — via fresh `gh issue view` / `gh issue list`.
- `research/findings/FINAL_RESEARCH_REPORT.md` (550 lines, #10 — §§6–9 read in full; whole file read).
- `research/findings/JAVA_ARCHITECTURE_DECISION.md` (419 lines, #9 — §§2–5 + DX-1..DX-8 read in full; whole file read).
- `research/findings/BENCHMARK_STRATEGY.md` (349 lines, #8 — §§1–7 read in full; whole file read).
- `research/findings/OPTIMIZATION_CATALOG.md` (138 lines, #7 — §§2–4, OPT-01..OPT-44 + X1–X7, read in full).
- `SPEC.md` (84 lines), `schema.sql` (26 lines), `bench/load.js` (70 lines), `test/test.sh` (126 lines), `docs/PROJECT_CONTEXT.md` — read in full.

### 0.3 Pins re-verified (fresh, this session — not trusted from parents)

- `research/java-analysis` = `main` = `dba6448`; `backup/main-baseline-2026-10-13` = `026bdb6` intact; `reference/spring-boot-pr33` = `7a9cb99` untouched (see §8).
- Issue states: #11 OPEN; #2–#10 CLOSED; #1 + #12 OPEN untouched (see §8).

### 0.4 Forbidden-word filter

Case-insensitive grep for the forbidden term (base prompt §4.4) run over the draft and again over the final file before commit — empty both times (see §8). Where that concept is needed, this doc writes `minimal-diff senior-dev mode`.

### 0.5 Parent-fidelity record (handed-over leads carried verbatim; deltas stated explicitly)

| Hand-over (session goal §3) | Disposition this session |
|---|---|
| Option A Plain Java baseline (Hikari-4 start, Nginx-first) | Adopted verbatim — §2 lane definition; every phase builds on A until DX-5 decides otherwise |
| C-pin `7a9cb99` as correctness oracle (never merge) | Adopted verbatim — §2 oracle rule + Phase 6 oracle diff |
| B profile-gated, D headroom-gated | Adopted verbatim — DX-5 gate (B) + DX-8 gate (D) scheduled in §§4.9–4.10/4.12 |
| DX-1..DX-8 execution order with blocking edges (DX-1+DX-2 → DX-3+DX-4 → DX-5 → DX-6 → DX-7 → DX-8) | Adopted with ONE stated scheduling delta: DX-6 (group commit, SQL-area) runs in Phase 8 right after DX-2 pool-shape settles, instead of after DX-5 — rationale: its only hard input is DX-2 pool shape; conditional re-confirmation rule covers a DX-5 lane change (see §4.8). All other hard edges preserved exactly |
| X1–X7 rank | Adopted verbatim — mapped per phase in §2/X-table and §4 |
| OPT-01..OPT-11 gates adopted; OPT-12..OPT-28 single-variable; OPT-29..OPT-33 headroom-gated; OPT-34..OPT-39 prohibited; OPT-40..OPT-44 quarantined (never targets) | Adopted verbatim — §§4/6 enforce per phase |
| ONE benchmark method (#10 §8: gate → S0–S4 → stats/J11 → EX-template) | Adopted verbatim, zero delta — §3 run book (open question Q9 closed by adoption) |
| 9 open questions from #10 §9 scheduled explicitly, nothing silently resolved | Scheduled — §5 maps Q1–Q9 to phase/owner/gate |

Total deltas forced by evidence: 0. Total scheduling deltas stated: 1 (DX-6 placement, above).

### 0.6 How to read this file

- §1 is the carried VERIFIED core the builder codes against (SPEC-exact shapes, no re-research needed).
- §2 is the lane decision + DX/X map (what runs where, in what order).
- §3 is the shared run book (copy-paste gate, k6 command, S0–S4, EX-template) — identical for every run.
- §4 is the 13 phases (each: consumes, objectives, tasks, deps, acceptance, verification, falsifier/stop, rollback, risks, recommended skills).
- §5 schedules the 9 open questions; §6 holds prohibitions + quarantines; §7 license posture; §8 verification appendix.

## 1. Ground truth snapshot (carried from #10 §§1–2 — code against this, do not re-derive)

All rows [VERIFIED] unless noted. Authority: `SPEC.md` + `test/test.sh`.

- **Box**: 1 vCPU (shared), 2 GB RAM, no swap, Ubuntu 24.04 x86_64. OOM = loss. No kernel tuning, no CPU pinning (in-process GC/thread/allocator/pragma settings fair game). (#10 §1/V1)
- **API**: 5 endpoints. `GET /health` → 200 `{"status":"ok","db":"ok","uptime_s":<int>}` after `SELECT 1` succeeds, else 503. `GET /feed` → 200 `{"posts":[<post> × 20]}` newest first `ORDER BY created_at DESC, id DESC`. `GET /posts/:id` → 200 `{"post":<post>}`. `POST /posts` (auth) → 201 with trimmed body, `like_count:0`. `POST /posts/:id/like` (auth) → 201 first / 200 repeat `{"liked":true,"already_liked":<bool>,"post_id":<int>}`. (#10 §1/V2/V5)
- **Post object**: `{"id":<int>,"body":<string>,"created_at":<string>,"author":<string>,"like_count":<int>}` — compact JSON, exact key order, `author` = `users.username`, `like_count` live per request, `created_at` stored-text passthrough (`strftime('%Y-%m-%dT%H:%M:%fZ','now')` default). (#10 §1/V2)
- **Auth**: `Authorization: Bearer <jwt>`, HS256-only, verify signature + `exp` every request; `sub` positive-integer string + `username` string or `invalid token payload`. Auth-before-id: `POST /posts/abc/like` without token → 401, with valid token → 400. (#10 §1/V3)
- **Create**: trim-before-validate; 500 code points after trim (code points, not UTF-16); malformed JSON → 400; schema `CHECK (length(body) BETWEEN 1 AND 500)`. First like 201 / repeat 200 / missing post 404 (app checks existence — SQLite FKs off by default). (#10 §1/V4/V5)
- **Schema/rules**: schema fixed (3 tables + 3 indexes, `schema.sql:1-2,24-26`); no cross-request result caches (prepared-statement/plan/state reuse allowed, result memoization forbidden); WAL + `synchronous=NORMAL`-or-stronger with commit-before-respond; suite entry is `test/test.sh` (42 checks, `passed/failed` gate `test/test.sh:125-126`; second run on same DB fails by design). (#10 §1/V6)
- **Bench shape**: k6 closed-model `ramping-vus` (60 s ramp → 5 min hold → 30 s down); VU = browsing loop (feed → think 3–7 s → get → think 3–8 s → 15 % like → 2 % post → idle 5–15 s); thresholds p95 < 500 ms, p99 < 1000 ms, errors < 1 %; score = max VUs passing a 5-min hold; k6 on a separate machine. (#10 §1/V7/V8)
- **C-pin**: `7a9cb99`, OPEN/unmerged/0 reviews, no benchmark numbers; MVC/Tomcat behind Nginx; virtual-threads flag absent [VERIFIED, re-verified §0.3-adjacent §8]; Hikari 4/1; keep-alive 70 s (satisfies ≥65 s rule); WAL + NORMAL + FK-on + busy 5 s; author-declared unoptimized baseline. (#10 §1/V9/V10)
- **Seed**: 50k users / 500k posts / 2M likes + 20k pre-signed tokens (`seed/tokens.json`). (#10 §1/V22)
- **Ceilings are not targets**: video 64k tie / conntrack ceiling / prior-video tables / laptop figures are quarantined context (never targets — §6). Validated Java bench count: 0. (#10 §§1/V23/V24, §10)

## 2. Architecture baseline + experiment map (carried from #10 §§6–8)

- **Build lane: Option A (Plain Java)** — JDK `HttpServer` + direct JDBC (xerial `sqlite-jdbc`), Hikari-4 start (explicitly not optimum), reference SQL + WAL/NORMAL + prepared-reuse + pragma gates, singleton mapper, Nginx-first then direct. Standing: [EXPERIMENTAL HYPOTHESIS as build baseline — first correct build + first profiled numbers, every sizing claim gated on §3 runs]. (#10 §7)
- **Correctness oracle: Option C-pin (`7a9cb99`)** — reuse shapes, never merge, MIT notice on verbatim copies. C-live (`845accd`) is a DX-5 comparison point only after line review (open question Q1). Building on C-pin is NOT the plan. (#10 §7)
- **Gated lanes**: Option B (Netty) runs only if a thread-blocking profile indicts threads (DX-5 gate); Option D (PR58 minimal extreme) only as a headroom-gated late probe with full edge audit (DX-8 gate). (#10 §7)
- **Baseline means**: first correct build + first profiled numbers + lane for DX-1..DX-4 — NOT vthreads-on (DX-4 decides), NOT direct-first (DX-3 decides), NOT group-commit-on (DX-6 decides; author-decline respected as default-off), NOT GC-tuned (DX-7 decides; `-Xmx512m` posture meanwhile). (#10 §7)

DX-1..DX-8 with X-rank (single-variable; variables, fixed sets, gates, falsifiers survive verbatim under §3):

| ID | Variable (one) | Runs in | Falsifier / stop rule | Maps to |
|---|---|---|---|---|
| DX-1 | Serialization: reference SQL vs per-row `json_object`+Java join vs `json_group_array` aggregate | Phase 8 | No alloc/p99 delta → keep reference query | X1 (OPT-12/13) |
| DX-2 | Writer placement: Hikari-4 vs 1W+3R split vs single Java writer queue | Phase 8 | Reader starvation or queue-p99 regression → keep pool-4 | X2 (OPT-15/16) |
| DX-6 | Group commit with wait-guard (+ atomic-like sub-row OPT-14) | Phase 8 (after DX-2 pool winner; §0.5 delta) | No write-p99 win at realistic mix, or atomicity cost → decline | X6 (OPT-19/14) |
| DX-4 | Threading flag: platform vs virtual threads | Phase 9 | No threads/pool-wait/p99 delta → platform stands | X5 (OPT-17) |
| DX-3 | Deploy mode: Nginx vs direct, same build | Phase 10 | Descriptor/idle-memory blowup or cap hit → stay behind Nginx | X3 (OPT-20) |
| DX-5 | Stack lane: A vs B vs C-live-shape, profile-gated | Phase 10 (after DX-3 mode winner; C-live line review Q1 first) | No thread-blocking in profile → A stands, B/C-live closed | X4 (OPT-18/21) |
| DX-7 | GC×heap: Serial/G1/Parallel/ZGC × 256/384/512 m + NMT | Phase 11 | No p99/GC-time delta → keep `-Xmx512m` default posture | X7 (OPT-22/23) |
| DX-8 | Minimal-extreme probe: D vs winner (late-stage, headroom-gated) | Phase 12 (D edge audit Q2 + headroom entry gate first) | Loop-stall p99 under write burst, or any SPEC-edge miss → D closed permanently | High-risk (OPT-29..33) |

Order with blocking edges: DX-1 + DX-2 → DX-6 (pool shape set) → DX-4 → DX-3 → DX-5 (thread-blocking profile from DX-1..DX-4 + mode winner) → DX-7 (alloc profile) → DX-8 (headroom-gated). NOT scheduled without profile indictment: OPT-24..OPT-28 tails (except single OPT-24 probe, §4.11); never: OPT-34..OPT-44 (§6). X-order honored: X1 + X2 first → X3 + X5 → X4 → X6 → X7 (X6 runs with X1/X2 in Phase 8 per §0.5 delta; re-confirmation rule in §4.8 covers a lane change).

## 3. Shared run book (ONE method — #10 §8 adopted verbatim, zero delta; Q9 closed by adoption)

Every bench or experiment run in §§4.7–4.12 executes these steps in order. Any run missing the gate line, the k6 command, or the fail+1 row is INCOMPLETE, not a result. Multi-variable optimization prohibited.

### 3.1 Correctness gate (copy-paste per run)

```bash
# 0. Fresh database per gate attempt (suite compares exact bytes with test/golden/*.json)
bash seed/make-seed.sh            # if a rebuild is needed (50k users / 500k posts / 2M likes)
rm -f /tmp/bench.db && cp seed/feed.db /tmp/bench.db

# 1. Start THIS build on the fresh copy (example: Nginx mode)
SQLITE_PATH=/tmp/bench.db JWT_SECRET=twelve-dollar-challenge HOST=127.0.0.1 PORT=3000 ./start.sh &
SRV=$!; sleep 3

# 2. Gate (42 checks). Must print failed=0.
bash test/test.sh http://127.0.0.1:3000; echo "suite_exit=$?"
# Expected tail: passed=42 failed=0 (shape: test/test.sh:125-126 prints passed/failed; 42-check count per README + SPEC hierarchy)

# 3. Record the gate line in the run log (required field): suite SHA, DB copy md5, pass line.
kill $SRV
```

Gate proves byte-exactness (goldens, auth/id matrices, read-back); it does NOT prove performance — green gate is permission to bench, not a score. Every single-variable change re-runs this full gate. (Carried #10 §8; OPT-01.)

### 3.2 k6 invocation (copy-paste; VUS is the only standard variable)

```bash
k6 run -e VUS=2500 -e BASE_URL=http://127.0.0.1:3000 bench/load.js
# Variants: -e DURATION=5m -e RAMP_UP=60s -e TOKENS=seed/tokens.json (defaults shown; log any override)
```

Stages pinned (60 s ramp → 5 min hold → 30 s down, `gracefulRampDown 10s`); thresholds p95<500 / p99<1000 / err<1 % (all three green for the full hold); VU mix + think/idle + 15 %/2 % probabilities pinned; k6 on a SEPARATE machine; same-harness comparisons only (coordinated-omission caveat). (Carried #10 §8; `bench/load.js:11-43`.)

### 3.3 Limit-search S0–S4 + stop rules

S0 warm-up 1000/2 min (unscored, setup-bug detector) → S1 probe 2500 doubling to fail → S2 narrow ±250 → S3 confirm with ONE full 5-min hold run TWICE at the score candidate (both green; report spread, not the better half) → S4 step-down 250 on fail. Stop: green-N + red-N+250 → score N; generator/conntrack-tainted reds are INVALID (fix harness, re-run, never score); three clean reds 250 apart → highest green below. No VU number here is a Java expectation. (Carried #10 §8.)

### 3.4 Per-level log table (required — J11: pass AND fail+1 rows)

| VUs | k6 avg/med/p95/p99/max | err% | k6 exit | srv CPU% | srv RSS/free | gen CPU/RAM | mode (nginx/direct) | gate (suite line) | verdict |
|---|---|---|---|---|---|---|---|---|---|
| 1000 (S0) | … | … | … | … | … | … | … | `passed=42 failed=0` | warmup |
| N (S3) | … | … | 0 | … | … | … | … | same build | PASS |
| N+250 | … | … | ≠0 | … | … | … | … | same build | FAIL (limit) |

Plus per hold: accept queue / `EMFILE` / eviction-reconnect counts, wall-clock alignment, JFR on/off + artifact path, drain-wait record, pragma declaration (`journal_mode / synchronous / foreign_keys / busy_timeout / cache_size / mmap_size / autocheckpoint-schedule` + NORMAL asterisk note). JFR stays on per run (cap + rotate) with one JFR-off falsifier per lane. (Carried #10 §8.)

### 3.5 Bottleneck-ID order + falsifiers (ask first on every ceiling)

1. Generator-vs-server (§6.2 sequence: saturation → INVALID; port-drain → INVALID; idle-server ceiling → harness artifact). 2. Conntrack-vs-app above ~60k (full table/drops → platform ceiling, stop scoring upward). 3. App-internal profile order: SQL → pool/queue → HTTP/conn → stack/threading (E-stack gate) → JSON/auth alloc last. (Carried #10 §8.)

### 3.6 EX-template (copy-paste per run)

```markdown
## EX-<id>: <one-line variable>
- Variable (ONE): <e.g. pool 4 → 1W+3R>
- Fixed: <commit SHA + SQL + pragmas + pool-except-variable + stack + JVM flags + mode + VUS plan>
- Gates per run: `test/test.sh` 100% on fresh DB (paste: passed=__ failed=__) + byte-compare + rule 5/6 code-read (caches/durability/schema/JWT) + pragma assertions
- Harness: S0–S4 (§3.3) + k6 invocation (paste full command) + generator count + drain-wait + JFR on/off
- Report: per-level table (§3.4: p95/p99/err + CPU + RSS at pass AND fail+1) + profile artifact path
- Falsifier / stop rule: <condition under which the row closes and the baseline stands>
- Maps to: <DX-n / X-n / OPT-nn>
- Verdict: <candidate held | baseline stands | needs rerun (harness artifact §3.5)>
```

## 4. The 13 phases

Conventions: `Consumes` cites the #10 section + OPT/DX rows (do not re-derive). `Verification` names the exact commands/logs. Every experiment phase runs under §3 with its EX rows. `Recommended skills` are for the future implementation agent.

### Phase 1 — Init (scaffold, pins, submission skeleton)

- **Consumes**: #10 §§1/7/11 (ground truth, lane A, license posture); OPT-01/02/09 (gate/durability/harness posture).
- **Objectives**: pinned toolchain, submission skeleton outside `research/`+`docs/`, `install.sh`/`build.sh`/`start.sh`/`README.md` placeholders per challenge rules, git hygiene.
- **Tasks**: (1) choose submission dir + JDK distribution/version pin (record version string + checksum; Java 21+ required for virtual threads; both reference lanes use Java 25 — verify at pin time, do not assume); (2) pin xerial `sqlite-jdbc` + (HikariCP/Jackson only if kept — minimal-diff senior-dev mode: smallest set that serves Phase 2–6); (3) skeleton `install.sh`/`build.sh`/`start.sh` honoring `SQLITE_PATH`/`JWT_SECRET`/`HOST`/`PORT` + ≤60 s start; (4) `.gitignore` for build artifacts/DB copies (never commit `feed.db` copies or JFR dumps).
- **Deps**: none (first).
- **Acceptance**: skeleton starts, serves nothing yet (or static 404 `{"error":"not found"}`), pins recorded in README with checksums.
- **Verification**: `./build.sh && ./start.sh` on the box; `git status` shows no artifacts; pins re-printable via one command.
- **Falsifier/stop**: build-from-source fails on the box → fix pins/scripts, no forward movement until green.
- **Rollback**: re-pin to last building set; record the failed pin as rejected with reason.
- **Risks**: unpinned transitive deps drift (mitigate: lockfile + checksums); committing DB/JFR artifacts (mitigate: `.gitignore` + `git status` check per commit).
- **Recommended skills**: `git-workflow`, `verification-before-completion`.

### Phase 2 — HTTP baseline (JDK HttpServer, routing skeleton, keep-alive)

- **Consumes**: #10 §§1 (SPEC envelope), 7 (lane A HTTP cut); OPT-08 (Nginx-first); E-edge envelope.
- **Objectives**: blocking-handler JDK `HttpServer` behind Nginx (`127.0.0.1:3000`), 5-path router returning SPEC-shaped stubs, idle ≥65 s (reference: 70 s satisfies the rule [VERIFIED pin fact]).
- **Tasks**: (1) server bootstrap + router for the 5 paths + 404 `{"error":"not found"}` fallback; (2) keep-alive/idle sizing for Nginx 64-idle envelope; (3) request logging hooks for §3.4 accept-queue/`EMFILE`/eviction fields; (4) `LimitNOFILE`/backlog sizing notes for later direct mode (no direct serving yet).
- **Deps**: Phase 1 (skeleton + start path).
- **Acceptance**: unknown path → 404 exact bytes; `GET /health` stub responds (DB check lands in Phase 3); idle conn survives ≥65 s behind Nginx config.
- **Verification**: `curl` byte checks vs SPEC strings; Nginx-upstream smoke run; idle-hold timing log.
- **Falsifier/stop**: JDK server cannot hold 64 Nginx-idle conns at ≥65 s without RSS blowup → record, stay the course only if bounded; envelope questions feed DX-3 (Phase 10), not this phase.
- **Rollback**: keep-alive/idle values revert to last stable; router changes are additive-only.
- **Risks**: framework-shaped over-engineering (mitigate: stdlib only, minimal-diff senior-dev mode); premature direct-mode tuning (mitigate: forbidden — DX-3 owns it).
- **Recommended skills**: `systematic-debugging`, `verification-before-completion`.

### Phase 3 — SQLite (WAL floor, per-conn pragmas, prepare-once, reference SQL)

- **Consumes**: #10 §§1 (V6 durability), 6 (OPT-02/06/07), 8 (pragma assertions); C1 boundary (NORMAL asterisk).
- **Objectives**: WAL + `synchronous=NORMAL` + FK-on + `busy_timeout=5000` on EVERY pooled conn with startup assertions; Hikari-4 start (explicitly not optimum); prepare-once-per-conn posture; SPEC reference SQL verbatim (feed correlated-count + `ORDER BY created_at DESC, id DESC LIMIT 20` on `posts_created_at_id_idx`; `INSERT ... RETURNING`; like upsert).
- **Tasks**: (1) pool wiring + per-conn pragma block + assertion query on all conns; (2) prepared-statement lifecycle (prepare at startup, reset+rebind per use); (3) `health` `SELECT 1` → 200/503 shapes; (4) `EXPLAIN QUERY PLAN` capture on fresh + grown data (baseline artifact for DX-1).
- **Deps**: Phase 2 (server to mount DB layer).
- **Acceptance**: pragma assertions pass on all 4 conns; `GET /health` live-DB 200; plan artifact stored.
- **Verification**: assertion log per conn; `EXPLAIN QUERY PLAN` outputs archived; NORMAL asterisk noted in run log (valid floor, weaker than FULL on power loss — document, never claim FULL).
- **Falsifier/stop**: pragma will not hold per-conn (session-only reversion) → fix wiring (set per checkout), never proceed on assumed pragmas.
- **Rollback**: reference SQL is the immutable fallback for every later SQL experiment.
- **Risks**: `cache_size`-class session-only drift (mitigate: per-variant re-assertion, carried into every EX row); pool-4 assumed optimal (mitigate: explicitly labeled start; DX-2 owns sizing).
- **Recommended skills**: `systematic-debugging`, `verification-before-completion`.

### Phase 4 — API (5 endpoints, byte-exact JSON, error table)

- **Consumes**: #10 §§1 (V2/V4/V5 shapes), 7 (SPEC-exact principle); OPT-04/05 (auth-before-id order, byte-exactness); C-pin oracle shapes.
- **Objectives**: all 5 endpoints with compact JSON, exact key order, stored-`created_at` passthrough, live `like_count`, full error table (`invalid post id` / `post not found` / `body is required` / `body must be at most 500 characters` / `malformed JSON body` / `internal server error`), trim-before-validate with code-point counting, like 201/200 + existence check, feed-20 + top-of-feed.
- **Tasks**: (1) handlers + JSON writer with key-order discipline (singleton mapper or equivalent — OPT-11 adoption gated on Phase 11 profile, use simplest correct shape now); (2) id validation matrix (`0`,`-1`,`abc`,`1.5` → 400); (3) create validation matrix (empty/whitespace/numeric/missing/malformed/501-char); (4) like existence + idempotency (8-way concurrent-like check shape borrowed from C-pin `ApiTest` idea, re-implemented not copied without notice).
- **Deps**: Phase 3 (DB layer + reference SQL).
- **Acceptance**: suite gate line green on fresh DB — `bash test/test.sh http://127.0.0.1:3000` → `passed=42 failed=0`.
- **Verification**: gate line pasted in log + DB md5 + suite SHA; byte-diff of goldens on any serializer touch.
- **Falsifier/stop**: any golden/auth/id mismatch → fix, no bench until `failed=0` (OPT-01 absolute).
- **Rollback**: per-handler revert; goldens are the arbiter, never the code.
- **Risks**: UTF-16 vs code-point length bugs (mitigate: suite 501-char + special-char round-trip checks); `like_count` staleness (mitigate: read-back checks `test/test.sh:114-118`).
- **Recommended skills**: `java-junit`, `verification-before-completion`.

### Phase 5 — JWT (HS256 per-request verify, three 401 shapes, auth-first router)

- **Consumes**: #10 §1 (V3 auth); OPT-03/04 (per-request re-verify, auth-before-id); pin `Authentication` shapes.
- **Objectives**: HS256-only verification (signature + `exp`) on every authed request; `missing bearer token` / `invalid or expired token` / `invalid token payload` exact shapes; auth-before-id ordering on `POST /posts/abc/like` (401 without token, 400 with valid token); no remembered verifications (rule 5).
- **Tasks**: (1) verifier (HMAC-SHA256, constant-time compare, `alg` rejection incl. `none`, `exp`/`sub`/`username` checks); (2) router ordering auth-first on all authed routes; (3) per-request cost baseline note (pays full HMAC per request — compliance price, no caching workaround ever).
- **Deps**: Phase 4 (authed endpoints exist).
- **Acceptance**: suite auth matrix green — same gate line `bash test/test.sh http://127.0.0.1:3000` → `passed=42 failed=0` (auth section `test/test.sh:68-83,113`).
- **Verification**: gate line + targeted `curl` matrix (no-token/Basic/garbage/bad-sig/`none`/expired/bad-sub/no-name/auth-before-id).
- **Falsifier/stop**: any 401-shape mismatch → fix verifier/router, no bench until green.
- **Rollback**: verifier is single-module revert; ordering change is router-only.
- **Risks**: JWT downgrade temptation under load (mitigate: OPT-39 prohibited — code-read gate per variant forever); `ThreadLocal<Mac>` sharing bugs (mitigate: OPT-11 safe shapes only after Phase 11 profile; simplest correct shape now).
- **Recommended skills**: `java-junit`, `verification-before-completion`.

### Phase 6 — Correctness (full gate, oracle diff, JUnit harness, bench permission)

- **Consumes**: #10 §§1, 7 (oracle rule), 8 (gate); OPT-01 (gate), OPT-05 (goldens); C-pin `7a9cb99` oracle.
- **Objectives**: 100 % suite on fresh DB as the standing pre-bench permission; JUnit harness mirroring the suite's matrices (golden reads, health/404/id, auth, create validation, like/read-back/feed-20); C-pin shape diff (byte-compare sample responses against oracle behavior; reuse shapes with MIT notice, never merge).
- **Tasks**: (1) JUnit suite: `*Test` classes, AAA, `@ParameterizedTest` (`@ValueSource`/`@CsvSource`) for id + auth matrices, `@Tag("gate")` vs `@Tag("bench")` separation (`mvn test` / `gradle test` runs gate tags); (2) fresh-DB gate discipline scripted (rebuild → copy → start → gate → record → kill, §3.1); (3) rule 5/6 code-read checklist v1 (no caches/copies, WAL+NORMAL, commit-before-respond, schema diff empty, JWT strict) — signed per build; (4) oracle spot-diff log.
- **Deps**: Phases 4–5 (full API + auth).
- **Acceptance**: `bash test/test.sh http://127.0.0.1:3000` → `passed=42 failed=0` on fresh DB + JUnit gate tags green + code-read checklist signed.
- **Verification**: gate line + JUnit report + checklist in the build log; second-run-on-same-DB fails golden (by design — proves freshness discipline).
- **Falsifier/stop**: gate red → no bench, ever (OPT-01 absolute; Phase 7+ blocked).
- **Rollback**: N/A (gate phase) — failing builds do not advance.
- **Risks**: stale-DB false green/red (mitigate: md5-recorded fresh copy per gate); oracle drift confusion (mitigate: pin-cited `7a9cb99` only, never live head).
- **Recommended skills**: `java-junit`, `git-workflow`, `verification-before-completion`.

### Phase 7 — Initial bench (first S0–S4 baseline, J11 table, harness falsifiers)

- **Consumes**: #10 §8 (ONE method: gate → S0–S4 → stats/J11 → EX-template); OPT-09 (harness + drain); J11 reporting policy.
- **Objectives**: first measured limit-search on the Phase 6-correct build (lane A, Hikari-4, Nginx-first, reference SQL, platform threads, `-Xmx512m` posture); per-level J11 table with pass AND fail+1 rows; generator + drain + conntrack falsifier drills exercised at least once.
- **Tasks**: (1) gate (§3.1) → S0 → S1 → S2 → S3×2 → record (§3.3); (2) k6 command pasted: `k6 run -e VUS=<n> -e BASE_URL=http://127.0.0.1:3000 bench/load.js` (k6 separate machine); (3) per-level table (§3.4) with CPU/RSS/generator columns; (4) run §3.5 falsifier sequence on the first ceiling (record generator headroom, drain-wait, conntrack occupancy even if below ~60k — drill the habit); (5) JFR artifact + `EXPLAIN QUERY PLAN` (fresh+grown) archived as the profile baseline for DX-5/DX-7.
- **Deps**: Phase 6 (bench permission).
- **Acceptance**: gate line `passed=42 failed=0` + k6 command pasted + per-level table with N (PASS) and N+250 (FAIL) rows + falsifier log entries. The measured N is the reference every later phase must beat — NOT a target met, just the baseline.
- **Verification**: §3.4 table completeness check (missing gate line / k6 command / fail+1 row = INCOMPLETE); profile artifacts paths recorded.
- **Falsifier/stop**: ceiling with generator saturation / drain shape / idle server → INVALID, fix harness, re-run (never score the red); S0 fail → setup bug, fix before S1.
- **Rollback**: N/A (measurement phase) — baseline stands until a later EX row beats it with all gates green.
- **Risks**: scoring a generator artifact as a server result (mitigate: §3.5 step 1 mandatory); run-to-run spread misread (mitigate: S3 doubled, report spread); quoting N as a Java expectation elsewhere (mitigate: §6 quarantine discipline — N is internal reference only).
- **Recommended skills**: `systematic-debugging`, `verification-before-completion`.

### Phase 8 — SQLite exps (DX-1 + DX-2 + DX-6; X1 + X2 + X6)

- **Consumes**: #10 §§6 (X1/X2/X6, OPT-12..16/19/14), 7/8 (DX-1/DX-2/DX-6 rows, EX-template); J2/J3 lessons.
- **Objectives**: settle serialization shape, writer placement, and write-path batching — each single-variable under §3, in this order: DX-1 → DX-2 → DX-6.
- **Tasks**: EX-1 (DX-1: reference SQL vs per-row `json_object`+Java join vs `json_group_array`; gates: OPT-05 goldens + byte compare + alloc profile + `ORDER BY created_at DESC, id DESC` equality proof); EX-2 (DX-2: Hikari-4 vs 1W+3R vs single Java writer queue; gates: commit-before-respond re-assert + busy-timeout expirations + queue-time + write p99); EX-3 (DX-6: wait-guarded group commit on the DX-2 winner + OPT-14 atomic-like sub-row; gates: realistic + write-heavy mixes, 404/200/201 + idempotency, failure-atomicity design).
- **Deps**: Phase 7 (baseline + profile artifacts); EX-3 needs EX-2 pool winner.
- **Acceptance** (per EX row): gate line `bash test/test.sh http://127.0.0.1:3000` → `passed=42 failed=0` + k6 command `k6 run -e VUS=<n> -e BASE_URL=http://127.0.0.1:3000 bench/load.js` pasted + per-level table with pass AND fail+1 rows + verdict (candidate held | baseline stands | rerun).
- **Verification**: EX-template §3.6 completeness per row; `EXPLAIN QUERY PLAN` fresh+grown per SQL change; pragma + durability declaration per run (§3.4).
- **Falsifiers/stop**: EX-1: no alloc/p99 delta → keep reference query. EX-2: reader starvation or queue-p99 regression → keep pool-4. EX-3: no write-p99 win at realistic mix, or atomicity cost → decline (author default respected).
- **Rollback** (per row): reference query / pool-4 / ungrouped single-statement commits; one-revert-per-row, re-gate after revert. Lane-change rule (§0.5 delta cover): if DX-5 (Phase 10) dethrones lane A, re-run the EX-3 winning shape once in the new lane as a confirmation row before adopting.
- **Risks**: multi-variable SQL+pool+commit changes in one build (mitigate: prohibited — one EX row per build); `like_count`-as-of-request or order breakage in rewrites (mitigate: golden + read-back + grown-data plan gates); group-commit failure atomicity (mitigate: designed + tested before bench).
- **Recommended skills**: `systematic-debugging`, `verification-before-completion`.

### Phase 9 — Concurrency exps (DX-4; X5 + thread-blocking profile for DX-5)

- **Consumes**: #10 §§6 (X5, OPT-17), 7/8 (DX-4 row); V11/E-vt (vthreads claims UNSUPPORTED — needs experiment).
- **Objectives**: cheapest toggle in the tree (one flag, clear counters); consolidate the JFR thread-blocking profile DX-5 needs (pool-wait + allocation counters from Phases 7–9).
- **Tasks**: EX-4 (DX-4: platform vs virtual threads, identical SQL/pragmas/pool; A-lane; C-lane repeats once only if cheap); counters: live threads, pool-wait histograms, p99 spread; profile consolidation note (thread-blocking: yes/no + evidence pointers).
- **Deps**: Phase 8 (SQL/pool settled — clean attribution).
- **Acceptance**: gate line + k6 command (`k6 run -e VUS=<n> -e BASE_URL=http://127.0.0.1:3000 bench/load.js`) + per-level pass AND fail+1 table + verdict per EX row.
- **Verification**: EX-template completeness; thread/pool-wait counter logs archived; profile note written (feeds Phase 10 DX-5 gate).
- **Falsifier/stop**: no threads/pool-wait/p99 delta → platform stands, row closed.
- **Rollback**: flag revert to platform threads; re-gate.
- **Risks**: unbounded thread growth masking pool saturation (mitigate: counters, not just latency); virtual-thread pinning on synchronized/JNI paths (mitigate: profile first, flag second).
- **Recommended skills**: `systematic-debugging`, `verification-before-completion`.

### Phase 10 — HTTP/net exps (DX-3 + DX-5; X3 + X4; C-live line review Q1 first)

- **Consumes**: #10 §§6 (X3/X4, OPT-18/20/21), 7/8 (DX-3/DX-5 rows), 9 (Q1 C-live review, Q3 64k sizing); V13 (Nginx CPU PARTIALLY VERIFIED); J1/J5/J10 lessons.
- **Objectives**: settle deploy mode, then the biggest structural decision — profile-gated, never pre-decided. Entry gate: Q1 C-live line review complete (auth-before-id, error table, key order, `created_at` passthrough, commit-before-respond re-proved on the WebFlux tree; until then every C-live figure stays UNSUPPORTED).
- **Tasks**: EX-5 (DX-3: Nginx vs direct, SAME build; gates: accept queue, `EMFILE`, eviction rate, p99 vs CPU; 65 s idle rule both modes; Q3 sizing counters: descriptor/idle-memory at ~15k sustained); EX-6 (DX-5: A vs B vs C-live-shape in the EX-5 winning mode with DX-1..DX-4 winners fixed; gates: JFR allocation + pool-wait counters; C-live shapes line-reviewed BEFORE the run).
- **Deps**: Phase 9 (thread profile for the DX-5 gate); EX-6 needs EX-5 mode winner + Q1 review.
- **Acceptance**: gate line + k6 command per row + per-level pass AND fail+1 tables + verdicts; deploy mode recorded as the fixed shape for everything after.
- **Verification**: EX-template completeness; Q1 review checklist signed before EX-6; eviction/`EMFILE` logs per mode.
- **Falsifiers/stop**: EX-5: descriptor/idle-memory blowup or cap hit → stay behind Nginx. EX-6: no thread-blocking in profile → A stands, B/C-live closed permanently for this box.
- **Rollback**: mode revert to Nginx-first; lane revert to A with DX-1..DX-4 winners intact; DX-6 re-confirmation row if lane changed (see §4.8).
- **Risks**: direct-mode `EMFILE`/OOM at idle scale (mitigate: sized backlog + `LimitNOFILE=65535` accounting from Phase 2 notes); WebFlux unreviewed-edge inheritance (mitigate: Q1 gate absolute); pre-deciding the lane for style (mitigate: prohibited — profile decides).
- **Recommended skills**: `systematic-debugging`, `code-review`, `verification-before-completion`.

### Phase 11 — JVM tuning (DX-7; X7 + alloc tails + OPT-24 single probe + Q4/Q7)

- **Consumes**: #10 §§6 (X7, OPT-22/23/11/24/25/26/27), 7/8 (DX-7 row), 9 (Q4 FULL-vs-NORMAL, Q7 cache-shrink transfer); D1/JFR bound PARTIALLY VERIFIED.
- **Objectives**: collector×heap winner inside 2 GB (SQLite+Nginx resident); alloc-profile verdict on OPT-11/OPT-23; ONE FULL-vs-NORMAL cost probe then back; profile-indicted cache tails only.
- **Tasks**: EX-7 (DX-7: Serial/G1/Parallel/ZGC × 256/384/512 m + NMT; gates: p99 + GC logs + native-mem; stay well under 2 GB); EX-8 (OPT-23 alloc flame AFTER SQL ruled out → adopt/decline OPT-11 singleton/Pattern/`ThreadLocal<Mac>`+`remove()` shapes); EX-9 (OPT-24: single FULL run, then back to NORMAL — owner decision required for any direction change, never silent — Q4); EX-10 only on profile indictment (OPT-25 checkpoint schedule with cap; OPT-26 `cache_size`/`journal_size_limit` one knob per run; OPT-27 `mmap_size` LAST — Q7 stays EXPERIMENTAL HYPOTHESIS until native-mem measured).
- **Deps**: Phase 10 (winning lane + mode fixed — tuning applies to the winner).
- **Acceptance**: gate line + k6 command per row + per-level pass AND fail+1 tables + verdicts; JVM flags + NMT note recorded as build posture.
- **Verification**: EX-template completeness; GC/native-mem logs archived; FULL-probe run explicitly labeled (never the standing posture).
- **Falsifiers/stop**: EX-7: no p99/GC-time delta → keep `-Xmx512m` default posture. EX-8: SQL/pool dominates → decline hand-tuning. EX-9: informational only. EX-10: any unbounded WAL growth / RSS pressure → revert knob.
- **Rollback**: flags revert to `-Xmx512m` default-collector posture; knob reverts per pooled-conn re-assertion.
- **Risks**: Serial stop-the-world on alloc spikes (mitigate: judge p99, not mean); oversized page cache stealing from heap/OS/Nginx (mitigate: resident-aware accounting); silent durability upgrade (mitigate: prohibited — NORMAL standing unless owner decides).
- **Recommended skills**: `systematic-debugging`, `verification-before-completion`.

### Phase 12 — Final bench/deploy (DX-8 headroom-gated probe + S0–S4 confirms + deploy)

- **Consumes**: #10 §§6 (OPT-29..33 high-risk), 7/8 (DX-8 row), 9 (Q2 D edge audit, Q5 generator falsifier, Q6 conntrack falsifier); V24 (headroom tiebreak J11).
- **Objectives**: headroom-gated D probe (ONLY if DX-1..DX-7 landed and headroom analysis + owner decision say a ceiling is worth chasing); final S0–S4 confirms (S3×2) on the ultimate build; deploy shape (app + Nginx pin, `install.sh`/`build.sh`/`start.sh` from-source-on-box, ≤60 s start).
- **Tasks**: entry gates first — (a) Q2 D `Server`/`Jwt`/`Json` edge audit: hand parser re-proves EVERY SPEC edge (auth-before-id, id matrix, malformed JSON, 404 shapes, key order, trim/500-code-point); any miss closes D permanently; (b) headroom entry analysis (p99 + CPU + RSS at the Phase 11 ceiling — is there a chase-worthy gap?); then EX-11 (DX-8: D vs winner, same SQL semantics/mode; gates: error-budget + reconnect-rate); then final S0–S4 ×2 + deploy-script hardening + drain/conntrack drill at the highest level reached (Q5/Q6 evidence logged even below ~60k).
- **Deps**: Phase 11 (tuned winner); EX-11 needs Q2 audit + headroom gate.
- **Acceptance**: gate line + k6 command + per-level pass AND fail+1 tables for EX-11 (if run) AND the final confirms; deploy scripts build-from-source on a clean box; score = highest VUs with green 5-min holds (internal reference, reported with spread + headroom per J11).
- **Verification**: EX-template completeness; audit checklist signed (or D-closed record); §3.5 falsifier logs on the final ceiling; from-source rebuild proof.
- **Falsifiers/stop**: EX-11: loop-stall p99 under write burst, or any SPEC-edge miss, or error-budget breach → D closed permanently, winner stands. Ceiling with conntrack-full/drops → platform ceiling, stop scoring upward.
- **Rollback**: winner build is untouched by the probe (separate lane); deploy reverts to last confirmed scripts.
- **Risks**: RST-churn vs <1 % budget (mitigate: reconnect-rate gate, OPT-32 uncertain honored); `-march=native` non-portability (mitigate: rebuild-from-pin per stepping, direct-only evidence labeled); probe corrupting the winner (mitigate: probe never touches the winning lane's branch state).
- **Recommended skills**: `systematic-debugging`, `git-workflow`, `verification-before-completion`.

### Phase 13 — Docs/submission (README, hygiene, quarantine discipline, STOP-ready)

- **Consumes**: #10 §§9 (Q8 caption-slip, Q9 reconciliation), 10 (quarantines), 11 (license posture); challenge submission rules.
- **Objectives**: submission that passes rules without the author present: README (build/bench/repro), `install.sh`/`build.sh`/`start.sh`, pinned versions + checksums, rule 5/6 compliance statement with NORMAL asterisk, no quarantined figure quoted anywhere (Q8: never quote 3,300/3,900; re-check audio before citing C#/Fortran ceilings — this doc never quotes them as results), harness-reconciliation note (adopts #10 §8 verbatim, delta: none — Q9), license notices (MIT upstream; Apache-2.0 deps manifest-noted; verbatim C-pin-derived shapes keep notice).
- **Tasks**: (1) README + scripts final pass; (2) quarantine grep (OPT-40..44 figures absent except as quarantines); (3) license manifest; (4) final fresh-DB gate + one S3 confirm on the submitted artifact; (5) STOP handoff note for #12 (what was measured, what stands, what stays open).
- **Deps**: Phase 12 (final build + score + deploy scripts).
- **Acceptance**: checklist — gate green on submitted artifact; docs complete; quarantine grep empty; license manifest present; no app-code change in this phase (docs/scripts only).
- **Verification**: gate line + grep outputs pasted; from-source rebuild + gate on clean checkout.
- **Falsifier/stop**: any gate red or quarantine hit → fix docs/scripts only (no lane changes without reopening the owning phase).
- **Rollback**: docs-only reverts.
- **Risks**: last-minute "one small optimization" (mitigate: prohibited — STOP gate; new ideas go to a future ticket with their EX row); quoting a quarantined number in README (mitigate: §6 list as the pre-submit checklist).
- **Recommended skills**: `documentation-writer`, `git-workflow`, `verification-before-completion`.

## 5. Open-question schedule (carried from #10 §9 — nothing silently resolved; #12 confirms)

| # | Question | Owner (phase) | Gate / falsifier |
|---|---|---|---|
| Q1 | C-live line review (`845accd` intent-only, no line review exists) | Phase 10 entry gate (before EX-6) | Auth-before-id, error table, key order, `created_at` passthrough, commit-before-respond re-proved on WebFlux tree; until then C-live figures stay UNSUPPORTED (OPT-42) |
| Q2 | D `Server`/`Jwt`/`Json` edge audit (never line-audited) | Phase 12 entry gate (before EX-11) | FULL SPEC-edge re-proof; any miss closes D permanently; until then OPT-29/30/32 stay `uncertain` |
| Q3 | JDK-HttpServer vs Tomcat 64k-idle sizing (no evidence) | Phase 10 EX-5 (E-edge counters) | Accept-queue/`EMFILE`/eviction/idle-memory; blowup keeps the build behind Nginx |
| Q4 | FULL-vs-NORMAL single cost probe (margin never measured) | Phase 11 EX-9 (OPT-24, one run then back) | Owner decision required for any direction change — never silent |
| Q5 | Generator-vs-server falsifier on every ceiling | Every EX row (§§4.7–4.12, §3.5) | Saturation/drain/idle-server → INVALID, fix harness, re-run, never score |
| Q6 | Conntrack falsifier above ~60k | Any ceiling above ~60k (Phase 12 final) | Full table/drops → platform observation, not an app score |
| Q7 | C++-style cache-shrink legality transfer (JVM RSS accounting differs) | Phase 11 EX-10 (OPT-26/27, native-mem tracked) | Until measured, `mmap_size` direction stays EXPERIMENTAL HYPOTHESIS |
| Q8 | Caption-slip magnitudes ("3,300"/"3,900" coherent only as 33,000/39,000) | Phase 13 docs (anyone quoting C#/Fortran ceilings) | Re-check audio first; this plan never quotes either figure as a result |
| Q9 | Harness-form reconciliation (#10 §8 owns the runnable form) | This doc adopts §3 verbatim, delta: none | Any later change states its delta in the #10 §9-shape table — no silent split; #12 verifies |

## 6. Prohibited + quarantined inheritance (verbatim IDs from #10 §10 — gates enforced by code read per variant)

Prohibited — no experiment ever (all [VERIFIED prohibitions]): OPT-34 response/query caches, table copies, remembered JWT verifications (rule 5) · OPT-35 `synchronous=OFF` / `journal_mode=DELETE` / MEMORY / in-memory DB (rule 6 floor) · OPT-36 unguarded batching — respond-before-commit (OPT-19/DX-6 is the only legal shape) · OPT-37 schema changes (tables/columns/indexes/triggers) · OPT-38 kernel tuning / CPU pinning / box changes (in-process settings stay fair game) · OPT-39 JWT downgrade (skip verify, non-HS256, ignore `exp`/`sub`/`username`).

Quarantined figures — never quoted as targets (mentioned only to quarantine): OPT-40 prior-video table as Java targets [OUTDATED] · OPT-41 `~0.77 ms` CPU/request budget [UNSUPPORTED] · OPT-42 PR33 `~20%` / 38k-vs-31.75k / interim remarks [UNSUPPORTED] · OPT-43 PR58 laptop table as droplet figures [UNSUPPORTED as droplet figures] · OPT-44 `~32k` port threshold as universal constant [mechanism PARTIALLY VERIFIED / threshold UNSUPPORTED — adopt drain discipline, measure on droplet].

Minor correction carried everywhere: the suite is `test/test.sh` (V21 INCORRECT-minor fixed).

## 7. License posture (inherited #10 §11 — API-verified, no new burden here)

Repo `arjaythedev/twelve-dollar-server-challenge` MIT: study + re-implement ideas freely; verbatim copies keep the notice (C-pin shapes reused as reference, never merged). Per-lane dependencies: A — xerial `sqlite-jdbc` Apache-2.0 (+ HikariCP / Jackson Apache-2.0 if kept); B — + Netty/Reactor Netty Apache-2.0 (manifest-noted); C — same Apache-2.0 set, heaviest manifest; D — SQLite amalgamation public domain + Temurin JDK pin (GPLv2+CE — flag/build use clean, no JDK redistribution planned). Harness-only: k6 AGPLv3 (binary use, not linked), Nginx 2-clause BSD (config reference), JFR/OpenJDK vendor notes. This plan introduces no dependency — nothing to clear beyond inheritance. Unsafe/obsolete/undocumented tricks adopted here: none (DX-8 high-risk rows carry falsifiers + edge-audit gates).

## 8. Verification appendix (commands run this session)

- `git status`: on `research/java-analysis` tracking `origin/research/java-analysis`, clean except pre-existing untracked `.idea/` (left alone, never added — verified via `git status --short`).
- `git rev-parse` / `git log --graph`: `main` = `research/java-analysis` = `dba6448`; `backup/main-baseline-2026-10-13` = `026bdb6` intact; `reference/spring-boot-pr33` = `7a9cb99` untouched; log linear; `git remote -v`: origin = fork, upstream = original; no upstream push.
- Precondition: `gh issue list --milestone Task-2-Research` shows #11 OPEN, #2–#10 CLOSED, #1 + #12 OPEN untouched. #11 was OPEN so the session proceeded.
- Claimed #11 via `gh issue comment 11` before any work (parallel-session guard).
- Base refs read in full (§0.2 list); pins re-verified fresh (§0.3), not trusted from parents.
- Self-containment check: §1 (ground truth) + §2 (lanes + DX/X map) + §3 (ONE run book with copy-paste commands) + §4 (13 phases with consumes/objectives/tasks/deps/acceptance/verification/falsifier/rollback/risks/skills) + §5 (9 open questions scheduled) + §6 (prohibited + quarantines) — a later implementation agent executes without reopening parents (pointers included for depth, never required for execution).
- No-new-claims check: every factual row traces to #10 sections / SPEC / test / bench / schema pins cited inline; new-claim count 0.
- No-invented-targets check: no suite/load executed; the only reference score is the agent's own Phase 7 measured baseline; every numeric figure attributed to SPEC/threshold/transcript/author/laptop source with UNSUPPORTED/OUTDATED quarantine where due (§6); validated-Java count stays 0.
- Classification check: every inherited significant claim carries one of the six verdicts (§§1–2/5–6 tables + inline tags); plan-procedure statements are method, not claims.
- License check: §7 inherits the API-verified table; no new dependency introduced.
- Forbidden-word grep over draft + final file before commit — empty both times.
- `git add` ONLY `docs/IMPLEMENTATION_ROADMAP.md`; commit `docs(roadmap): implementation_roadmap closes #11`; push `origin research/java-analysis`; ff-merge to `main`; push `main` (or STOP on non-ff).

*No implementation started. Next: #12 Verification push + STOP gate (wiring comment on Epic #1); #11 now UNBLOCKED for close (#10 closed).*
