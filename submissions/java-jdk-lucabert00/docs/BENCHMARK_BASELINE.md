# Gate F — first benchmark record (Wayfinder-8/8, #21)

Status: **INCOMPLETE — measurement pending on the benchmark box.**
No S-level was run in this session; every S-row below reads `NOT RUN`.
No number in this file is a result, a target, or an expectation — the only
reference score will be the measured baseline itself once the S0–S4
limit-search executes on the benchmark box with a separate generator.
This Windows dev box authors docs only and never scores local runs.

Build under test: `fbb9d31` (`feat(java-baseline): benchmark readiness
closes #20`), unchanged since #20 — no code, script, or config drift, so
#20 evidence is reused without re-proof (re-prove only on build change;
any future change restarts at the gate as a new EX row owned by a future
phase).

Method: `research/findings/BENCHMARK_STRATEGY.md` §§1–7, executed as written
(the ONE method — not re-derived). Run book: `docs/IMPLEMENTATION_ROADMAP.md`
§§3–4. Harness pinned by `bench/load.js` (stages, thresholds, VU mix —
unmodified).

## 1. Gate (§3.1) — reused, build unchanged

- Gate owner: #19 (Gate D) + #20 re-gate after the echo-only `start.sh`
  change. Authoritative line (Ubuntu/CI, fresh DB):
  `passed=42 failed=0`.
- Suite file: `test/test.sh` (126 lines, gate at lines 125–126),
  git blob `381effb8a522989b4ba64a08471d9d08126e0152`.
- Runner: `test/run.sh`,
  git blob `3d08303f5fa4925357cf32daf9e797d013236681`.
- Seed: `seed/feed.db` md5 `e1c4bcb0f8aad922ca8f9a49939cfb1b`
  (verified fresh this session: `md5sum seed/feed.db` matches the #19/#20
  record; content sha `738d11ab3164c13177e347f75038d1e80b43c64e754544a42d1c32bd8d7bcf1d`
  per `seed/make-seed.sh` `EXPECTED`, 50000 users / 500000 posts /
  2016005 likes + `seed/tokens.json`).
