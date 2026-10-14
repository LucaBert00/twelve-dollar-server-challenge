# BENCHMARK_STRATEGY — correctness-first reproducible method (Wayfinder-8/11, closes #8)

- Ticket: #8 `[Wayfinder-8/11] BENCHMARK_STRATEGY -- reproducible method` (OPEN at writing time)
- Output: this file only (`research/findings/BENCHMARK_STRATEGY.md`). No app code.
- Parallel session note: #9 `JAVA_ARCHITECTURE_DECISION` is CLOSED @ `41d7d4b` in a sibling session. This doc is reconciled to it (§0.6), not derived from it.
- Truth hierarchy: `SPEC.md` + `test/test.sh` > prior analysis/findings docs (#2–#7 + #9) > PR33/PR58 discussion > AI reports > external.
- Every significant claim below carries one of `VERIFIED` / `PARTIALLY VERIFIED` / `INCORRECT` / `UNSUPPORTED` / `OUTDATED` / `EXPERIMENTAL HYPOTHESIS` with primary evidence (repo URL + commit SHA + file + line, doc URL, or measured output). Needs-experiment rows carry method, never a conclusion. No invented targets, no fabricated numbers, no microbenchmark confusion.

## 0. Method, skills, inputs, filters, #9 reconciliation

### 0.1 Skills used (ticket mandate, in order)

Invoked via skill tool this session: `research` (primary-source derivation: SPEC/test/load.js/README/manifest/transcript + prior docs), `systematic-debugging` (§6 bottleneck-ID procedure: reproduce → gather evidence per boundary → single hypothesis → one variable → verify; §7 template enforces it), `documentation-writer` (Diátaxis How-to Guide + Reference mixed: §1–§7 how-to, §8–§10 reference), `verification-before-completion` (gate before every claim below and before commit/push/close).

Standing map skills — skipped with reason (ticket permits skip with justification, none claimed as used): `grilling` (scope fixed by #8 body checklist + handed-over leads; no open scoping questions — proceed), `domain-modeling` (no new domain terms: VU, closed-model, p95/p99, group commit, WAL, conntrack, drain — all reused from #2–#7/#9 with identical meaning), `to-spec` / `to-tickets` (output is one findings doc, not a new spec or ticket set; §7 template rows are verifiable slices consumed by #10, not new tickets), `github` / `git-workflow` (standard linear ops — claim comment, single-file commit, ff-only merge, close — each verified with fresh `gh`/`git` commands per §11).

### 0.2 Inputs verified (fresh this session)

- Issue #8 body (scope source of truth) + 2 comments (frontier note + recon context pointer — re-verified below, not trusted) + Epic #1 body (order + Decisions) + milestone list (#8 OPEN; #2/#3/#4/#5/#6/#7/#9 CLOSED; #10/#11/#12 + Epic #1 OPEN untouched) — fresh `gh` commands (§11).
- `docs/WAYFINDER_MAP_PROMPT.md` §4 (global hard rules) — read in full.
- `research/analysis/REPORT_COMPARISON.md` (246 lines), `research/analysis/FACT_CHECK.md` (145 lines), `research/analysis/VIDEO_ANALYSIS.md` (590 lines), `research/analysis/SPRING_BOOT_CODE_REVIEW.md` (370 lines) — read in full.
- `research/findings/GITHUB_RESEARCH.md` (348 lines: Areas E–F + §§D3/F1–F3 read in full; §§A–D/E1–E2 read for context), `research/findings/OPTIMIZATION_CATALOG.md` (138 lines: §§OPT-09/X1–X7/§5 read in full; §§2–4 read for row mapping), `research/findings/JAVA_ARCHITECTURE_DECISION.md` (§0.6 consumed-method note + §5 DX-1..DX-8 rows read in full; §§1–4/6–7 read for option/gate context) — read in full.
- `SPEC.md` (84 lines), `schema.sql` (26 lines), `bench/load.js` (70 lines), `test/test.sh` (126 lines), `docs/PROJECT_CONTEXT.md`, `research/sources/SOURCE_MANIFEST.md` — read in full.
- `bench/nginx.conf` (30 lines), `README.md` scoring/rules (§§Rules/Scoring) — read in full for limit-search normative form.
- Transcript `research/inputs/I-Asked-Expert-Programmers-to-Max-Out-a-12-Dollar-Server_QGWKCaglSBI_transcript.md` — methodology sections only (§§[00:00]/[01:01]/[02:02]/[03:02]/[15:11]–[16:12]/[18:13]/[19:49]/[20:15]/[21:15] read; per-language numbers reused only via #3 J1–J11, not re-derived).

### 0.3 Forbidden-word filter

Case-insensitive grep for the forbidden term (base prompt §4.4) run over the draft and again over the final file before commit — empty both times (see §11). Where that concept is needed, this doc writes `minimal-diff senior-dev mode`.

### 0.4 Classification discipline

Evidence cells and inline tags open with the verdict + primary citation (repo URL + SHA + file + line, doc URL, or measured output). `EXPERIMENTAL HYPOTHESIS` rows name a §7 experiment instead of a winner. Quarantined figures (OPT-40..OPT-44) are never quoted as targets (§9).

### 0.5 Recon leads disposition (verify, don't trust — all re-verified)

| Recon lead | Disposition this session |
|---|---|
| `bench/load.js` stages (60s ramp / 5m hold / 30s down, `gracefulRampDown 10s`) + thresholds (p95<500, p99<1000, err<1%) + VU mix (feed → think 3–7s → post → think 3–8s → 15% like / 2% post → idle 5–15s, SharedArray tokens) | [VERIFIED] `bench/load.js:26-43` (stages/thresholds/`gracefulRampDown`/`summaryTrendStats`), `bench/load.js:20-24` (env + `SharedArray` tokens), `bench/load.js:11-14,47-70` (loop + probabilities + sleeps) — adopted verbatim in §2 |
| V8 limit-search (warm-up 1000/2min → 2500 doubling → ±250 → 5-min hold → −250 retry) | [VERIFIED] `README.md:100-105` (warm-up/probe/narrow/confirm/step-down) + `bench/load.js:26-43` (stages) + transcript §[02:02] (criteria) — adopted verbatim in §3 |
| SPEC envelope (Nginx 64 idle / ≥65s vs direct ~15k / `LimitNOFILE=65535`) | [VERIFIED] `SPEC.md:18-21` + `bench/nginx.conf:3-6` (`keepalive 64`) + `README.md:55-58` (rule 9, 14–20% class) — adopted verbatim in §2.5/§4 |
| J11 reporting (p95/p99/err + CPU + RSS at pass AND fail+1) | [VERIFIED] as reporting policy (`VIDEO_ANALYSIS.md` §4.10/§4.12 tiebreak + §9/J11; consumed in #9 §5 preamble) — adopted verbatim in §5.4 |
| F1 closed-model semantics + coordinated-omission caution | [VERIFIED] `bench/load.js:26-36` (`ramping-vus`) + k6 open-vs-closed + ramping-vus docs (#6 F1) — adopted verbatim in §2.1/§5.3 |
| F2 drain discipline (`~32k` threshold NOT a constant) | [PARTIALLY VERIFIED mechanism / UNSUPPORTED threshold] PR33 `6684ebf` message (author-stated, box-unmeasured — #6 F2, OPT-44) — adopted as discipline + per-box measurement in §4.5, threshold never quoted |
| F3 correctness-first + pragma-asserted + JFR pipeline | [VERIFIED existence] `test/test.sh:1-126` + pin `ApiTest` 11 methods (#4 §3.8) + PR33 `baf8550` `PROFILE=true` flag (#6 A2/F3) — adopted verbatim in §1/§4.4 |
| D3 JFR <1% overhead | [PARTIALLY VERIFIED] vendor-stated (Red Hat/JFR guides — #6 D3), box-unmeasured — adopted with cap + rotate + on/off falsifier in §4.4 |
| OPT-09 harness | [VERIFIED method] catalog §2.1 row (ramp/hold/JFR + drain discipline) — adopted as §0.6 skeleton |
| Quarantines OPT-40..OPT-44 (never quote as targets) | [VERIFIED quarantine list] catalog §5 — inherited verbatim in §9 |

### 0.6 #9 reconciliation (ONE method for #10)

#9 §0.6 consumes exactly this harness: correctness-first (`test/test.sh` 100% on a FRESH DB, OPT-01) → k6 closed-model `ramping-vus` (thresholds p95<500 / p99<1000 / err<1%, VU = browsing loop with think/idle mix, F1) → warm-up/probe/narrow/confirm limit-search (V8) → TIME_WAIT drain discipline (F2, `~32k` NOT quoted — OPT-44) → pragma assertions + JFR per run (F3/D3, OPT-09); every §5 DX row reports p95/p99/err + CPU + RSS at pass AND fail+1 (J11); one variable per run.

This doc adopts that shape unchanged as §§1–7. Delta: none. #10 inherits ONE method (this file's runnable form); DX-1..DX-8 variable/fixed/gate/falsifier definitions in #9 §5 survive verbatim under it (§7.2 mapping). If a later session changes the runnable form, it must state the delta against this §0.6 in the same table shape — no silent split.

---

## 1. Correctness gate: `test/test.sh` first (nothing counts without 100%)

### 1.1 Rule

[VERIFIED] No benchmark run counts unless the exact build under test passes the full suite on a FRESH database copy immediately before the run (`SPEC.md:3-4` suite-wins rule; `README.md:41` rule 4; `test/test.sh:1-9` fresh-DB discipline; catalog OPT-01 adopted gate; #9 §0.6 first step).

- Suite entry is `test/test.sh` (126 lines, `passed/failed` gate at `test/test.sh:125-126`). [VERIFIED] The `test/run.sh` filename in older AI-report prose is [INCORRECT-minor] (#2 V21) — `test/run.sh` is the submission-runner path cited in PR docs, not the suite.
- Second run against the same database fails golden checks by design (writes one post + likes). [VERIFIED] `test/test.sh:7-9,95-123`.

### 1.2 Runnable gate (copy-paste)

```bash
# 0. Fresh database per gate attempt (suite compares exact bytes with test/golden/*.json)
bash seed/make-seed.sh            # if a rebuild is needed (50k users / 500k posts / 2M likes)
rm -f /tmp/bench.db && cp seed/feed.db /tmp/bench.db

# 1. Start THIS build on the fresh copy (example: Nginx mode)
SQLITE_PATH=/tmp/bench.db JWT_SECRET=twelve-dollar-challenge HOST=127.0.0.1 PORT=3000 ./start.sh &
SRV=$!; sleep 3

# 2. Gate (42 checks). Must print failed=0.
bash test/test.sh http://127.0.0.1:3000; echo "suite_exit=$?"
# Expected tail: passed=42 failed=0  [VERIFIED shape] test/test.sh:125-126 prints passed/failed; 42-check count per README.md:17 + SPEC.md:3-4 hierarchy

# 3. Record the gate line in the run log (required field, §7.1): suite SHA, DB copy md5, pass line.
kill $SRV
```

- `JWT_SECRET` default in the suite is `twelve-dollar-challenge` [VERIFIED] `test/test.sh:15`; SPEC runtime table names the same value [VERIFIED] `SPEC.md:8-14`.
- What the gate proves (and what it does not): golden reads [VERIFIED] `test/test.sh:52-57`, health/404/id matrix [VERIFIED] `test/test.sh:60-66`, auth matrix incl. auth-before-id [VERIFIED] `test/test.sh:68-83,113`, create validation (trim, 500 code points, malformed JSON) [VERIFIED] `test/test.sh:85-93,96-103`, create/like/read-back + feed-20/top-of-feed/entry-match [VERIFIED] `test/test.sh:110-123`. It does NOT prove performance — a green gate is permission to bench, not a score.
- Per-variant re-gate: every single-variable change (§7) re-runs this full gate. No fast run that skips work is accepted (measure-first policy, PR33 `OPTIMIZATIONS.md` via #4 §3.9). [VERIFIED policy]

## 2. k6 harness pinned (identical for every run)

### 2.1 Executor semantics

[VERIFIED] `ramping-vus` is a closed model: the next iteration starts only when the last finishes (k6 open-vs-closed + ramping-vus docs, #6 F1). Throughput is bounded by VU count × iteration latency; slow responses *reduce* arrival rate. File: `bench/load.js:26-36`.

- Coordinated-omission caution (required): do NOT read closed-model p99 as open-arrival p99; compare only same-harness runs. [VERIFIED caveat] #6 F1. If the owner later wants open-arrival queue behavior, that is a separate arrival-rate probe — not this harness, never mixed into the same table.

### 2.2 Stages (do not improvise)

[VERIFIED] `bench/load.js:26-43`:

```js
stages: [
  { duration: __ENV.RAMP_UP || '60s', target: VUS },   // 60 s ramp
  { duration: __ENV.DURATION || '5m', target: VUS },   // 5 min hold (the scored hold)
  { duration: '30s', target: 0 },                      // 30 s ramp-down
],
gracefulRampDown: '10s',
```

- `RAMP_UP`/`DURATION` env overrides exist but the DEFAULTS above are the scored shape. Limit-search (§3) varies only `VUS`; stage durations stay pinned unless the run log records the override as the variable (then it is no longer the standard hold — see §7).

### 2.3 Thresholds (pass/fail, all must hold for the full 5-min hold)

[VERIFIED] `bench/load.js:38-41` + `README.md:105`:

```
http_req_duration: p(95)<500, p(99)<1000     # ms
http_req_failed:   rate<0.01                  # < 1% errors
summaryTrendStats: avg, med, p(95), p(99), max
```

- k6 exits non-zero on threshold breach [VERIFIED] `bench/load.js:14-15` header comment. A run is PASS only if k6 exits 0 AND the full 5-min hold completed (sustained, not burst — transcript §[02:02]). [VERIFIED]
- Score definition: the most users that pass a 5-minute hold with all three thresholds green. [VERIFIED] `README.md:105`.

### 2.4 VU mix (the workload — VU ≈ browsing human, NOT RPS)

[VERIFIED] `bench/load.js:11-14,47-70`:

| Step | Action | Think / probability |
|---|---|---|
| 1 | `GET /feed` (expect 200) | — |
| 2 | think | 3–7 s uniform (`between(3,7)`) |
| 3 | `GET /posts/:id` (one id sampled from the feed) | — |
| 4 | think | 3–8 s uniform (`between(3,8)`) |
| 5 | `POST /posts/:id/like` (expect 200/201) | 15% chance (`LIKE_PROB=0.15`) |
| 6 | `POST /posts` (expect 201) | 2% chance (`POST_PROB=0.02`) |
| 7 | idle | 5–15 s uniform (`between(5,15)`), then repeat |

- Env: `BASE_URL` (default `http://127.0.0.1:3000`), `VUS` (default 100), `DURATION` (default `5m`), `RAMP_UP` (default `60s`), `TOKENS` (default `seed/tokens.json`, 20k pre-signed tokens per SPEC auth §). [VERIFIED] `bench/load.js:20-24`.
- First-iteration stagger `sleep(0–5 s)` on `__ITER==0` spreads same-tick VUs. [VERIFIED] `bench/load.js:50`.
- `Math.random()` think times are unseeded → run-to-run variance is expected; §5.2 handles it by repetition, not by re-seeding the harness (harness stays byte-identical).

### 2.5 Connection envelope (size for it from the start)

[VERIFIED] `SPEC.md:18-21`:

- Behind Nginx (`127.0.0.1:3000`): Nginx keeps up to 64 idle connections; hold idle ≥65 s so Nginx never reuses a socket just closed. Pin reference: PR33 `keep-alive-timeout=70s` satisfies the rule (#4 §3.2). `bench/nginx.conf:3-6` sets `keepalive 64`.
- Direct (`0.0.0.0:80`): every k6 VU holds its own keep-alive → size for ~15k sustained (and note the video's 64k conntrack ceiling as ceiling-evidence, not a target — §9/OPT-44). Service gets `LimitNOFILE=65535` + `CAP_NET_BIND_SERVICE`.
- SPEC's `~15,000` is a sizing expectation, not a cap [PARTIALLY VERIFIED reading] (#3 §7/S2); the video proves a top contender must hold ~64k keep-alives (transcript §§[15:11]–[16:12]). Java impls size idle/backlog/file limits for the envelope in §4.3, and DX-3 (Nginx vs direct) is the last HTTP variable (§7.2).

### 2.6 Runnable k6 invocation (copy-paste)

```bash
k6 run -e VUS=2500 -e BASE_URL=http://127.0.0.1:3000 bench/load.js
# Variants: -e DURATION=5m -e RAMP_UP=60s -e TOKENS=seed/tokens.json (defaults shown; log any override)
```

- k6 runs on a SEPARATE machine. [VERIFIED] `README.md:97-98` + transcript §[02:02] generator discussion. Never co-locate generator and server on the 1-vCPU box (§6.2).

## 3. Limit-search procedure (steps + stop conditions, no invented targets)

[VERIFIED] Normative form `README.md:100-105`, restated in #2 V8 / #5 V8, criteria in transcript §[02:02], stages in `bench/load.js:26-43`:

```
S0  Warm-up (unscored):  VUS=1000, DURATION=2m. Absorbs JIT + pool + page-cache warmup. Any fail here is a setup bug — fix harness, do not score.
S1  Probe from 2500, doubling to fail: 2500 → 5000 → 10000 → … until a run FAILS thresholds (§2.3) or errors.
S2  Narrow to ±250: binary/step search between last PASS and first FAIL until the boundary is within 250 users.
S3  Confirm: ONE full 5-minute hold at the candidate number (default DURATION=5m, RAMP_UP=60s). PASS = score candidate.
S4  Step-down on fail: if S3 fails, step down 250 users and repeat S3. Score = highest VUS with a green 5-min hold.
```

- Stop conditions (all required): (a) S3 green at N and red at N+250 → score N; (b) any S3 red with generator saturation (§6.2) or conntrack/full-table evidence (§6.3) → INVALID run, fix harness, re-run S3 (do not score the red); (c) three consecutive S3 reds each 250 apart with clean harness → score the highest green below them.
- Worked shape (illustrative arithmetic, NOT a target): pass at 2500 → 5000 → fail at 10000 → narrow 5000..10000 by 250s → confirm greens/fails → step-down. No VU number in this file is a Java expectation (§9 quarantines apply).
- `benchmark.py` in PR33 (warm-up → doubling probes → 250-user refinement → 5-min confirmation, p95<500/p99<1000/<1% errors) is the same shape [VERIFIED description, unexecuted] (#4 §3.8) — a compatible automation of S0–S4, not a second method. Either driver is fine if it logs S0–S4 identically.

## 4. Warmup, monitoring, profiling (what to capture per level)

### 4.1 Warmup

- k6 ramp (60 s) + hold (5 m) IS the JIT warmup [VERIFIED method] #6 D3 (`bench/load.js` stages + `benchmark.py` description, both read, neither executed for figures). S0 (1000/2min) is additional warmup, unscored [VERIFIED] `README.md:100`.
- Long-running warm process (open conns + prepare at startup, start ≤60 s per `README.md:59-60` rule 10) is baseline posture, not an experiment [VERIFIED posture] video PHP §[10:07]–§[11:08] → J7; catalog OPT-10.

### 4.2 Monitoring (required per S3 hold — J11 report shape)

At minimum, per hold, at PASS and at FAIL+1 step:

| Signal | How | Why |
|---|---|---|
| k6 `avg/med/p95/p99/max` + error rate | k6 end-of-test summary (`summaryTrendStats` [VERIFIED] `bench/load.js:42`) | Thresholds + tiebreak |
| Server CPU % (1-min + hold avg) | `docker stats` / `pidstat` / droplet monitor, logged per hold | Bottleneck ID (§6); video tiebreak used CPU headroom (transcript §§[15:11]–[19:14]) |
| Server RSS + box free (2 GB, no swap) | same + `/proc/meminfo`; include native/SQLite/Nginx/OS, not heap alone (#6 D1) | OOM = loss (`README.md:52-54`); heap-vs-RSS discipline |
| Accept queue, `EMFILE`, eviction/reconnect rate | server logs + `dmesg`/`ss -s` | DX-3/E-edge falsifier (§7.2) |
| Generator CPU/RAM per k6 VM | generator-side `top`/cloud monitor | §6.2 artifact check |
| Server + generator wall-clock alignment | NTP-synced timestamps in both logs | Post-hoc correlation |

- J11 policy: every experiment reports p95/p99/err + CPU + RSS at pass AND at fail+1 (table shape of #3 §6.1). [VERIFIED reporting policy] #3 J11, consumed in #9 §5 preamble.

### 4.3 Connection-harness logging (required fields per run)

Nginx vs direct mode, keep-alive/idle setting (≥65 s rule both modes [VERIFIED] `SPEC.md:18-19`), `LimitNOFILE`, backlog size, eviction policy if any, forced-disconnect count. Never gate a conclusion on a run with forced disconnections or a full conntrack table — fix the harness first (#3 J10). [VERIFIED transfer policy]

### 4.4 Profiling (JFR per run + targeted counters)

- JFR continuous/low-overhead stays on per run (`PROFILE=true` shape in PR33 `baf8550` [VERIFIED message] #6 A2/F3). Overhead <1% is vendor-stated [PARTIALLY VERIFIED] (Red Hat/JFR guides — #6 D3), box-unmeasured: cap + rotate JFR files (disk/I-O on 2 GB box) and run one JFR-off falsifier per lane to prove p99 is not a profiling artifact.
- Per-lane counters (not all every run — attach to the DX row that needs them, §7.2): pool-wait histograms + `busy_timeout` expirations (DX-2), queue time (DX-2/DX-6), allocation flame graph (DX-1/DX-7), GC logs + native-mem tracking (DX-7), `EXPLAIN QUERY PLAN` on fresh + grown data (DX-1).
- Correctness + pragma assertions ride along: `ApiTest`-style pragma checks on every pooled conn (`journal_mode=wal, synchronous=1/NORMAL, foreign_keys=1, busy_timeout=5000` [VERIFIED present] #4 §3.8) catch session-only knob drift (e.g. `cache_size`-class reversion — #6 C5); 8-way concurrent-like idempotency + 503-on-closed-pool re-gates catch check-then-insert races. [VERIFIED existence] #6 F3.

### 4.5 TIME_WAIT / ephemeral-port drain discipline (mandatory repro hygiene)

- Mechanism [PARTIALLY VERIFIED]: persistent k6 pod + closed conns × 60 s TIME_WAIT × rising VU count = ephemeral exhaustion → `connect() failed` misread as server failure; drain-wait converts false negatives into slower cadence (PR33 `6684ebf` message re-fetched verbatim — #6 F2).
- Discipline: wait for k6 ports to drain between runs (or fresh k6 pod / wider ephemeral range); record pod reuse, ephemeral range, drain wait, retry policy in every run log. The `~32k users` figure in that message is author-stated, box-unmeasured → [UNSUPPORTED as a general figure] (OPT-44): adopt the discipline, measure the threshold on the droplet, never quote 32k as a constant.
- Server-side RST eviction (PR58 LRU + `SO_LINGER=0`) is a separate SERVER choice with client-retry cost, not a harness fix. [VERIFIED distinction] #6 F2/A4.

## 5. Latency distributions + stats reliability

### 5.1 Distributions to capture (never a single mean)

Report `avg/med/p95/p99/max` + error rate together per hold [VERIFIED] `bench/load.js:42` `summaryTrendStats`. Tail decides: video failures arrived as P99-only (PHP 40k→41k: P95 flat 182 ms, P99 853 ms→2.4 s), cliff (Bun 47k→48k), and error-budget-first (Go/Zig forced-reconnect shapes) [VERIFIED observations] #3 §§4.7/4.9/4.4/4.11. Judge tails, not means (pool/tail guidance #5 E5). At a shared ceiling, lower P99 + lower CPU + lower RAM wins the "fastest" call [VERIFIED framing] transcript §§[15:11]–[19:14] + #3 J11.

### 5.2 Repetitions + variance handling

- Minimum: S3 confirm hold runs TWICE at the score candidate (same build, same box, same generator count); both must be green. Any S2 boundary within one 250-step of a red runs twice.
- Unseeded `Math.random()` think/idle times + shared-CPU noise guarantee run-to-run spread: report the spread (both p99s, both CPU avgs), not the better half. A single green surrounded by reds is [UNSUPPORTED] as a score — re-run.
- No invented variance band is stated here (no Java bench exists — #7 §0 validated-0). Variance handling is procedural (repeat + report spread), not a fabricated ±%.

### 5.3 Coordinated-omission + closed-model rules

- Same-harness comparisons only (§2.1). A p99 measured under closed-model `ramping-vus` MUST NOT be compared to an open-arrival number or to a different think-mix. Arrival-rate changes (e.g. shrinking think times to stress the server) change the workload the implementations optimized for (rejected mid-experiment in-video [VERIFIED] transcript §[02:02]–§[03:02]) — record as a different experiment, never as the same score.
- Generator-headroom rule: a ceiling with idle target CPU + empty generator headroom is a generator artifact until proven otherwise [VERIFIED observation → EXPERIMENTAL HYPOTHESIS transfer] #3 §2.4. §6.2 gives the falsifier.

### 5.4 Per-level log table (required — J11 shape)

| VUs | k6 avg/med/p95/p99/max | err% | k6 exit | srv CPU% | srv RSS/free | gen CPU/RAM | mode (nginx/direct) | gate (suite line) | verdict |
|---|---|---|---|---|---|---|---|---|---|
| 1000 (S0) | … | … | … | … | … | … | … | `passed=42 failed=0` | warmup |
| … probe … | … | … | … | … | … | … | … | same build | … |
| N (S3) | … | … | 0 | … | … | … | … | same build | PASS |
| N+250 | … | … | ≠0 | … | … | … | … | same build | FAIL (limit) |

## 6. Bottleneck-ID procedure (systematic-debugging shape: evidence per boundary, one hypothesis, verify)

### 6.1 Principle

[VERIFIED process] `systematic-debugging` skill: no fixes without root-cause evidence; reproduce → gather evidence at each component boundary → single hypothesis → smallest change → verify. For benchmarks the boundaries are generator → network/conntrack → HTTP/conn handling → app threads/pool → SQLite → GC/heap. Log what enters/exits each boundary per hold (§4.2) BEFORE proposing the next variable.

### 6.2 Generator-vs-server isolation (ask first on every ceiling)

Falsifier sequence on any FAIL or suspicious PASS-with-idle-server:

1. Generator saturation? Check per-k6-VM CPU/RAM during the hold. In-video precedent: k6 machine ran out of RAM simulating users; fix was 4 generator VMs, NOT a bigger assertion [VERIFIED] transcript §§[02:02]–[03:02]. If any generator VM is near its limit → INVALID run: add generators, re-run S3, do not score the red.
2. Port drain? Check `connect() failed` + TIME_WAIT + ephemeral counts (§4.5). If drain-shaped → INVALID run: drain-wait / fresh pod, re-run.
3. If generators are clean and the server is idle at the ceiling → harness artifact until proven otherwise (#3 §2.4 transfer). Do not claim a server limit.

### 6.3 Conntrack-vs-app isolation

Platform ceiling precedent [VERIFIED] transcript §§[15:11]–[16:12]: Linux connection-tracking table sized for 65,000 entries; beyond ~64,000 the kernel drops packets; author refused kernel tuning mid-experiment (rules forbid it — `README.md:59-62` rules 10–11). Rust hit 64k with half CPU free — true ceiling unknown.

- On any ceiling above ~60k: check conntrack table occupancy + kernel drops + forced-reconnect counts (Go §4.4 / Zig §4.11 / Axum §4.10 shapes [VERIFIED] #3 §§4.4/4.10/4.11). Full table or drop counters → platform/harness ceiling, not an app result. Record and stop scoring upward.
- Self-imposed conn limits (Go >16k forced reconnects, Zig 60k hard cap [VERIFIED] #3 §§4.4/4.11) fail via error budget with healthy latency/CPU — check error-reconnect correlation before blaming CPU.

### 6.4 App-internal isolation (profile order, not presumption)

Measure in this order (catalog profile order, `OPTIMIZATIONS.md` measure-first policy [VERIFIED] #4 §3.9):

1. SQL first: JFR + `EXPLAIN QUERY PLAN` (fresh + grown data) + per-statement timings. Correlated-count feed cost is the SPEC's own reference shape (characterization, not a finding [VERIFIED] #4 §3.5; `SPEC.md:46-53`).
2. Pool/queue second: pool-wait histograms, `busy_timeout` expirations, queue time, write p99. Known shape: pool-4 vs ~200 Tomcat threads parks request threads on checkout under write pressure [VERIFIED shape, unmeasured effect] #4 §3.7. "More threads ≠ more SQLite writers" (single-writer reality [VERIFIED shape] #4 §§3.1/3.7; #6 C2).
3. HTTP/conn third: accept queue, `EMFILE`, eviction rate, idle-conn memory (DX-3/E-edge falsifier).
4. Stack/threading fourth: ONLY after a profile shows thread-blocking (not SQL) as the bottleneck (E-stack gate [VERIFIED stance] #2 V18; #5 E2). Default MVC+JDBC baseline stands until then.
5. JSON/auth alloc last: allocation flame graph only after SQL ruled out (E-jvm tail [VERIFIED shapes, EXPERIMENTAL cost] #6 D2). One-line shapes (`Pattern`, mapper singleton, `ThreadLocal<Mac>` with `remove()`) are safe-candidate, not free wins (OPT-11).

### 6.5 Bottleneck catalog (what failure looks like — video-attributed, Java transfer by experiment)

[VERIFIED per-submission attributions] #3 §6.1: CPU cliffs (Erlang/Python/C#), conn-churn error fails (Go/Axum/Zig), tail-only fails (PHP/Fortran), structure-notch (C lock/128-batch guess [EXPERIMENTAL HYPOTHESIS, author-unprofiled] #3 §4.8), cliff+errors (Bun), platform ceiling (Rust/C++ conntrack). Use as pattern library for §6.4 readings, never as Java predictions.

## 7. Single-variable experiment template (the only way runs are recorded)

### 7.1 Blank template (copy-paste per run — runnable without re-derivation)

```markdown
## EX-<id>: <one-line variable>
- Variable (ONE): <e.g. pool 4 → 1W+3R>
- Fixed: <commit SHA + SQL + pragmas + pool-except-variable + stack + JVM flags + mode + VUS plan>
- Gates per run: `test/test.sh` 100% on fresh DB (paste: passed=__ failed=__) + byte-compare + rule 5/6 code-read (caches/durability/schema/JWT) + pragma assertions
- Harness: S0–S4 (§3) + k6 invocation (paste full command) + generator count + drain-wait + JFR on/off
- Report: per-level table (§5.4: p95/p99/err + CPU + RSS at pass AND fail+1) + profile artifact path
- Falsifier / stop rule: <condition under which the row closes and the baseline stands>
- Maps to: <DX-n / X-n / OPT-nn>
- Verdict: <candidate held | baseline stands | needs rerun (harness artifact §6.2/§6.3)>
```

- Rules: one variable per run; every run carries its falsifier; any run missing the gate line, the k6 command, or the fail+1 row is INCOMPLETE (not a result); multi-variable optimization is prohibited (acceptance criterion, #8 body).

### 7.2 DX-1..DX-8 mapping (inherited from #9 §5 — variables, fixed sets, gates, falsifiers survive verbatim)

| ID | Variable (one) | Fixed | Gates per run | Falsifier / stop rule | Maps to |
|---|---|---|---|---|---|
| DX-1 | Serialization: reference SQL vs per-row `json_object`+Java join vs `json_group_array` aggregate | Same pool/pragmas/stack/mode | OPT-05 goldens + byte compare + alloc profile | No alloc/p99 delta → keep reference query | X1 (OPT-12/13) |
| DX-2 | Writer placement: Hikari-4 vs 1W+3R split vs single Java writer queue | Same SQL/pragmas/stack | Commit-before-respond re-assert + busy-timeout expirations + queue-time + write p99 | Reader starvation or queue-p99 regression → keep pool-4 | X2 (OPT-15/16) |
| DX-3 | Deploy mode: Nginx vs direct, same build | Same SQL/pool/stack/JVM | Accept queue, `EMFILE`, eviction rate, p99 vs CPU; 65 s idle rule both modes | Descriptor/idle-memory blowup or cap hit → stay behind Nginx | X3 (OPT-20) |
| DX-4 | Threading flag: platform vs `spring.threads.virtual.enabled=true` (A-lane; C-lane repeats once) | Identical SQL/pragmas/pool | Threads, pool-wait, p99 spread | No threads/pool-wait/p99 delta → platform stands | X5 (OPT-17; E-vt) |
| DX-5 | Stack lane: A vs B vs C-live-shape, profile-gated | Same SQL/pragmas/pool/mode from DX-1..DX-3 winners | JFR allocation + pool-wait counters; C-live shapes line-reviewed BEFORE the run | No thread-blocking in profile → A stands, B/C-live closed | X4 (OPT-18/21; E-stack) |
| DX-6 | Group commit with wait-guard (+ atomic-like sub-row OPT-14) | Same lane as winner | Realistic + write-heavy mixes; 404/200/201 + idempotency gates; failure-atomicity designed | No write-p99 win at realistic mix, or atomicity cost → decline (author default respected) | X6 (OPT-19/14) |
| DX-7 | GC×heap: Serial/G1/Parallel/ZGC × 256/384/512 m + NMT, SQLite+Nginx resident | Same lane/SQL/load | p99 + GC logs + native-mem; stay well under 2 GB | No p99/GC-time delta → keep `-Xmx512m` default posture | X7 (OPT-22/23) |
| DX-8 | Minimal-extreme probe: D vs A-winner (late-stage, headroom-gated) | Same SQL semantics/mode | FULL SPEC-edge audit of hand parser first; error-budget + reconnect-rate gates | Loop-stall p99 under write burst, or any SPEC-edge miss → D closed permanently | High-risk (OPT-29..33) |

- Order with blocking edges (inherited X-order, #7 §4): DX-1 + DX-2 first (independent SQL/pool) → DX-3 + DX-4 (need SQL/pool settled) → DX-5 (needs thread-blocking profile from DX-1..DX-4) → DX-6 (needs pool shape from DX-2) → DX-7 (needs alloc profile) → DX-8 (headroom-gated, #9-cleared). Explicitly NOT scheduled without profile indictment: OPT-24..OPT-28 tails (DX-side notes only), OPT-34..OPT-44 (never).

## 8. SQLite-discipline + durability declaration (per-run checklist)

- Rule 5 (no cross-request caching) [VERIFIED prohibition] `README.md:42-45` + video S5 ruling (#3 §7/S5): every request re-queries SQLite; JWT re-verified per request; no response/query caches, no table copies, no remembered verifications. Allowed reuse (plan/state, not data): prepared-statement reuse, mapper singleton, `ThreadLocal<Mac>`, OS page cache + `mmap` (the database, not a copy). Code-read gate per variant (OPT-34).
- Rule 6 (durable commit before response; WAL + NORMAL-or-stronger) [VERIFIED floor] `README.md:46-49` + sqlite.org WAL/synchronous docs (#6 C1): pragmas enforced per pooled conn (`Application.java:27-30` pin shape) + asserted on all conns (`ApiTest`-style); single-statement auto-commit or wait-guarded group commit ONLY (unguarded respond-before-commit prohibited, OPT-36). Session-only knobs (`cache_size`-class) must be set per pooled conn — re-assert per variant (#6 C5).
- Required run-log declaration (every §7 row): `journal_mode / synchronous / foreign_keys / busy_timeout / cache_size / mmap_size / autocheckpoint-schedule` + `FULL`-vs-`NORMAL` note. NORMAL carries the video asterisk (valid, passes rules, weaker than FULL on power loss [VERIFIED] transcript §[18:13]; normative form `README.md:46-49` rule 6 — #3 §7/S6): document it, never silently upgrade the claim to FULL durability. FULL-cost probe (OPT-24) is one run, then back to NORMAL, never a direction change without owner decision.
- Schema fixed [VERIFIED prohibition] `schema.sql:1-2` (no new tables/columns/indexes/triggers; 3 indexes only `schema.sql:24-26`); migration-diff gate per variant (OPT-37).
- Box rules [VERIFIED prohibitions] `README.md:59-62` rules 10–11: build from source on the box, pinned versions, no prebuilts, start ≤60 s; no kernel tuning / CPU pinning / box changes (in-process GC/thread/allocator/pragma settings stay fair game). Conntrack tuning refused mid-experiment in-video [VERIFIED] transcript §§[15:11]–[16:12] (OPT-38).

## 9. Quarantined figures (never quote as targets) + prohibited list

Inherited verbatim from #7 §5 / #9 §6 (verdicts preserved, mentioned only to quarantine):

| ID | Claim | Verdict | Resolution |
|---|---|---|---|
| OPT-40 | Prior-video table (Spring 5.1k/10.25k, Go 11.75k, Rust 14.05k) as Java targets | OUTDATED | Third-party summary of `sQXFhh_PiG4`; current round peaks 64k (#2 V14; #5 V14). Re-date or drop |
| OPT-41 | `~0.77 ms` CPU/request box budget | UNSUPPORTED | Arithmetic on summarized numbers, never measured on Java path (#2 V16). Drop or re-derive from Java profiles |
| OPT-42 | PR33 `~20%` / 38k-vs-31.75k / interim "not that big" as results | UNSUPPORTED | Commit/merge messages + comments 8/11, no method/raw data/review (#4 §9; #6 A3). Never quote; E-stack re-measures |
| OPT-43 | PR58 laptop table (128k–416k users, µs/CPU deltas, ~40k req/s ceiling) as droplet figures | UNSUPPORTED (as droplet figures) | Apple-Silicon Docker `--cpus 1`, different arch/noise (#6 A4). Shapes only; numbers never transfer |
| OPT-44 | `~32k users` ephemeral-port threshold as universal constant | PARTIALLY VERIFIED mechanism / UNSUPPORTED threshold | PR33 `6684ebf` message, box-unmeasured (#6 F2). Adopt drain discipline (OPT-09); measure on droplet |

- Prohibited techniques (never tested, code-read gates per variant — #7 §2.4 / #9 §6 reused): OPT-34 (caches/copies/remembered verifications), OPT-35 (OFF/DELETE/MEMORY/in-memory), OPT-36 (unguarded batching), OPT-37 (schema changes), OPT-38 (kernel/pinning/box), OPT-39 (JWT downgrade). All [VERIFIED prohibitions].
- Microbenchmark-confusion guard: SSD-vs-memory anecdotes, laptop-local µs/CPU deltas, and prior-video defaults-round numbers are not droplet evidence (forum caution #6 C5; OPT-43/OPT-40). Only measured holds on the target box under this harness count.

## 10. License posture (checked, no new burden here)

Inherited API-verified table (#6 §7; #7 §6; #9 §7 — reused, not re-derived): repo `arjaythedev/twelve-dollar-server-challenge` MIT (study + re-implement ideas; verbatim copies keep notice); HikariCP / xerial/sqlite-jdbc / Netty / Jackson Apache-2.0 (dependency-allowed); SQLite engine public domain; OpenJDK/Temurin GPLv2+CE (flag use clean; no JDK redistribution planned); Nginx 2-clause BSD (config reference); k6 AGPLv3 harness-only use (binary use, not linked — findings carry no distribution burden). This strategy doc introduces no dependency — nothing to clear beyond inheritance. Unsafe/obsolete/undocumented tricks adopted here: none (DX-8 high-risk rows carry falsifiers + edge-audit gates, same guard as #6 §7).

## 11. Verification appendix (commands run this session)

- `git status --short --branch` → on `research/java-analysis` tracking `origin/research/java-analysis`, clean except pre-existing untracked `.idea/` (left alone, never added); `git log --oneline --graph -15` linear from `026bdb6`; `git branch -vv` → `research/java-analysis @ 41d7d4b` (= `main` = `origin/main` = `origin/research/java-analysis`), `backup/main-baseline-2026-10-13 @ 026bdb6` intact, `reference/spring-boot-pr33 @ 7a9cb99` untouched; `git remote -v` origin=fork, upstream=original, no upstream push. `git rev-parse` re-confirmed all three SHAs before writing.
- Precondition: `gh issue list --milestone Task-2-Research` shows #8 OPEN, #2/#3/#4/#5/#6/#7/#9 CLOSED, #10/#11/#12 + Epic #1 OPEN untouched. If #8 had not been OPEN the session would have stopped (it was OPEN).
- Claimed #8 via `gh issue comment 8` before any work (parallel-session guard).
- Base refs read in full (§0.2 list); recon leads re-verified against primary files (§0.5 table); #9 §0.6/§5 reconciliation recorded with zero delta (§0.6); DX-1..DX-8 mapped 1:1 to #9 §5 (§7.2).
- Runnable-without-rederivation check: §§1/2.6/3/7.1 hold copy-paste commands (gate, k6 invocation, S0–S4 steps, blank template); a later agent executes §§1→3→7 without reopening prior docs.
- No-invented-targets check: no suite/load executed; every numeric figure attributed to SPEC/threshold/transcript/author/laptop source with UNSUPPORTED/OUTDATED quarantine where due (§9); validated-Java count stays 0.
- Classification check: every significant claim carries one of the six verdicts (§§0–10, tables + inline tags).
- License check: §10 inherits API-verified table; k6 AGPLv3 noted as harness-only.
- Forbidden-word grep over draft + final file before commit — empty both times.
- `git add` ONLY `research/findings/BENCHMARK_STRATEGY.md`; commit `docs(research): benchmark_strategy closes #8`; push `origin research/java-analysis`; ff-merge to `main`; push `main` (or STOP on non-ff).

*No implementation started. Next: #10 FINAL_RESEARCH_REPORT now UNBLOCKED (#8 + #9 both closed after this session's close); #11/#12 untouched for their sessions.*
