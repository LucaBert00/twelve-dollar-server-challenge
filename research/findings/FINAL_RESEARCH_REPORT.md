# FINAL_RESEARCH_REPORT — Task-2 research synthesis (Wayfinder-9/11, closes #10)

- Ticket: #10 `[Wayfinder-9/11] FINAL_RESEARCH_REPORT -- synthesis` (OPEN at writing time)
- Output: this file only (`research/findings/FINAL_RESEARCH_REPORT.md`). No app code.
- Blocked by Issues 1–8 (#2/#3/#4/#5/#6/#7/#8/#9, all CLOSED — precondition met, see §12).
- #11 IMPLEMENTATION_ROADMAP and #12 Verification are OPEN in later sessions: this doc is their
  self-contained input. Nothing here starts implementation.
- Truth hierarchy observed throughout: `SPEC.md` + `test/test.sh` (42 checks) >
  transcript > AI reports > PR33/PR58 discussion > external.
- Every significant claim below carries one of `VERIFIED` / `PARTIALLY VERIFIED` / `INCORRECT` /
  `UNSUPPORTED` / `OUTDATED` / `EXPERIMENTAL HYPOTHESIS` with primary evidence (repo URL + commit
  SHA + file + line, doc URL, or measured output). Needs-experiment rows carry method, never a
  conclusion. No benchmarks invented (validated Java count stays 0).
- Synthesis only: this doc consolidates Issues 1–8. It does not re-derive verdicts, re-score reports,
  or introduce new claims beyond Issues 1–8 without fresh primary evidence (none introduced —
  new-claim count: 0).

## 0. Method, skills, inputs, filters, parent-fidelity

### 0.1 Skills used (ticket mandate, in order)

Invoked via skill tool this session: `academic-deep-research` (verdict-table rigor, per-claim
classification, contradiction disclosure — applied as synthesis discipline over closed docs, not as a
new literature search), `documentation-writer` (Diátaxis Explanation + Reference mixed: §§1–5, §9
explanation; §§2, §§6–8, §§10–11 reference), `verification-before-completion` (gate before every
claim below and before commit/push/close).

Standing map notes skills — used or skipped with reason (none claimed unless invoked):
`grilling` skipped (scope fixed by #10 body + handed-over leads; no open scoping questions),
`domain-modeling` skipped (no new domain terms — all terms reused from #2–#9 with identical
meaning), `research`/`deep-research` satisfied through `academic-deep-research` synthesis pass (no
new discovery owned by #10 — consolidation of closed docs only), `to-spec`/`to-tickets` skipped
(output is one findings doc, not a new spec or ticket set; §7 rows are verifiable slices consumed
by #11, not new tickets), `java-springboot` skipped (no Spring code judged; pin facts reused from
#4 with fresh SHA re-verification in §12), `github`/`git-workflow` satisfied by standard linear ops
verified with fresh `gh`/`git` commands per §12.

### 0.2 Inputs read (in full, fresh this session)

- `docs/WAYFINDER_MAP_PROMPT.md` §4 (global hard rules).
- Issue #10 body + 1 comment (Depends note: blocked until Issues 1–8 closed) + Epic #1 body
  (order + Decisions so far) — via fresh `gh issue view` / `gh issue list`.
- `research/analysis/REPORT_COMPARISON.md` (246 lines, #2) + `research/analysis/FACT_CHECK.md`
  (145 lines, #5) + `research/analysis/VIDEO_ANALYSIS.md` (590 lines, #3) +
  `research/analysis/SPRING_BOOT_CODE_REVIEW.md` (370 lines, #4).
- `research/findings/GITHUB_RESEARCH.md` (348 lines, #6) +
  `research/findings/OPTIMIZATION_CATALOG.md` (138 lines, #7) +
  `research/findings/JAVA_ARCHITECTURE_DECISION.md` (~419 lines, #9) +
  `research/findings/BENCHMARK_STRATEGY.md` (349 lines, #8).
- `SPEC.md` (84 lines), `schema.sql` (26 lines), `bench/load.js` (70 lines),
  `test/test.sh` (126 lines, `passed/failed` gate at `test/test.sh:125-126`),
  `docs/PROJECT_CONTEXT.md`, `research/sources/SOURCE_MANIFEST.md`.
- Transcript `research/inputs/I-Asked-Expert-Programmers-to-Max-Out-a-12-Dollar-Server_QGWKCaglSBI_transcript.md`
  — context only (methodology sections + #3 J1–J11 reuse); per-language numbers reused only via #3,
  never re-derived here.

### 0.3 Pins re-verified (fresh, this session — not trusted from parents)

- `research/java-analysis` = `main` = `f4e25f8`; `backup/main-baseline-2026-10-13` = `026bdb6`
  intact; `reference/spring-boot-pr33` = `7a9cb99` untouched (see §12).
- Pin spot-check: `application.properties` 6 lines (no `spring.threads.virtual` key);
  `git grep spring.threads` over the submission tree at `7a9cb99`: 0 hits — flag-absent
  [VERIFIED] again this session.
- Issue states: #10 OPEN; #2/#3/#4/#5/#6/#7/#8/#9 CLOSED; #1/#11/#12 OPEN untouched (see §12).

### 0.4 Forbidden-word filter

Case-insensitive grep for the forbidden term (base prompt §4.4) run over the draft and again over
the final file before commit — empty both times (see §12). Where that concept is needed, this doc
writes `minimal-diff senior-dev mode`.

### 0.5 Parent-fidelity record (deltas stated explicitly)

| Hand-over (session goal §3) | Disposition this session |
|---|---|
| Verdict table V1–V24 (#5, 0 classification changes) | Adopted verbatim — 24/24 rows, 0 classification changes, 0 new claims (§2) |
| Arch recommendation — baseline Option A Plain Java, C-pin `7a9cb99` as correctness oracle, B profile-gated, D headroom-gated — plus 44-cell evaluation + DX-1..DX-8 with falsifiers (#9 §§3–5) | Adopted verbatim — recommendation + DX-1..DX-8 rows + falsifiers + order carried in §§7–8; 44-cell condensed per-axis with pointers (§7) |
| Benchmark method — gate → S0–S4 limit-search → stats/J11 → EX-template (#8 §§1–7, zero delta vs #9 §0.6, ONE method) | Adopted verbatim — ONE method carried in §8; delta vs #9 §0.6: none |
| Catalog splits OPT-01..OPT-44 (11/17/5/6+5) + ranked X1–X7 with blocking edges (#7 §§2–4) | Adopted verbatim — splits + ID map + X1–X7 rank/order carried in §6 |
| Quarantines OPT-40..OPT-44 never quoted as targets | Honored — quarantined figures appear only in §10 to quarantine, never as results |

Total deltas forced by evidence: 0.

### 0.6 How to read this file

- §§1–2 are the VERIFIED core + the full V1–V24 verdict table: accept and build on them.
- §§3–5 summarize the video, PR33, and GitHub evidence: observation sources with transfer methods.
- §§6–8 are the decision payload for #11: catalog map + architecture + benchmark method + DX rows.
- §9 lists every open question explicitly — nothing is silently resolved.
- §§10–11 are gates: prohibited techniques + quarantined figures + license posture.
- §12 records the verification steps run. Validated Java bench count in this file: 0.

## 1. Ground truth (condensed reference — VERIFIED core V1–V10)

All rows [VERIFIED] unless noted. Authority: `SPEC.md` + `test/test.sh`; transcript corroborates
where cited; PR33 pin corroborates where cited.

- **Box**: 1 vCPU (shared), 2 GB RAM, no swap, Ubuntu 24.04 x86_64, 50 GB storage; OOM = loss;
  no kernel tuning, no CPU pinning (in-process GC/thread/allocator/pragma settings fair game).
  Evidence: `docs/PROJECT_CONTEXT.md:5-6`; `SPEC.md:8-14`; `README.md:52-62` rules 10–11;
  transcript §[01:01]. (V1)
- **API**: 5 endpoints (`GET /health`, `GET /feed`, `GET /posts/:id`, `POST /posts`,
  `POST /posts/:id/like`); compact JSON, exact key order, post object
  `id/body/created_at/author/like_count`; `like_count` live per request; `created_at` stored-text
  passthrough (ms + `Z`); new rows `strftime('%Y-%m-%dT%H:%M:%fZ','now')`.
  Evidence: `SPEC.md:23-43`; `test/test.sh:52-57,95-123` golden + read-back checks. (V2)
- **Auth**: `Bearer` HS256-only; verify signature + `exp` every request; `sub` positive-integer
  string + `username` string or `invalid token payload`; auth-before-id ordering
  (`POST /posts/abc/like` no token → 401, valid token → 400).
  Evidence: `SPEC.md:72-79`; `test/test.sh:68-83,113`; pin `Authentication.java` shapes (#4 §3.4). (V3)
- **Create**: trim-before-validate; 500 code points after trim (count code points, not UTF-16);
  malformed JSON → 400; schema `CHECK (length(body) BETWEEN 1 AND 500)`.
  Evidence: `SPEC.md:67-69`; `schema.sql:13`; `test/test.sh:85-93,96-103`. (V4)
- **Likes/health/feed**: first like 201, repeat 200 `already_liked:true`, missing post 404 (app must
  check existence — SQLite FKs off by default); health `SELECT 1` then 200 or 503; feed 20 newest
  `ORDER BY created_at DESC, id DESC`.
  Evidence: `SPEC.md:38-42`; `test/test.sh:60-61,110-123`; pin `PostRepository.java` shapes (#4 §3.5). (V5)
- **Schema/rules**: schema fixed (3 tables + 3 indexes, no additions); no cross-request result caches
  (prepared-statement/plan/state reuse allowed, result memoization forbidden); WAL +
  `synchronous=NORMAL`-or-stronger with commit-before-respond (group commit legal only with the
  wait-guard); suite entry is `test/test.sh` (the `test/run.sh` filename in older AI prose is
  [INCORRECT-minor], V21 — `test/run.sh` is the submission-runner path in PR docs, not the suite).
  Evidence: `schema.sql:1-2,24-26`; README rules 5/6 (#4 §7); `test/test.sh:125-126`. (V6/V21)
- **Bench**: k6 closed-model `ramping-vus` (60 s ramp → 5 min hold → 30 s down); VU = browsing loop
  (feed → think 3–7 s → get → think 3–8 s → 15 % like → 2 % post → idle 5–15 s); thresholds
  p95 < 500 ms, p99 < 1000 ms, errors < 1 %; score = max VUs passing a 5-min hold; k6 on a separate
  machine; limit-search warm-up 1000/2 min → probe from 2500 doubling to fail → narrow ±250 →
  5-min confirm → step down 250. Evidence: `bench/load.js:11-43`; `README.md:100-105`;
  transcript §[02:02]. (V7/V8 — claudeJava12's partial read noted in V7, resolved: chatWork §7 is
  the method reference)
- **PR33 pin**: OPEN, unmerged, 0 reviews, 7 conversation comments at freeze (13/0/0 live at #4
  writing time — see §4), no benchmark numbers; head `7a9cb99`; MVC/Tomcat behind Nginx;
  virtual-threads flag absent; WebFlux absent; Hikari 4/1; keep-alive 70 s (satisfies ≥65 s rule);
  WAL + NORMAL + FK-on + busy 5 s; author self-declares unoptimized baseline.
  Evidence: `SOURCE_MANIFEST.md:33-41`; `SPRING_BOOT_PR33.md`; fresh `git show` (§0.3). (V9/V10)
- **Seed**: 50k users / 500k posts / 2M likes + 20k pre-signed tokens.
  Evidence: `SOURCE_MANIFEST.md:49`; `bench/load.js:24`. (V22)
- **Video round**: QGWKCaglSBI is the current round (13 languages, 26/28 benchmarked, best 64,000 —
  Rust io_uring + C++ tied, conntrack ceiling); "zero Java submissions" is time-bounded to the
  benchmarked video set (no contradiction with PR33, opened Oct 10–13, unmeasured); headroom (P99 +
  CPU + RAM at the shared ceiling) is the tiebreaker, not the tie.
  Evidence: transcript §§[03:02]–[19:14]; #3 §§4.10/4.12. (V23/V24)

## 2. Verdict table V1–V24 (carried verbatim from #5 — 0 classification changes)

Source: `research/analysis/FACT_CHECK.md` §1, condensing `research/analysis/REPORT_COMPARISON.md`
§5. Verdicts copied verbatim; Evidence trimmed to the primary citation; Resolution is the parent
disposition. Row count: 24. Classification changes: 0. New claims: 0.

| # | Claim | Verdict | Primary evidence | Resolution |
|---|---|---|---|---|
| V1 | Box 1 vCPU / 2 GB / no swap / Ubuntu 24.04 | VERIFIED | `docs/PROJECT_CONTEXT.md:5-6`; `SPEC.md:8-14`; transcript §[01:01] | Accept; sizing gate |
| V2 | 5 endpoints, compact JSON, exact key order, live `like_count`, text `created_at` passthrough | VERIFIED | `SPEC.md:23-43`; `test/test.sh:52-57,95-123` | Accept; byte-compare in CI |
| V3 | Auth-before-id; HS256-only; exp-then-payload errors | VERIFIED | `SPEC.md:72-79`; `test/test.sh:68-83,113`; `Authentication.java` | Accept; router order is a correctness gate |
| V4 | Trim-then-validate; 500 code points; malformed-JSON 400 | VERIFIED | `SPEC.md:67-69`; `schema.sql:13`; `test/test.sh:85-93,96-103` | Accept; count code points, not UTF-16 |
| V5 | Like 201/200 + existence check; health `SELECT 1`; feed 20 newest `created_at,id DESC` | VERIFIED | `SPEC.md:38-42`; `test/test.sh:60-61,110-123`; `PostRepository.java` | Accept |
| V6 | Schema fixed; 3 indexes; no-cache vs permitted page-cache/mmap/prepared | VERIFIED | `schema.sql:1-2,24-26`; SPEC rules; PR33 `OPTIMIZATIONS.md` | Accept; no result/token caches |
| V7 | Bench thresholds + VU-loop + separate generator | VERIFIED (chatWork/chatNormal); PARTIALLY VERIFIED (claude — mix admitted unread) | `bench/load.js:11-43`; transcript §[02:02] | Accept chatWork §7 as method reference |
| V8 | Warm-up 1000/2 min → 2500 doubling → ±250 → 5-min hold → −250 retry | VERIFIED | Upstream README method + `bench/load.js` stages/thresholds; transcript §[02:02] | Accept |
| V9 | PR33 head `7a9cb99`, 4 commits, 35 files, OPEN/unmerged/0 reviews/7 comments/no numbers | VERIFIED | `SOURCE_MANIFEST.md:33-41`; `SPRING_BOOT_PR33.md:§2-6,§8` | Accept; cite SHA on reuse |
| V10 | No virtual-threads flag; WebFlux absent; Hikari 4/1; keep-alive 70 s; WAL/NORMAL/FK-on/busy-5 s | VERIFIED | `application.properties` (6 lines); `Application.java`; `pom.xml` | Accept; flag-absent claims true, effect unproven |
| V11 | "Virtual threads will surely help" / "WebFlux next" | UNSUPPORTED | `SPRING_BOOT_PR33.md:§8` (no numbers); author defers | needs-experiment (E1–E2 / DX-4/DX-5) |
| V12 | "NIO/raw-Netty better here" | UNSUPPORTED | Same §8 (testing-pending, no profile/diff) | needs-experiment; plausible for conn-count only (E3 / DX-5) |
| V13 | Nginx 14–20 % CPU | PARTIALLY VERIFIED | Repo-reported original-video observation, not Java measurement | needs-experiment behind-vs-direct (E4 / DX-3) |
| V14 | Prior-video table 5.1k/10.25k/11.75k/14.05k as current guidance | OUTDATED | Third-party summary of `sQXFhh_PiG4`; current round peaks at 64k | Re-date to prior video; never a Java target (OPT-40) |
| V15 | DB-post table (Go 13.4k SQLite) as current guidance | PARTIALLY VERIFIED (source real, context adjacent) | Oct-13 DB post, Go app, same box class — not the QGWKCaglSBI round | Keep as NORMAL-vs-FULL evidence only; not a Java target |
| V16 | `~0.77 ms` CPU/request box budget | UNSUPPORTED | Arithmetic on summarized numbers, never measured on Java path | Drop or re-derive from Java profiles (OPT-41) |
| V17 | JDK+vthreads vs Netty vs Boot non-conclusion | VERIFIED stance | PR33 baseline admission + measure-first rule | Accept as decision policy |
| V18 | MVC+JDBC baseline; WebFlux only after thread-block evidence | VERIFIED stance | Blocking Xerial driver + single-writer reality | Accept baseline order (E-stack gate / DX-5) |
| V19 | Pool/reader-writer/vthread/JVM/pragma sweeps as isolated candidates | EXPERIMENTAL HYPOTHESIS (honest) | No Java bench in evidence; PR33 lists same as unmeasured | Experiment backlog, not results (E1/E5/E6; DX-2/DX-4/DX-7) |
| V20 | FFM binding, PGO SQLite, cache-shrink, checkpoint deferral, hand-written JSON | EXPERIMENTAL HYPOTHESIS | Transcript C/C++/Bun shapes exist; Java transfer unmeasured | Late-stage only, profile-gated (E7; DX-8/DX-1) |
| V21 | Test filename `test/run.sh` | INCORRECT (minor) | Repo root holds `test/test.sh` (126 lines) | Correct to `test/test.sh` everywhere |
| V22 | Seed 50k/500k/2M + tokens | VERIFIED | `SOURCE_MANIFEST.md:49`; `bench/load.js:24` | Accept |
| V23 | "Zero Java submissions" vs PR33 existence | VERIFIED both (time-bounded) | Transcript §[03:02] vs PR33 Oct 10–13 open/unbenchmarked | No contradiction; Java track = baseline + greenfield |
| V24 | Conntrack 65k ceiling; Rust/C++ 64k tie; C++ higher p99/CPU | VERIFIED | Transcript §§[15:11]–[19:14] | Accept; headroom is the tiebreaker (J11 reporting policy) |

Needs-experiment backlog E1–E8 (`FACT_CHECK.md` §2, methods preserved): E1 vthreads A/B; E2 WebFlux
only on thread-block profile; E3 NIO/raw-Netty profile-gated; E4 Nginx-vs-direct same-build A/B;
E5 pool sweep + `busy_timeout` + write p99; E6 heap×GC sweep inside 2 GB; E7 FFM/PGO/mmap/
checkpoint/JSON late-stage; E8 group commit with wait-guard (second-order at ~100 writes/s under
the realistic mix — arithmetic from `load.js` probabilities, not a bench result). All map to DX
rows in §8.

Per-report ranking reused from #2 (not re-scored): chatWorkJava12 52/55 > chatNormalJava12 43/55 >
claudeJava12 40/55; chatWork §7 is the benchmark-method reference; claude §5 numbers are the
OUTDATED example (V14). Full scoring context: `research/analysis/REPORT_COMPARISON.md` §§2–3.

## 3. Video reconstruction (from #3 — observations VERIFIED, Java transfer EXPERIMENTAL)

Full timestamped source: `research/analysis/VIDEO_ANALYSIS.md` §§1–6. What #11 needs:

- **Method**: limit-search with pass criteria p95 < 500 ms, p99 < 1000 ms, errors < 1 %, sustained
  5 min; k6 on a separate machine; fastest entries saturated the generator first (fixed with 4
  generator VMs — Java runs must watch generator CPU/RAM and never score a generator artifact).
  [VERIFIED] transcript §§[02:02]–[03:02]; #3 §§2.2–2.4.
- **Recurring five** (author takeaways, [VERIFIED] as observed; transfer per §8 DX rows):
  drop Nginx / prepare-once + single DB connection / JSON built in SQLite / group commit with the
  wait-guard / strip framework layers after profiling. #3 §5.
- **Java lessons J1–J11** (#3 §9, transfers EXPERIMENTAL HYPOTHESIS unless noted): J1 Nginx-first
  then direct (DX-3); J2 single-owner + prepare-once baseline, pool/split experimental (DX-2);
  J3 SQLite-built JSON byte-exact (DX-1); J4 group commit second-order (DX-6); J5 profile-gated
  strip (DX-5); J6 allocation discipline + heap×GC sweep (DX-7); J7 long-running warm process
  [VERIFIED baseline posture]; J8 readers-parallel/single-writer + vthreads/NIO A/B ungated-winner
  (DX-4/DX-5); J9 SQLite-internal wins late-stage (DX-8/tails); J10 connection handling first-class
  (envelope sizing from the start, DX-3); J11 headroom reporting [VERIFIED policy — every DX run
  reports p95/p99/err + CPU + RSS at pass AND fail+1].
- **SPEC-conflict resolutions S1–S8** (all resolved for SPEC/test, zero left open — #3 §7): S1
  `LimitNOFILE`/bind capability vs no-kernel-tuning (both hold); S2 SPEC ~15k is a sizing
  expectation, video 64k is ceiling-evidence (size for 64k idle + 65 s rule); S3 SQLite-JSON allowed
  only if golden-byte-exact; S4 batching allowed only with commit-wait guard; S5 OS page cache +
  `mmap` are the database (private copies prohibited); S6 NORMAL valid but weaker than FULL
  (document the asterisk, never claim FULL durability); S7 JWT edge table per SPEC (HS256-only,
  exp, sub/username, auth-first); S8 SPEC out-of-scope note wins (16 KB, 2^53, hex/exponent, wrong
  methods).
- **Scores by language** (video observations, never Java targets): Ruby 14k (Iodine+YJIT, tail cliff);
  Erlang 15k (Cowboy, CPU); Python 22k (Uvicorn-direct strip doubled 11k→22k, CPU); Go 22k (group
  commit, connection bottleneck at 78 % CPU); C# 32k (low-level Kestrel, CPU 93 %); Fortran 38k
  (SQLite-JSON + group commit); PHP 40k (Swoole long-running, tail-only fail); C 46k (io_uring + PGO
  SQLite, structure-notch miss by 43 ms, lock/128-batch cause unprofiled); Bun 47k (raw TCP +
  SQLite bytes, cliff); Rust Axum 43k (forced closes) vs io_uring 64k (P99 224 ms, ~50 % CPU,
  conntrack ceiling); Zig 61k (self-imposed 60k conn limit, 1.02 % errors); C++ 64k (page-cache
  shrink + `mmap` + off-path checkpoints, P99 910 ms vs Rust 224 ms — Rust wins on headroom).
  #3 §§4/6.1.
- **Video-ID separation** (reuse, do not re-derive — #3 §8): QGWKCaglSBI current vs sQXFhh_PiG4
  prior (OUTDATED as guidance) vs Oct-13 DB post (adjacent, NORMAL-vs-FULL context only) vs
  post-video repo changes (PR33 drift/PR58 — §§4–5 below).

## 4. Spring Boot code review (from #4 — pin `7a9cb99`)

Full line review: `research/analysis/SPRING_BOOT_CODE_REVIEW.md` §§3–7. What #11 needs:

- **Standing**: C-pin is the correctness oracle (0 SPEC conflicts by line review; allowed-strict
  extras — 16 KiB→413, trailing-token 400, `crit`/`b64` rejection — suite-consistent; `exp == now`
  boundary untested). Rule 4 (100 % suite) unconfirmed by execution by design (no blind
  reproduction). [VERIFIED] #4 §§6–7.
- **6 confirmed problems** (shape VERIFIED, impact unmeasured): vthreads flag absent (re-verified
  §0.3); pool-4 vs ~200 Tomcat threads parking shape; 2-round-trip like (benign, no delete
  endpoint); per-request `Mac` init + per-call regex compile; no app-level prepared reuse;
  `exp == now` unspecified. #4 §4.
- **Comments**: 7 in-scope (2 true observations — flag absent ×2; 1 accepted author statement —
  unoptimized baseline; 3 unproven performance claims — vthreads/WebFlux/NIO; 2 non-claims) and
  0 reviews / 0 inline at freeze (13/0/0 live census at #4 writing time, PARTIALLY VERIFIED).
  First quantitative claims (comment 11 `~20%`, merge message 38k-vs-31.75k) are [UNSUPPORTED]
  (no method/raw data/review) — quarantined as OPT-42, never quoted. #4 §§2/9.
- **Live-head drift** (recorded, not reviewed): 9 commits `7a9cb99 → 845accd` (direct serving +
  prepared reuse + TIME_WAIT fix + WebFlux-on-Netty variant + serialized writer + SQLite-built
  JSON + group-commit decline); pin-scoped statements ("Nginx mode / MVC / pool-4 / no prepared
  reuse") are [OUTDATED] for live head, current for the pin. #4 §9; #6 A1–A3.
- **OPTIMIZATIONS.md**: measure-first policy + 13 candidates + rule 5/6 non-negotiables
  [VERIFIED] vs README rules. #4 §§3.9/7.

## 5. GitHub research (from #6 — Areas A–F)

Full evidence: `research/findings/GITHUB_RESEARCH.md` §§1–6. What #11 needs:

- **A1–A3 (PR33 live head)**: drift mechanisms PARTIALLY VERIFIED (messages + file lists, lines
  not reviewed); every magnitude UNSUPPORTED; group-decline rationale PARTIALLY VERIFIED
  (Rule-6-conservative). Re-run E-deltas against `845accd`, not the pin.
- **A4/B3 (PR58 `java-nio-stohirov` @ `3cc06f6`, strongest external idea source)**: zero-dependency
  Java 25, hand HTTP/1.1 on epoll-via-FFM, direct FFM to SQLite 3.54.0 amalgamation, one thread +
  one conn inline, per-row `json_object` joined in Java, group commit per wake-up batch, scheduled
  PASSIVE checkpoints, `locking_mode=EXCLUSIVE`, SerialGC 256 m. Design presence PARTIALLY
  VERIFIED (page + `Db.java` 220 lines + `build.sh` + README read); laptop figures (128k–416k
  users, µs/CPU deltas) UNSUPPORTED for the droplet (OPT-43); rule-compat PARTIALLY VERIFIED
  (edge paths unreviewed — open question §9). Atomic like
  (`INSERT…WHERE EXISTS…ON CONFLICT`, `changes()`→201/200/404) is the elegant single-statement
  shape for DX-6 sub-row.
- **B1/B2 (threading/stack)**: vthreads flag mechanism VERIFIED, every gain UNSUPPORTED (helps only
  if threads, not pooled conns/single writer, gate); Netty pattern PARTIALLY VERIFIED, magnitudes
  UNSUPPORTED (run only after a thread-blocking profile).
- **C1–C5 (SQLite)**: WAL+NORMAL durability boundary VERIFIED (app-crash durable, power-loss may
  roll back — the asterisk); queue-placement trade VERIFIED shape, sizing EXPERIMENTAL; prepared
  reuse + QPSG PARTIALLY VERIFIED; per-row-JSON mechanism PARTIALLY VERIFIED, gains EXPERIMENTAL;
  cache/mmap/journal/checkpoint/locking semantics VERIFIED, optima EXPERIMENTAL (measure one knob
  at a time; `cache_size` is session-only — set per pooled conn).
- **D1–D3 (JVM)**: SerialGC single-CPU mechanism PARTIALLY VERIFIED, optimum EXPERIMENTAL (sweep
  inside 2 GB with SQLite+Nginx resident); `Mac`/regex/mapper shapes + thread-safety constraints
  VERIFIED, cost EXPERIMENTAL (profile after SQL ruled out); JFR <1 % overhead PARTIALLY VERIFIED
  (vendor-stated — cap + rotate + one JFR-off falsifier per lane).
- **E1–E2/F1–F3 (HTTP/harness)**: keep-alive envelope VERIFIED (Nginx 64/≥65 s vs direct ~15k/
  `LimitNOFILE=65535`; winner EXPERIMENTAL); JSON/JWT constraints VERIFIED, cost ranking
  EXPERIMENTAL; `load.js` closed-model semantics + thresholds VERIFIED (same-harness comparisons
  only; coordinated-omission caveat); TIME_WAIT mechanism PARTIALLY VERIFIED, `~32k` threshold
  UNSUPPORTED (adopt drain discipline, measure on droplet); correctness-first + pragma-asserted +
  JFR pipeline VERIFIED existence.
- **Licenses** (API-verified, §11): host repo MIT; HikariCP / sqlite-jdbc / Netty / Jackson
  Apache-2.0; SQLite public domain; OpenJDK/Temurin GPLv2+CE (flag use clean); k6 AGPLv3
  harness-only; Nginx 2-clause BSD. No unsafe/obsolete/undocumented trick adopted anywhere.

## 6. Optimization catalog map (from #7 — splits + X1–X7 carried verbatim)

Full decision database: `research/findings/OPTIMIZATION_CATALOG.md` §2 (44 rows with ID | Area |
Technique | Source | Mechanism | Evidence | Trade-off | Rule compat | Priority | Validation | Status).
Validated count: 0. Map (Status Funding: `adopted` = gate/posture in force; `candidate` = needs its
experiment; `rejected` = prohibited or quarantined):

- **Safe-baseline OPT-01–OPT-11 (10 adopted + 1 safe-candidate)**: OPT-01 correctness-first gate;
  OPT-02 WAL+NORMAL floor + pragma assertions; OPT-03 per-request JWT re-verify, no result caches;
  OPT-04 auth-before-id; OPT-05 byte-exact responses; OPT-06 prepare-once-per-conn posture;
  OPT-07 pool-4 starting point (explicitly not optimum); OPT-08 Nginx-first; OPT-09 ramp/hold/JFR +
  drain discipline; OPT-10 long-running warm process; OPT-11 cheap safe shapes (mapper/Pattern/
  `ThreadLocal<Mac>`) — candidate, profile-gated.
- **Experimental OPT-12–OPT-28 (17 rows, one variable per run)**: OPT-12 SQLite-JSON per-row vs
  aggregate; OPT-13 prepared-reuse/QPSG/`EXPLAIN QUERY PLAN`; OPT-14 atomic like; OPT-15 writer
  placement 1W+3R/Java-queue; OPT-16 pool sweep + `busy_timeout`; OPT-17 vthreads toggle; OPT-18
  stack A/B (profile-gated); OPT-19 group commit with wait-guard; OPT-20 direct-vs-Nginx;
  OPT-21 profile-gated layer strip; OPT-22 GC×heap matrix; OPT-23 JSON/auth alloc profile (tail);
  OPT-24 FULL cost probe (single run); OPT-25 checkpoint schedule; OPT-26 cache/journal sizing;
  OPT-27 mmap on/off (last); OPT-28 feed-query alternatives preserving live `like_count` + order.
- **High-risk OPT-29–OPT-33 (5 rows, late-stage only, #9-cleared, each with falsifier)**: OPT-29
  single-thread-inline + EXCLUSIVE + THREADSAFE=0; OPT-30 epoll-via-FFM; OPT-31 native SQLite
  build variants + PGO; OPT-32 RST idle eviction; OPT-33 queue-full 503 shedding.
- **Prohibited OPT-34–OPT-39 + quarantined OPT-40–OPT-44**: see §10.

Highest-value experiments X1–X7 (ranked, with blocking edges — #7 §4 carried verbatim):

| Rank | Experiment | Rows | Why highest-value | Falsifier / stop rule |
|---|---|---|---|---|
| X1 | SQLite-JSON per-row vs aggregate | OPT-12 (+ OPT-13 plans) | Single-statement change; removes per-column JNI/JDBC + Java strings/escaping; cheap byte gate | No alloc/p99 delta → keep reference query |
| X2 | Pool + writer placement (4 vs 1W+3R vs Java queue) | OPT-15 + OPT-16 | Single-writer serialization is the KNOWN bottleneck shape; makes waits observable | Reader starvation or queue-p99 regression → keep pool-4 |
| X3 | Direct-vs-Nginx same-build A/B | OPT-20 | Envelope VERIFIED both modes; deploy shape for everything after | Descriptor/idle-memory blowup or cap hit → stay behind Nginx |
| X4 | Stack choice, profile-gated | OPT-18 (+ OPT-21 strip) | Biggest structural decision; must NOT be pre-decided | No thread-blocking in profile → MVC+JDBC stands |
| X5 | Virtual-threads toggle | OPT-17 | Cheapest toggle; single flag, clear counters | No threads/pool-wait/p99 delta → platform stands |
| X6 | Group commit with wait-guard | OPT-19 (+ OPT-14 atomic like) | Amortizes WAL appends; legal shape exists in-tree | No write-p99 win at realistic mix, or atomicity cost → decline (author default) |
| X7 | GC×heap matrix | OPT-22 (+ OPT-23 profile) | Collector threads compete for the only core; heap competes with page cache | No p99/GC-time delta → keep `-Xmx512m` default posture |

Execution order: X1 + X2 first (independent) → X3 + X5 (need SQL/pool settled) → X4 (needs
thread-blocking profile from X1–X3) → X6 (needs pool shape from X2) → X7 (needs alloc profile).
NOT scheduled: OPT-24–OPT-28 tails (profile indictment only), OPT-29–OPT-33 (headroom-gated),
OPT-34–OPT-44 (never).

## 7. Architecture decision (from #9 — recommendation + 44-cell condensed)

Full evaluation: `research/findings/JAVA_ARCHITECTURE_DECISION.md` §§2–3 (options defined per layer;
11 axes × A/B/C/D = 44 cells, each with verdict + rationale; §3.1 evidence paragraphs).

- **Option A (Plain Java)**: JDK `HttpServer` + direct JDBC (Hikari-4 start), blocking handlers on
  platform threads (vthreads via DX-4), reference SQL + WAL/NORMAL + prepared-reuse + pragma gates,
  singleton mapper, JFR/GC/pool counters, Nginx-first then direct. Standing: [EXPERIMENTAL
  HYPOTHESIS as the build baseline — recommended starting lane, every sizing claim gated on §8].
- **Option B (Netty)**: event loops own sockets only; blocking JDBC hops to a bounded executor
  (one thread per SQLite conn, sweep under DX-5). Standing: [EXPERIMENTAL HYPOTHESIS —
  profile-gated lane, NOT the starting lane].
- **Option C (Spring Boot lineage)**: C-pin `7a9cb99` = CORRECTNESS ORACLE (adopted reference —
  reuse shapes, never merge, MIT notice on verbatim copies); C-live `845accd` = comparison point
  for DX-5 re-runs (intent PARTIALLY VERIFIED, code uninspected, figures UNSUPPORTED). Building on
  C-pin is NOT recommended (known pool-parking + framework tax; author's own travel is away from
  MVC). [VERIFIED pin characterization; C-live deltas PARTIALLY VERIFIED at most]
- **Option D (PR58 minimal extreme)**: admitted on research evidence only (A4 + B3 + OPT-29..33).
  Standing: [EXPERIMENTAL HYPOTHESIS — high-risk late-stage probe DX-8 only, headroom-gated].

Condensed 44-cell (per-axis standing; full cells + evidence in #9 §§3–3.1):

| Axis | A Plain Java | B Netty | C Spring Boot | D Minimal extreme |
|---|---|---|---|---|
| 1 SPEC compat | VERIFIED posture (gates per variant) | PARTIALLY VERIFIED (reactive edge surface unreviewed) | VERIFIED at pin; C-live PARTIALLY VERIFIED carry | PARTIALLY VERIFIED shape, edges uncertain |
| 2 1-vCPU fit | EXPERIMENTAL (favored — no framework threads) | EXPERIMENTAL (wins only if threads gate) | PARTIALLY VERIFIED tax (parking shape + per-request work) | EXPERIMENTAL (falsifier: loop stall) |
| 3 Memory | EXPERIMENTAL (favored — smallest floor) | PARTIALLY VERIFIED cost (slab + stacks) | VERIFIED sizing note (heaviest floor) | PARTIALLY VERIFIED floor (laptop-local claim) |
| 4 HTTP concurrency | EXPERIMENTAL (JDK server unsized at 64k) | PARTIALLY VERIFIED pattern (idle-conn shape) | VERIFIED behind-Nginx; direct message-only | PARTIALLY VERIFIED design (RST churn unanalyzed) |
| 5 SQLite interaction | VERIFIED discipline (oracle gates) | Same as A + executor sweep | VERIFIED at pin; C-live intent PARTIALLY VERIFIED | PARTIALLY VERIFIED shapes (atomic like, per-row JSON, group commit) |
| 6 Tail latency | EXPERIMENTAL (pool + writer + GC decide) | EXPERIMENTAL (extra hop can add a queue) | PARTIALLY VERIFIED risk (checkout parking) | EXPERIMENTAL with falsifier |
| 7 Complexity | VERIFIED lowest-framework | PARTIALLY VERIFIED higher | VERIFIED highest-structural | VERIFIED highest-custom |
| 8 Observability | PARTIALLY VERIFIED (custom wiring) | PARTIALLY VERIFIED (rich conn signal, poor traces) | VERIFIED richest off-shelf | PARTIALLY VERIFIED thinnest |
| 9 Deploy | VERIFIED both-modes | Same modes, heavier image | VERIFIED most-trodden | PARTIALLY VERIFIED narrowest (direct-only, `-march=native`) |
| 10 Optimization potential | EXPERIMENTAL (widest cheap headroom) | Same rows, narrower | PARTIALLY VERIFIED bounded (pays framework tax first) | EXPERIMENTAL (highest ceiling, highest variance) |
| 11 Maintainability | VERIFIED simplest-to-own | PARTIALLY VERIFIED (version + reactive expertise) | VERIFIED most-maintainable-by-convention | INCORRECT as maintainable (expert-only) |

Decision (one line, #9 §4.1 carried verbatim): **Build the submission on Option A (Plain Java:
JDK HttpServer + direct JDBC, Hikari-4 start, Nginx-first), keep Option C-pin (`7a9cb99`) as the
correctness oracle, gate Option B (Netty) on a thread-blocking profile, and hold Option D (PR58
minimal extreme) as a headroom-gated late-stage probe.**

Why (principles, #9 §4.2 condensed): Java-first (fork intention, smallest surface); SPEC-exact (no
framework edges to fight, C-pin byte reference); durability preserved (NORMAL floor + wait-guarded
group commit only); no prohibited caching (OPT-34..39 gates per variant); minimal deps (heavier
lanes earn place by measurement); measurable (cheapest lane to profile; A can lose honestly to B /
C-live in DX-5). Baseline means: first correct build + first profiled numbers + lane for
DX-1..DX-4 — not vthreads-on, not direct-first, not group-commit-on, not GC-tuned (DX-3/4/6/7
decide). Not-B/C/D-now are gated, not rejected (#9 §4.3).

## 8. Benchmark method + decision experiments (from #8 — ONE method, zero delta)

Runnable method: `research/findings/BENCHMARK_STRATEGY.md` §§1–7. Delta vs #9 §0.6: none — #10
inherits ONE method. Condensed:

- **Gate (§1)**: `test/test.sh` 100 % on a FRESH DB immediately before every run
  (`passed=42 failed=0` at `test/test.sh:125-126`); second run on the same DB fails by design.
  [VERIFIED] Copy-paste gate in #8 §1.2.
- **Harness (§2)**: k6 `ramping-vus` closed model pinned (60 s ramp → 5 min hold → 30 s down,
  `gracefulRampDown 10s`); thresholds p95<500 / p99<1000 / err<1 % (all three green for the full
  hold); VU mix + think/idle + 15 %/2 % probabilities pinned; connection envelope sized from the
  start (Nginx 64/≥65 s vs direct ~15k/`LimitNOFILE=65535`); k6 on a separate machine; same-harness
  comparisons only (coordinated-omission caveat). [VERIFIED] `bench/load.js:11-43`.
- **Limit-search S0–S4 (§3)**: S0 warm-up 1000/2 min (unscored, setup-bug detector); S1 probe 2500
  doubling to fail; S2 narrow ±250; S3 confirm with ONE full 5-min hold (run TWICE at the score
  candidate, both green); S4 step-down 250 on fail. Stop rules: green-N + red-N+250 → score N;
  generator/conntrack-tainted reds are INVALID (fix harness, re-run, never score); three clean reds
  250 apart → highest green below. No VU number here is a Java expectation. [VERIFIED]
  `README.md:100-105` + `bench/load.js:26-43` + transcript §[02:02].
- **Warmup/monitoring/profiling (§4)**: ramp + hold IS the JIT warmup (+ S0); per-hold J11 signals
  (k6 stats, server CPU, server RSS + box free, accept/`EMFILE`/eviction, generator CPU/RAM,
  clock alignment); connection-harness logging per run; JFR per run (cap + rotate + one JFR-off
  falsifier); targeted counters per DX row (pool-wait/`busy_timeout`, queue time, alloc flame,
  GC + NMT, `EXPLAIN QUERY PLAN` fresh + grown); pragma assertions + idempotency re-gates ride
  along. [VERIFIED existence; JFR bound PARTIALLY VERIFIED vendor-stated]
- **Stats (§5)**: `avg/med/p95/p99/max` + error rate together, never a single mean; S3 doubled for
  variance (unseeded think times + shared-CPU noise — report spread, not the better half); J11
  per-level log table (pass AND fail+1 rows required).
- **Bottleneck-ID (§6)**: generator-vs-server falsifier first (saturation → INVALID; port-drain →
  INVALID; idle-server ceiling → harness artifact); conntrack-vs-app above ~60k (full table/drops
  → platform ceiling, stop scoring upward; self-imposed limits fail via error budget — check
  error-reconnect correlation); app-internal profile order SQL → pool/queue → HTTP/conn →
  stack/threading (E-stack gate) → JSON/auth alloc last. Video bottleneck catalog as pattern
  library, never as Java predictions.
- **EX-template (§7.1, copy-paste per run)**: variable (ONE) / fixed (SHA + SQL + pragmas + pool +
  stack + JVM + mode + VUS plan) / gates (suite line + byte-compare + rule 5/6 code-read + pragma
  assertions) / harness (S0–S4 + k6 command + generator count + drain-wait + JFR on/off) / report
  (per-level table + profile artifact) / falsifier / maps-to / verdict. Missing gate line, k6
  command, or fail+1 row = INCOMPLETE, not a result. Multi-variable optimization prohibited.
- **Durability declaration (§8, per-run checklist)**: Rule 5 (per-request re-query/re-verify; allowed
  plan/state reuse vs prohibited result caching); Rule 6 (WAL + NORMAL-or-stronger per pooled conn,
  session-only knobs re-asserted; wait-guarded group commit only); NORMAL asterisk documented every
  run; FULL probe (OPT-24) one run then back; schema-fixed diff gate; box rules (build on box,
  pinned, ≤60 s start; no kernel/pinning changes).

DX-1..DX-8 (single-variable, #9 §5 carried verbatim — variables, fixed sets, gates, falsifiers
survive under the §8 harness):

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

Order with blocking edges (inherited X-order): DX-1 + DX-2 first → DX-3 + DX-4 → DX-5 →
DX-6 → DX-7 → DX-8. NOT scheduled without profile indictment: OPT-24..OPT-28 tails; never:
OPT-34..OPT-44.

## 9. Open questions (explicit — nothing silently resolved)

Carried from #9 §5.9 + #8 open items. Each names its owner and its falsifier/gate. #11 must
schedule them; #12 must confirm none was silently closed.

1. **C-live line review.** PR33 live head `845accd` (WebFlux-on-Netty + direct-60k + prepared
   reuse + serialized writer + SQLite-built JSON) is message-verified intent only — no line review
   exists. Owner: first DX-5 prerequisite (before any C-live-shape run). Gate: auth-before-id,
   error table, key order, `created_at` passthrough, commit-before-respond re-proved on the WebFlux
   tree. Until then every C-live figure stays UNSUPPORTED (OPT-42).
2. **D `Server`/`Jwt`/`Json` edge audit.** PR58 `Server.java`/`Jwt.java`/`Json.java` semantics were
   never line-audited; hand parser must re-prove EVERY SPEC edge (auth-before-id, id matrix,
   malformed JSON, 404 shapes, key order, trim/500-code-point rules). Owner: DX-8 prerequisite
   (#9-cleared). Falsifier: any SPEC-edge miss closes D permanently. Until then OPT-29/30/32 stay
   `uncertain`.
3. **JDK-HttpServer vs Tomcat 64k-idle sizing.** No evidence sizes the JDK `HttpServer` at ~15k–64k
   idle keep-alives; Tomcat defaults (`maxConnections`, `maxKeepAliveRequests`) are documented but
   droplet-unmeasured for direct mode. Owner: DX-3 (E-edge) with accept-queue/`EMFILE`/eviction/
   idle-memory counters. Falsifier: descriptor/idle-memory blowup keeps the build behind Nginx.
4. **FULL-vs-NORMAL single cost probe.** WAL + `synchronous=NORMAL` is the floor (VERIFIED
   boundary with the power-loss asterisk documented); the FULL durability margin was never measured
   on this workload. Owner: OPT-24 single probe under §8, then back to NORMAL. Gate: owner decision
   required for any direction change — never silent.
5. **Generator-vs-server falsifier on every ceiling.** Any FAIL or suspicious PASS-with-idle-server
   must run the §8 §6.2 sequence (generator saturation → INVALID; port-drain → INVALID;
   idle-server ceiling → harness artifact). Owner: every EX row (§7.1 template enforces it).
   Until passed, no ceiling is a server result.
6. **Conntrack falsifier above ~60k.** Full table / kernel drops / forced-reconnect counts decide
   platform-vs-app ceiling (video precedent VERIFIED; kernel tuning forbidden). Owner: §8 §6.3
   checks on any ceiling above ~60k. Until passed, the ceiling is a platform observation, not an
   app score.
7. **C++-style cache-shrink legality transfer.** The video ruling (OS page cache + `mmap` are the
   database) is SPEC-anchored (S5), but the C++ memory-accounting reasoning does not transfer
   automatically to JVM RSS accounting inside 2 GB. Owner: E-sql/E-jvm tail (OPT-26/27) with
   native-mem tracking. Until measured, `mmap_size` direction stays EXPERIMENTAL HYPOTHESIS.
8. **Caption-slip magnitudes.** Transcript fail-points "3,300" (§[09:05]) and "3,900" (§[10:07])
   are coherent only as 33,000/39,000 (PARTIALLY VERIFIED — audio authoritative). Owner: anyone
   quoting C#/Fortran ceilings re-checks audio first; this doc never quotes 3,300/3,900 as results.
9. **Harness-form reconciliation.** #8 owns the runnable form; if any later session changes it, it
   must state the delta against §8 (same table shape as #8 §0.5) — no silent split. Owner: #11
   (adopts §8 verbatim or records delta); #12 verifies.

## 10. Prohibited + quarantined inheritance (verbatim from #7 §2.4/§5, via #9 §6)

Gates enforced by code read per variant — no experiment ever (all [VERIFIED prohibitions]):

- OPT-34 response/query caches, table copies, remembered JWT verifications (rule 5).
- OPT-35 `synchronous=OFF` / `journal_mode=DELETE` / MEMORY / in-memory DB (rule 6 floor).
- OPT-36 unguarded batching — respond-before-commit (OPT-19/DX-6 is the only legal shape).
- OPT-37 schema changes (tables/columns/indexes/triggers).
- OPT-38 kernel tuning / CPU pinning / box changes (in-process settings stay fair game).
- OPT-39 JWT downgrade (skip verify, non-HS256, ignore `exp`/`sub`/`username`).

Quarantined figures — never quoted as targets (mentioned only to quarantine):

| ID | Claim | Verdict | Resolution |
|---|---|---|---|
| OPT-40 | Prior-video table (Spring 5.1k/10.25k, Go 11.75k, Rust 14.05k) as Java targets | OUTDATED | Re-date to `sQXFhh_PiG4` or drop |
| OPT-41 | `~0.77 ms` CPU/request box budget | UNSUPPORTED | Drop or re-derive from Java profiles |
| OPT-42 | PR33 `~20%` / 38k-vs-31.75k / interim "not that big" as results | UNSUPPORTED | Never quote; DX-5 re-measures under §8 |
| OPT-43 | PR58 laptop table (128k–416k users, µs/CPU deltas, ~40k req/s ceiling) as droplet figures | UNSUPPORTED (as droplet figures) | Shapes only; numbers never transfer |
| OPT-44 | `~32k users` ephemeral-port threshold as universal constant | PARTIALLY VERIFIED mechanism / UNSUPPORTED threshold | Adopt drain discipline (OPT-09); measure on droplet |

Minor correction carried everywhere: the suite is `test/test.sh` (V21 INCORRECT-minor fixed).

## 11. License posture (inherited #6 §7 — API-verified, no new burden here)

Repo `arjaythedev/twelve-dollar-server-challenge` MIT: study + re-implement ideas freely; verbatim
copies keep the notice (C-pin shapes reused as reference, never merged). Per-lane dependencies:
A — xerial `sqlite-jdbc` Apache-2.0 (+ HikariCP / Jackson Apache-2.0 if kept); B — + Netty/Reactor
Netty Apache-2.0 (manifest-noted); C — same Apache-2.0 set, heaviest manifest; D — SQLite
amalgamation public domain + Temurin JDK pin (GPLv2+CE — flag/build use clean, no JDK
redistribution planned). Harness-only: k6 AGPLv3 (binary use, not linked), Nginx 2-clause BSD
(config reference), JFR/OpenJDK vendor notes. This synthesis doc introduces no dependency — nothing
to clear beyond inheritance. Unsafe/obsolete/undocumented tricks adopted here: none (high-risk
rows carry falsifiers + edge-audit gates).

## 12. Verification appendix (commands run this session)

- `git status`: on `research/java-analysis` tracking `origin/research/java-analysis`, clean except
  pre-existing untracked `.idea/` (left alone, never added — verified via `git status --short`).
- `git rev-parse`: `main` = `research/java-analysis` = `f4e25f8`; `backup/main-baseline-2026-10-13`
  = `026bdb6` intact; `reference/spring-boot-pr33` = `7a9cb99` untouched. `git log --graph` linear.
  `git remote -v`: origin = fork, upstream = original; no upstream push.
- Precondition: `gh issue list --state all` shows #10 OPEN, #2–#9 CLOSED, #1/#11/#12 OPEN
  untouched. If #10 had not been OPEN the session would have stopped (it was OPEN).
- Claimed #10 via `gh issue comment 10` before any work (parallel-session guard).
- Base refs read in full (§0.2 list); pins re-verified fresh (§0.3), not trusted from parents.
- Self-containment check: §§1–2 (ground truth + V1–V24) + §6 (catalog map + X1–X7) + §7 (arch +
  condensed 44-cell + DX-1..DX-8) + §8 (ONE method + EX-template) + §9 (9 open questions) + §10
  (prohibited + quarantines) — a #11 agent executes without reopening parents (pointers included
  for depth, never required for execution).
- No-new-claims check: every row traces to #2–#9 sections cited inline; new-claim count 0;
  classification changes vs #5: 0.
- No-invented-targets check: no suite/load executed; every numeric figure attributed to
  SPEC/threshold/transcript/author/laptop source with UNSUPPORTED/OUTDATED quarantine where due
  (§10); validated-Java count stays 0.
- Classification check: every significant claim carries one of the six verdicts (§§1–11 tables +
  inline tags).
- License check: §11 inherits the API-verified table; no new dependency introduced.
- Forbidden-word grep over draft + final file before commit — empty both times.
- `git add` ONLY `research/findings/FINAL_RESEARCH_REPORT.md`; commit `docs(research):
  final_research_report closes #10`; push `origin research/java-analysis`; ff-merge to `main`;
  push `main` (or STOP on non-ff).

*No implementation started. Next: #11 IMPLEMENTATION_ROADMAP consumes this report + #8 method
without re-researching; #12 verifies and stops. #11 now UNBLOCKED (#10 closed).*