- Windows dev note (unchanged from #19): `passed=41 failed=1` locally, single
  miss is the mingw `curl` special-char argv quirk; JUnit `HttpGateTest`
  passes the same round-trip via explicit UTF-8. Not a regression.
- Fresh-DB discipline: every gate/bench attempt starts from a private copy
  (`cp seed/feed.db /tmp/bench.db`); second run on the same DB fails
  goldens by design (freshness proof in `docs/GATE.md`).
- Rule 5/6 checklist: signed PASS in `docs/CHECKLIST.md` (#19); standing
  posture (pool 4, backlog 128, `-Xmx512m`, pragma floor) explicitly not
  optima — sizing belongs to later phases.

## 2. k6 invocation (pasted form — S-level fills `<n>` on the benchmark box)

```bash
k6 run -e VUS=<n> -e BASE_URL=http://127.0.0.1:3000 bench/load.js
# Defaults in force unless the run log records an override:
# DURATION=5m, RAMP_UP=60s, TOKENS=seed/tokens.json
```

- k6 runs on a SEPARATE machine — never co-located with the 1-vCPU server.
  Co-located runs are INVALID and never scored.
- Stages pinned (`bench/load.js:26-43`): 60 s ramp → 5 min scored hold →
  30 s down, `gracefulRampDown 10s`.
- Thresholds pinned (all three green for the full hold):
  `p95<500`, `p99<1000`, `err<1%`.
- VU mix pinned: feed → think 3–7 s → post → think 3–8 s → 15% like →
  2% post → idle 5–15 s; first-iteration 0–5 s stagger.

## 3. Limit-search S0–S4 + J11 table (§§3.3–3.4)

Procedure (method §§3/5.2): S0 warm-up unscored → S1 probe doubling to
fail → S2 narrow to ±250 → S3 full 5-min hold TWICE at the candidate
(report spread, not the better half) → S4 step-down 250 on fail.
Stop: green-N + red-N+250 → reference N; tainted reds are INVALID
(fix harness, re-run, never score); three clean reds 250 apart →
highest green below.

| VUs | k6 avg/med/p95/p99/max | err% | k6 exit | srv CPU% | srv RSS/free | gen CPU/RAM | mode | gate (suite line) | verdict |
|---|---|---|---|---|---|---|---|---|---|
| S0 | NOT RUN | — | — | — | — | — | nginx | `passed=42 failed=0` (reused §1) | warmup pending |
| S1 probe | NOT RUN | — | — | — | — | — | nginx | same build | pending |
| S2 narrow | NOT RUN | — | — | — | — | — | nginx | same build | pending |
| N (S3 ×2) | NOT RUN | — | — | — | — | — | nginx | same build | pending |
| N+250 | NOT RUN | — | — | — | — | — | nginx | same build | pending (fail+1 required) |

Missing gate line / k6 command / fail+1 row = INCOMPLETE, not a result —
this record is INCOMPLETE on all three counts for the S-rows (gate line
present via §1 reuse; measured rows absent).

## 4. Falsifier sequence (§3.5) — procedure, drilled at the first ceiling

On the first ceiling, in order (no step skipped):

1. Generator saturation → INVALID run (add generators, re-run S3, never
   score the red). Per-hold evidence: per-generator-VM CPU/RAM.
2. Port-drain shape (`connect() failed` + TIME_WAIT + ephemeral counts) →
   INVALID run (drain-wait / fresh generator, re-run). Every run log
   records generator count, ephemeral range, drain wait, retry policy.
3. Clean generators + idle server at the ceiling → harness artifact until
   proven otherwise. No server limit is claimed.
4. Conntrack occupancy is logged on EVERY ceiling even when the table is
   nearly empty (drill the habit): occupancy + kernel drops +
   forced-reconnect counts alongside each S3 hold.

Status: NOT RUN — first ceiling not yet reached; sequence stands ready.

## 5. Profile baseline

- `EXPLAIN QUERY PLAN` (fresh, empty DB): `docs/EXPLAIN_QUERY_PLAN.md`
  (Phase 3, #16 — feed via `posts_created_at_id_idx`, single-post via PK,
  like-count via covering `likes_post_id_idx`).
- `EXPLAIN QUERY PLAN` (grown, seed rows) — captured this session,
  read-only, on a private copy (`cp seed/feed.db`, never the seed itself),
  session pragmas applied first to mirror pool state
  (`journal_mode=wal`, `synchronous=1`, `foreign_keys=1`,
  `busy_timeout=5000`; row counts 50000 / 500000 / 2016005):

```
FEED (ORDER BY p.created_at DESC, p.id DESC LIMIT 20):
6|0|0|SCAN TABLE posts AS p USING INDEX posts_created_at_id_idx
9|0|0|SEARCH TABLE users AS u USING INTEGER PRIMARY KEY (rowid=?)
16|0|0|CORRELATED SCALAR SUBQUERY 1
21|16|0|SEARCH TABLE likes AS l USING COVERING INDEX likes_post_id_idx (post_id=?)
SINGLE (WHERE p.id = 1):
3|0|0|SEARCH TABLE posts AS p USING INTEGER PRIMARY KEY (rowid=?)
6|0|0|SEARCH TABLE users AS u USING INTEGER PRIMARY KEY (rowid=?)
13|0|0|CORRELATED SCALAR SUBQUERY 1
18|13|0|SEARCH TABLE likes AS l USING COVERING INDEX likes_post_id_idx (post_id=?)
LIKE-PROBE (SELECT count(*) FROM posts WHERE id = 1):
3|0|0|SEARCH TABLE posts USING INTEGER PRIMARY KEY (rowid=?)
```

  Shape matches the fresh-DB plans exactly: index usage is unchanged with
  grown data. Capture tool: Python 3.9 `sqlite3` (module version 3.35.5)
  — the reference SQL is engine-agnostic here (plan shape, not timings).
- Pragma declaration (standing posture, every pooled conn,
  `Db.applyPragmas` + `Db.init` 4/4 assertion, fails fast):
  `journal_mode=WAL` / `synchronous=NORMAL (1)` / `foreign_keys=ON (1)` /
  `busy_timeout=5000` / `cache_size` default (session knob, re-asserted per
  variant, never assumed) / `mmap_size` default (untouched) /
  autocheckpoint schedule default (untouched).
  NORMAL asterisk note: NORMAL is the valid WAL floor (passes rules) but is
  weaker than FULL on power loss — documented, never claimed as FULL.
  A FULL-cost probe is a single Phase 11 run, never a silent direction
  change.
- JFR artifact: PENDING — continuous low-overhead recording stays on per
  S-run with cap + rotation (2 GB box); artifact paths land in §3 rows as
  runs complete. No JFR captured in this session (no S-run executed).

## 6. Verify-only closures (evidence)

(a) Compression — k6 sends no `Accept-Encoding`, so the `bench/nginx.conf`
gzip block (lines 18–20: `gzip on`, `gzip_types application/json`,
`gzip_min_length 1024`) is inert for the official workload:

- Static evidence (fresh this session): case-insensitive grep for
  `Accept-Encoding|gzip|Content-Encoding` over `bench/` hits ONLY
  `bench/nginx.conf:18-20`. `bench/load.js` sets no `Accept-Encoding` on
  any of its four request shapes (feed GET, post GET bare; like/create
  POSTs carry only `Authorization` + `Content-Type`, `bench/load.js:49,62,66`).
- Standing verdict (planning doc §K): k6 sends no `Accept-Encoding` by
  default (maintainer-confirmed), and the workload file adds none — nginx
  only compresses when the client advertises support.
- Conclusions: no application-level gzip work in this map; never
  attribute Nginx overhead to compression without a capture.
- Live capture (app-side header log or tcpdump sample proving the
  identity workload): PENDING on the benchmark box — closes with the
  first S-run, with evidence instead of assumption.

(b) Startup flag-state — `start.sh` final (unchanged since #20):

```
JDK HttpServer flags (startup log):
  -Xmx512m
  -Dsun.net.httpserver.idleInterval=70
  collector=default (no -XX flag; DX-7 owns tuning)
  sun.net.httpserver.maxIdleConnections=200 (default, unchanged)
  sun.net.httpserver.nodelay=false (default, unchanged)
```

plus live-property lines from `challenge.Main` and 4/4 pragma assertions
before the HTTP layer starts (full excerpt pasted on #20; build unchanged
since, reused here). `idleInterval=70` satisfies the SPEC ≥ 65 s rule
with the reference value class; `maxIdleConnections=200` covers the
Nginx 64-idle envelope; `nodelay=false` stays default (experimental,
deferred to Phase 10).

## 7. JFR-on/off falsifier

Policy (one JFR-off run per lane): the first scored S3 hold on the
benchmark box runs with JFR on (artifact path in §3); a repeat hold at
the same level runs with JFR off to prove p99 is not a profiling
artifact. If the off-run cannot be scheduled with the first S3 pair, it
is recorded here as DEFERRED with reason (generator availability) and
scheduled against the next S3 pair — never silently dropped.

Status: SCHEDULED (deferred — no S-run executed in this session).

## 8. Record status

- Gate line: PRESENT (§1 reuse, build unchanged).
- k6 command: PRESENT as form (§2); no level filled (no run).
- J11 rows: ABSENT (NOT RUN) — no N (PASS), no N+250 (FAIL).
- Falsifier log: procedure ready (§4), no entries.
- Profile paths: EXPLAIN fresh + grown PRESENT; JFR PENDING.
- Header evidence: static PRESENT (§6a); live capture PENDING.
- Flag-state log: PRESENT (§6b reuse).
- JFR-off falsifier: SCHEDULED (§7).

Verdict: INCOMPLETE. No tuning, no multi-variable change, no score claim,
no code change (docs-only record). Next step: execute S0–S4 on the
benchmark box per §§2–4 and amend this record with measured rows; any
setup bug S0 finds returns to its owning ticket's scope with a re-gate.

Method: minimal-diff senior-dev mode (smallest record serving later phases).
