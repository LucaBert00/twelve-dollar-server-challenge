# FACT_CHECK — condensed verdict table (Wayfinder-2/11, closes #5)

> Output of `[Wayfinder-2/11] FACT_CHECK` (gh #5). Blocked by #2 (CLOSED).
> ONE file for this ticket. No app code. `research/inputs/*` untouched. Reference branch never merged.
> Derived ONLY from `research/analysis/REPORT_COMPARISON.md` §5–§6 (parent, closed #2) with
> `SPEC.md` + `test/test.sh` as resolution authority. No new claims beyond the parent matrix
> without fresh primary evidence.

## 0. Method, truth hierarchy, skills

### 0.1 Parent + authority

- Parent: `research/analysis/REPORT_COMPARISON.md` @ `b8b8762` (Wayfinder-1/11, closes #2).
  This file condenses its §5 verification matrix (V1–V24) into a reusable table for
  downstream architecture (#9) and catalog (#7) work. It does not re-derive verdicts from scratch.
- Resolution authority: `SPEC.md` (84 lines) + `test/test.sh` (126 lines, `passed/failed` at
  `test/test.sh:125-126`) override the parent where they speak directly.
- Truth hierarchy observed: `SPEC.md` + `test/test.sh` > `REPORT_COMPARISON.md` §5 >
  transcript > AI reports > PR33 discussion > external.
- Classification labels preserved verbatim from parent:
  `VERIFIED` / `PARTIALLY VERIFIED` / `INCORRECT` / `UNSUPPORTED` / `OUTDATED` /
  `EXPERIMENTAL HYPOTHESIS`. Zero classification changes without fresh primary evidence
  (none introduced this session — count of changes: 0).

### 0.2 Skills used / skipped (justification)

Invoked via skill tool this session (ticket mandate, in order):
`research` (primary-source derivation from parent + SPEC/test),
`documentation-and-adrs` (condensed reference-table shape, decision-preserving),
`verification-before-completion` (gate before every claim below and before commit/push/close).

Map Notes standing skills — skipped with reason (ticket permits skip with justification):
`grilling`, `domain-modeling` — skipped: rubric and domain terms fixed in #2 §0.2; this ticket
condenses, it does not re-score or re-model. `to-spec`, `to-tickets` — skipped: output is an
analysis doc, not a new spec or ticket set. `java-springboot` — skipped: no Spring code written
or judged; reference-branch facts reused verbatim from parent §0.4 re-verification.
`documentation-writer` — skipped: `documentation-and-adrs` already covers the reference-table
shape; second docs skill adds no structure. `github`, `git-workflow` — skill invocation skipped:
only standard linear ops used (claim comment, single-file commit, ff-only merge, close); each
verified with fresh `gh`/`git` commands per §4.

### 0.3 Inputs verified

- `research/analysis/REPORT_COMPARISON.md` (246 lines) — read in full; §5 (V1–V24) + §6
  handoff notes are the sole claim source.
- `SPEC.md` (84 lines), `test/test.sh` (126 lines) — read in full fresh this session for
  resolution authority (key-order, auth-before-id, trim/code-point, like/health/feed rules,
  thresholds cited below).
- `docs/WAYFINDER_MAP_PROMPT.md` §4 (global hard rules) — read in full.
- Issue #5 body (scope source of truth), Epic #1 body (order + Decisions), #2 CLOSED state —
  verified via fresh `gh issue view` / `gh issue list --milestone Task-2-Research`.

### 0.4 Forbidden-word filter

Case-insensitive grep for the forbidden term (§4.4) run over the draft and again over the
final file before commit — empty both times (see §4). Where that concept is needed, this
doc writes `minimal-diff senior-dev mode`.

## 1. Condensed verdict table (Claim | Verdict | Evidence | Resolution)

Every row of parent §5 is accounted for (24/24). Verdicts copied verbatim; Evidence trimmed
to the primary citation; Resolution is the parent's disposition.

| # | Claim (source) | Verdict | Evidence | Resolution |
|---|---|---|---|---|
| V1 | Box 1 vCPU / 2 GB / no swap / Ubuntu 24.04 (all three) | VERIFIED | `docs/PROJECT_CONTEXT.md:5-6`; `SPEC.md:8-14`; transcript §[01:01] | Accept; use as sizing gate |
| V2 | 5 endpoints, compact JSON, exact key order, live `like_count`, text `created_at` passthrough (all three) | VERIFIED | `SPEC.md:23-43`; `test/test.sh:52-57,95-123` golden + read-back checks | Accept; byte-compare in CI |
| V3 | Auth-before-id; HS256-only; exp-then-payload errors (all three) | VERIFIED | `SPEC.md:72-79`; `test/test.sh:68-83,113`; `Authentication.java` (Bearer/HS256/constant-time/exp/payload) | Accept; router order is a correctness gate |
| V4 | Trim-then-validate; 500 code points; malformed-JSON 400 (all three) | VERIFIED | `SPEC.md:67-69`; `schema.sql:13`; `test/test.sh:85-93,96-103` | Accept; count code points, not UTF-16 |
| V5 | Like 201/200 + existence check; health `SELECT 1`; feed 20 newest `created_at,id DESC` (all three) | VERIFIED | `SPEC.md:38-42`; `test/test.sh:60-61,110-123`; `PostRepository.java` projection + ordering + `RETURNING` | Accept |
| V6 | Schema fixed; 3 indexes; no-cache vs permitted page-cache/mmap/prepared (all three) | VERIFIED | `schema.sql:1-2,24-26`; SPEC rules; PR33 `OPTIMIZATIONS.md` non-negotiables | Accept; no result/token caches |
| V7 | Bench thresholds + VU-loop + separate generator (chatWork §7; chatNormal §8; claude §7-partial) | VERIFIED (chatWork/chatNormal); PARTIALLY VERIFIED (claude — mix admitted unread) | `bench/load.js:11-43`; transcript §[02:02] | Accept chatWork §7 as method reference; claude must read `load.js` before downstream use |
| V8 | Warm-up 1000/2 min → 2500 doubling → ±250 → 5-min hold → −250 retry (chatWork §7; chatNormal §8) | VERIFIED | Upstream README method + `bench/load.js` stages/thresholds; transcript §[02:02] criteria | Accept |
| V9 | PR33 head `7a9cb99`, 4 commits, 35 files, OPEN/unmerged/0 reviews/7 comments/no numbers (chatWork §4; chatNormal §3) | VERIFIED | `SOURCE_MANIFEST.md:33-41`; `SPRING_BOOT_PR33.md:§2-6,§8`; fresh `git show` in #2 session | Accept; cite SHA on reuse |
| V10 | No virtual-threads flag; WebFlux absent; Hikari 4/1; keep-alive 70 s; WAL/NORMAL/FK-on/busy-5 s (chatWork §4) | VERIFIED | `application.properties` (6 lines); `Application.java` pragmas/pool; `pom.xml` webmvc/jdbc/sqlite-jdbc | Accept; flag-absent claims in comments 6.1/6.7 true, effect unproven |
| V11 | "Virtual threads will surely help" / "WebFlux next" (PR comments, echoed loosely) | UNSUPPORTED | `SPRING_BOOT_PR33.md:§8` (no numbers); author defers (6.3/6.6) | needs-experiment (§2, method E1–E2) |
| V12 | "NIO/raw-Netty better here" (comment 6.7) | UNSUPPORTED | Same §8 (testing-pending, no profile/diff) | needs-experiment; directionally plausible for conn-count only (§2, method E3) |
| V13 | Nginx 14–20 % CPU (chatNormal §482; chatWork §1) | PARTIALLY VERIFIED | Repo-reported original-video observation, not Java measurement (both reports hedge correctly) | needs-experiment behind-vs-direct (§2, method E4) |
| V14 | Prior-video table 5.1k/10.25k/11.75k/14.05k as current guidance (claude §9/§59-73) | OUTDATED | Third-party summary of `sQXFhh_PiG4`; current transcript §[03:02]–[19:14] peaks at 64k | Re-date to prior video; do not use for Java targets |
| V15 | DB-post table (Go 13.4k SQLite etc.) as current guidance (claude §74-90) | PARTIALLY VERIFIED (source real, context adjacent) | Oct-13 DB post, Go app, same box class — not the QGWKCaglSBI round | Keep as SQLite-NORMAL-vs-FULL evidence; not a Java target |
| V16 | `~0.77 ms CPU/request` box budget (claude §9) | UNSUPPORTED | Arithmetic on summarized numbers, not measured on Java path | Drop or re-derive from Java profiles |
| V17 | JDK+vthreads vs Netty vs Boot non-conclusion (chatNormal §285) | VERIFIED stance | PR33 baseline admission + measure-first rule | Accept as decision policy |
| V18 | MVC+JDBC baseline; WebFlux only after thread-block evidence (chatWork §5) | VERIFIED stance | Blocking Xerial driver + single-writer reality; no counter-evidence in set | Accept baseline order |
| V19 | Pool/reader-writer/vthread/JVM/pragma sweeps as isolated candidates (all three §5/§10/§6-7) | EXPERIMENTAL HYPOTHESIS (honest) | No Java bench in evidence; PR33 lists same as unmeasured | Accept as experiment backlog, not results (§2, methods E1/E5/E6) |
| V20 | FFM binding, PGO SQLite, cache-shrink, checkpoint deferral, hand-written JSON (claude §6-7) | EXPERIMENTAL HYPOTHESIS | Transcript C/C++/Bun shapes exist; Java transfer unmeasured | Late-stage only, profile-gated (§2, method E7) |
| V21 | Test filename `test/run.sh` (chatNormal §564; claude M1) | INCORRECT (minor) | Repo root: `test/test.sh` (114 lines); `test/run.sh` is the PR-docs runner path | Correct to `test/test.sh` for the suite |
| V22 | Seed 50k/500k/2M + tokens (chatNormal §155-177; chatWork §1) | VERIFIED | `SOURCE_MANIFEST.md:49`; `bench/load.js:24` tokens; schema tables | Accept |
| V23 | "Zero Java submissions" (transcript) vs PR33 existence | VERIFIED both (time-bounded) | Transcript §[03:02] (benchmarked set) vs PR33 Oct 10–13 open/unbenchmarked | No contradiction; Java track = baseline + greenfield |
| V24 | Conntrack 65k ceiling; Rust/C++ 64k tie; C++ higher p99/CPU (transcript) | VERIFIED | Transcript §[15:11]–[19:14] | Accept; headroom is the tiebreaker, not the tie |

## 2. Needs-experiment backlog (carried forward with method, never silently resolved)

From parent §4 + §6. Downstream #7 must schedule these; #8 must make them runnable single-variable.

- E1 (V11/V19): Virtual-threads effect on 1 vCPU + single SQLite writer. Method: A/B on target
  box — platform threads vs `spring.threads.virtual.enabled=true` vs NIO tuning; same
  commit/pragmas/pool; 5-min holds; report p95/p99/err/CPU/RSS spread.
- E2 (V11/V18): WebFlux adoption. Method: adopt only if a profile shows Tomcat-thread blocking
  as the bottleneck; default MVC+JDBC baseline stands.
- E3 (V12): NIO/raw-Netty direction. Method: profile-gated; plausible for conn-count only,
  not write concurrency; compare same-SQL baseline behind identical load.
- E4 (V13): Nginx-vs-direct. Method: same build behind Nginx vs direct on the real box (direct
  must hold ~15k keep-alives, idle ≫ 65 s per `SPEC.md:18-21`, port 80 non-root); sequence
  Nginx-first for comparability, then direct.
- E5 (V19): Pool size/split + busy-timeout. Method: pool sweep + `busy_timeout` + p99 under write
  load; pool-4 is a starting point explicitly not optimum; keep write sections short, judge on
  tail latency not averages.
- E6 (V19): JVM flags (G1/768 m vs Serial/640 m vs default). Method: pin JDK 25, sweep heap × GC
  on target box with RSS + GC-log + p99 gates; stay well under 2 GB incl.
  native/SQLite/Nginx/OS (no swap).
- E7 (V20): FFM libsqlite3 / PGO SQLite / `mmap` / page-cache shrink / checkpoint deferral /
  hand-written JSON. Method: late-stage only after a correct baseline + profile; must preserve
  exact key order, escaping, UTF-8, and per-request HMAC semantics; measure after SQL baseline.
- E8 (V4-derived group commit, parent §4.4): single-writer queue + drain +
  `BEGIN IMMEDIATE` … `COMMIT` with "respond only after the shared commit" guard; second-order
  at ~100 writes/s under the realistic mix — measure, do not assume.

## 3. Parent-fidelity + downstream notes

- Parent-fidelity check: parent §5 holds V1–V24 (24 rows). This table holds V1–V24 (24 rows).
  Row count: 24. Classification changes vs parent: 0. New claims beyond parent: 0.
- Handoff per parent §6: keep V1–V10 as the VERIFIED core; V11–V13/V19–V20 as the
  needs-experiment backlog with the methods in §2 above; V14 as OUTDATED (re-date or drop);
  V16 as UNSUPPORTED (drop); V21 as INCORRECT-minor (fix to `test/test.sh`).
- Downstream: #7 OPTIMIZATION_CATALOG consumes V19–V20 + §2 methods as its candidate backlog;
  #9 JAVA_ARCHITECTURE_DECISION consumes V17–V18 as its baseline-order policy and V11–V13 as
  its comparative-experiment list. Re-check PR33 comments/reviews before benchmarking
  (`SPRING_BOOT_PR33.md:§11` re-check recipe, per parent §6).
- No benchmarks invented this session. No implementation started.

## 4. Verification steps run (this session)

- `git status` clean on `research/java-analysis`; `HEAD = main = origin/main =
  origin/research/java-analysis = b8b8762`; `backup/main-baseline-2026-10-13 = 026bdb6`
  intact; `reference/spring-boot-pr33 = 7a9cb99` untouched (fresh `git status` + `git branch -vv`
  + `git log --graph` before work).
- Precondition: `gh issue view 2` shows CLOSED; `gh issue list --milestone Task-2-Research`
  shows #5 OPEN, #2 CLOSED, others untouched.
- Claimed #5 via `gh issue comment 5` before work (parallel-session guard).
- Inputs in §0.3 read in full; verdicts derived from parent §5 only, resolutions checked against
  `SPEC.md` + `test/test.sh`.
- Forbidden-word grep over the draft and over the final file before commit — empty both times.
- Skills: see §0.2 for invoked vs skipped-with-reason.

*Next: #7 OPTIMIZATION_CATALOG chain downstream. Frontier #3/#4/#8 remain for parallel sessions.*
