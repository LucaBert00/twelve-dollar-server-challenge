# VIDEO_ANALYSIS — timestamped reconstruction of the original $12-server video experiment (Wayfinder-3/11, closes #3)

> Output of `[Wayfinder-3/11] VIDEO_ANALYSIS` (gh #3). Frontier ticket, blocked by none.
> ONE file for this ticket. No app code. `research/inputs/*` untouched. Reference branch never merged.
> Primary observation source BELOW `SPEC.md` + `test/test.sh` in the truth hierarchy.

## 0. Method, truth hierarchy, skills, inputs

### 0.1 Truth hierarchy (observed throughout)

`SPEC.md` + `test/test.sh` (126 lines, `passed/failed` at `test/test.sh:125-126`) >
transcript (`research/inputs/I-Asked-Expert-Programmers-to-Max-Out-a-12-Dollar-Server_QGWKCaglSBI_transcript.md`, auto-captions, audio authoritative) >
`REPORT_COMPARISON.md` §1/§4.10 + `FACT_CHECK.md` (video-ID fork + 64k ground truth — reused, not re-derived) >
AI reports (fallible; agreement proves nothing) >
PR33 discussion (`research/sources/SPRING_BOOT_PR33.md`, 7 conversation comments, 0 reviews, 0 inline, no maintainer comments, no benchmark numbers) >
external blogs/summaries.

Transcript-vs-SPEC conflicts are resolved in favor of SPEC/test (§7). Every significant claim below
carries one of `VERIFIED` / `PARTIALLY VERIFIED` / `INCORRECT` / `UNSUPPORTED` / `OUTDATED` /
`EXPERIMENTAL HYPOTHESIS` with primary evidence (transcript timestamp, file+line/commit, doc URL,
or measured output). Needs-experiment items carry a method, never a silent resolution.

### 0.2 Video-ID separation (reuse, do not re-derive)

Per `REPORT_COMPARISON.md` §1 + §4.10 (V14/V23–V24) and `FACT_CHECK.md` V14–V15/V23–V24, reused verbatim:

- **Current round (this ticket):** `QGWKCaglSBI` — "I Asked Expert Programmers to Max Out a $12 Server",
  published 2026-10-13, ~22:55 (1376 s), 13 languages, 26/28 benchmarked, best 64,000
  (`SOURCE_MANIFEST.md:43-50`; transcript Ch. 0:00–22:17).
- **Prior video (NOT current, do not re-attribute):** `sQXFhh_PiG4` — 8-language defaults round linked from
  upstream README (`README.md:5`). Its numbers (e.g. Rust + SQLite ~14,000, Spring ~5.1k/10.25k,
  Go ~11.75k, Rust ~14.05k in third-party summaries) are `OUTDATED` as current guidance per V14.
  Any number dated to that round stays labeled prior-video.
- **Oct-13 DB post (adjacent, NOT the video round):** Go + SQLite comparison (~13.4k class) cited in
  `REPORT_COMPARISON.md` V15 / `FACT_CHECK.md` V15. Real source, adjacent context (same box class,
  Go app) — kept only as SQLite `NORMAL`-vs-`FULL` durability evidence, never as a Java target.
- **Later repo/community changes (after the video):** PR #33 (`feat: add Java 25 Spring Boot 4 submission`,
  head `7a9cb997`, opened Oct 10 / discussed Oct 13, OPEN unmerged unbenchmarked per
  `SOURCE_MANIFEST.md:33-41`) and any post-video merged-submission batches. Separated in §8.

### 0.3 Skills used / skipped (justification)

Invoked via skill tool this session (ticket mandate, in order): `using-superpowers` (routing),
`grilling` (scope tree: fixed scope — one file, no app code, no re-derivation; frontier empty after
preconditions, proceed), `research` (primary-source derivation: transcript + SPEC/test + manifest),
`deep-research` (wide-mode evidence pass: transcript full read + SPEC/test/load.js cross-check +
REPORT_COMPARISON/FACT_CHECK reuse), `documentation-writer` (Diátaxis Explanation + Reference mixed:
§1–§6 explanation, §7–§9 reference), `verification-before-completion` (gate before every claim below
and before commit/push/close).

Map Notes standing skills — `domain-modeling`, `to-spec`, `to-tickets`, `java-springboot`, `github`,
`git-workflow`: `github` + `git-workflow` used for claim comment, single-file commit, ff-only merge,
close (see closing comment). `domain-modeling` skipped: no new domain terms introduced (all terms
— group commit, WAL, page cache, conntrack, VU — reused from parent reports with identical meaning).
`to-spec` / `to-tickets` skipped: output is an analysis doc, not a new spec or ticket set.
`java-springboot` skipped as a skill invocation: this ticket reconstructs the video, it does not judge
Spring code; PR33 facts reused verbatim from `REPORT_COMPARISON.md` §1 + `FACT_CHECK.md` V9–V10
(re-verified SHAs in §10) rather than re-inspecting Java files (owned by #4).

### 0.4 Inputs verified (fresh this session)

- Transcript (165 lines incl. grouped cues + raw-cue provenance): Ch. 0:00–22:17 — read in full.
- `SPEC.md` (84 lines), `test/test.sh` (126 lines), `bench/load.js` (70 lines), `schema.sql` (26 lines),
  `README.md` (rules 1–11 + scoring) — read in full.
- `research/sources/SOURCE_MANIFEST.md` (baseline `026bdb6`, PR head `7a9cb997`, input hashes) — read in full.
- `research/analysis/REPORT_COMPARISON.md` (246 lines) §1 + §4.10 + §5 (V1–V24) and
  `research/analysis/FACT_CHECK.md` (145 lines) V1–V24 + §2 (E1–E8) — read in full; ground truth reused.
- `docs/WAYFINDER_MAP_PROMPT.md` §4 (global hard rules) — read in full.
- Issue #3 body (scope source of truth), Epic #1 body (order + Decisions), #3 OPEN / #2 CLOSED / #5 CLOSED —
  verified via fresh `gh issue view` before work (see §10).

### 0.5 Forbidden-word filter

Case-insensitive grep for the forbidden term (§4.4) run over the draft and again over the final file
before commit — empty both times (see §10). Where that concept is needed, this doc writes
`minimal-diff senior-dev mode`.

---

## 1. Objectives, hardware, rules (SPEC-grounded)

### 1.1 Video objective

- Challenge: implement one small feed API on SQLite in any language; benchmark on the $12 server;
  fastest implementation wins on sustained concurrent users. [§00:00] — VERIFIED (transcript §[00:00]
  + `README.md:1-8` + `SOURCE_MANIFEST.md:5-16`).
- Follow-up motive: prior defaults round peaked at ~14,000 (Rust + SQLite); community claimed better;
  author opened the repo and asked for the fastest implementation per language. [§00:00] — VERIFIED
  as prior-video statement (transcript §[00:00]); the 14,000 figure is OUTDATED as a current target
  (see §8.1).
- Scope of this ticket: reconstruct the experiment as primary observation source, below SPEC/test.
  [§00:00–§01:01] — VERIFIED (issue #3 body + hierarchy §0.1).

### 1.2 Hardware (box)

| Fact | Evidence | Verdict |
|---|---|---|
| 1 vCPU (shared), 2 GB RAM | Transcript §[00:00] + §[01:01]; `README.md:3,52-54`; `SPEC.md:8-14` runtime table | VERIFIED |
| No swap | `README.md:52-54` + `REPORT_COMPARISON.md` V1 (PROJECT_CONTEXT); transcript does not state swap (absence is not contradiction) | VERIFIED (SPEC/repo authority) |
| Ubuntu 24.04 x86_64, DigitalOcean Basic droplet | `README.md:52-54` + `REPORT_COMPARISON.md` V1; transcript §[01:01] says "$12 server" without OS version (complementary, not conflicting) | VERIFIED |
| 50 GB storage | Transcript §[01:01] ("50 gigs of storage") | VERIFIED (transcript-only; no SPEC counterpart; accepted as box description) |
| Shared CPU + RAM with OS (and Nginx if used); OOM = loss | `README.md:52-54` | VERIFIED |
| No kernel tuning, no CPU pinning for you; in-process settings (GC, threads, allocator, rule-6 pragmas) fair game | `README.md:59-62` rules 10–11 | VERIFIED |

### 1.3 API + correctness rules (transcript stated, SPEC/test decide)

| Rule (transcript §[01:01]) | SPEC/test resolution | Verdict |
|---|---|---|
| Same social API: 5 endpoints incl. health check; load feed, open post, like, create | `SPEC.md:34-42` (GET /health, GET /feed, GET /posts/:id, POST /posts, POST /posts/:id/like) | VERIFIED |
| 42-check test suite, responses must match spec exactly | `README.md:17,41`; `test/test.sh:1-9` (fresh-DB golden bytes); suite prints `passed/failed` at `test/test.sh:125-126` | VERIFIED |
| SQLite in-process, same process as app; no separate DB/cache server (no Postgres, Redis, Memcached) | `README.md:33-38` rules 2–3 + `SPEC.md:8-14` (`SQLITE_PATH`, WAL, `schema.sql`) | VERIFIED |
| Every authenticated request verifies login-token signature | `SPEC.md:74-79` (HS256-only, verify signature + `exp` every request) + `test/test.sh:68-83` | VERIFIED (SPEC details win: HS256-only, exp-then-payload ordering) |
| Writes commit before respond; batching multiple writes into one transaction allowed ONLY if each response waits for its commit | `README.md:46-49` rule 6 (WAL `synchronous=NORMAL` or stronger; no OFF/OFF/MEMORY/in-memory; group commit allowed with wait guard) | VERIFIED (no conflict; README wording is the normative form) |
| "Free to optimize otherwise": framework, own HTTP server, drop Nginx, whatever | `README.md:55-58` rule 9 (Nginx optional; direct on `0.0.0.0:80`) + rules 10–11 | VERIFIED |

---

## 2. Methodology (how the numbers were produced)

### 2.1 Load shape (VU loop)

- k6 simulates thousands of virtual users; each VU browses like a person: load feed → wait a few
  seconds → open a post → sometimes like → sometimes create. [§02:02] — VERIFIED against
  `bench/load.js:11-14,47-70` (feed → think 3–7 s → post → think 3–8 s → 15 % like → 2 % post →
  idle 5–15 s → repeat).
- k6 runs on a separate machine (`README.md:97-98`). [§02:02 implied] — VERIFIED (`README.md:97-98`).

### 2.2 Pass criteria (all must hold for a full 5-minute sustain; burst does not count)

- 95 % of requests < 500 ms, 99 % < 1 s, < 1 % errors, sustained 5 minutes. [§02:02] — VERIFIED
  (`bench/load.js:38-41` thresholds `p(95)<500`, `p(99)<1000`, `rate<0.01` + `README.md:100-105` scoring).
- "Keep adding users until the server can't meet the requirements" [§02:02] is the video's plain-language
  form of the README limit-search below. No conflict; README + `load.js` win for replication.

### 2.3 Limit-search + scoring (README normative form)

- Warm-up 1,000 users / 2 min (unscored) → probe from 2,500 doubling to fail → narrow to ±250 →
  5-min confirm → step down 250 on fail. Score = max VUs passing a 5-min hold. — VERIFIED
  (`README.md:100-105` + `bench/load.js:26-43` stages; restated in `REPORT_COMPARISON.md` §1 +
  `FACT_CHECK.md` V8; transcript §[02:02] states criteria without the doubling numbers).
- `load.js` env: `BASE_URL` (default `http://127.0.0.1:3000`), `VUS` (default 100), `DURATION` (default 5m),
  `RAMP_UP` (default 60s), `TOKENS` (default `seed/tokens.json`); per-VU stagger `sleep(0–5 s)` on first
  iteration. — VERIFIED (`bench/load.js:20-24,50`).

### 2.4 Load-generator bottleneck (acknowledged limit)

- Prior round needed 1 generator VM; this round some implementations were so fast the k6 machine became
  the bottleneck first (ran out of RAM simulating users). [§02:02] — VERIFIED (transcript statement;
  no SPEC counterpart; accepted as method observation).
- Mitigations considered but rejected mid-experiment: making each VU generate more requests (would change
  the workload implementations optimized for) or buying a bigger generator (provider would not sell one).
  Fix applied: 4 separate generator VMs against the $12 server. [§02:02–§03:02] — VERIFIED (transcript
  statements; method rationale, not a SPEC claim).
- Java consequence: any Java limit-search must keep the generator off the critical path (separate machine,
  watch generator RAM/CPU); a ceiling with idle target CPU + empty generator headroom is a generator
  artifact until proven otherwise. Classification: VERIFIED observation → EXPERIMENTAL HYPOTHESIS
  transfer (method: monitor generator + re-run with added generators; owned by #10 BENCHMARK_STRATEGY
  downstream, not resolved here).

---

## 3. Submissions inventory (who was tested)

- Languages with submissions: C, C++, Rust, Zig, Go, TypeScript, Python, Bun, PHP, Ruby, Erlang,
  Fortran, R, and TypeScript-on-Bun (author lists "Bun" and "TypeScript running on Bun" as overlapping
  entries in captions). [§03:02] — VERIFIED (transcript enumeration).
- 28 submissions received, 26 benchmarked. [§03:02] — VERIFIED (transcript counts).
- Excluded: one C++ that loaded everything into an in-memory database (against the rules; author calls it
  "pretty cool", points to GitHub). [§03:02] — VERIFIED (transcript exclusion + rule-5/6 rationale
  in `README.md:42-49`; SPEC/test win: in-memory DB fails rule 6).
- Excluded: one R submission that could not start on the benchmark machine (missing system library).
  [§03:02] — VERIFIED (transcript exclusion; maps to README rule 10: must build/start on the box
  within 60 s).
- Zero Java submissions, zero Node submissions benchmarked in-video ("Java developers, you guys failed
  me"). [§03:02] — VERIFIED both (time-bounded: benchmarked set at video date) per `FACT_CHECK.md` V23;
  no contradiction with PR33 (opened Oct 10, unmerged/unbenchmarked — see §8.3).

---

## 4. Per-language observations (timestamps, numbers, bottlenecks)

All figures below are the author's reported sustained-hold results. They are video observations
(transcript-primary), not Java measurements. Java transfer for each is an EXPERIMENTAL HYPOTHESIS
unless marked SPEC-verified; methods in §9.

### 4.1 Ruby [§03:53–§04:02]

- Two Rails submissions: 3,000 and 3,500 users. No prior-video baseline (Ruby not in original round).
  — VERIFIED (transcript statement).
- Fastest Ruby: 14,000 users with Iodine (C web server, single worker + single thread) + Ruby YJIT.
  "Barely looks like Ruby." — VERIFIED (transcript statement).
- Optimization: SQLite builds the JSON response directly; Ruby sends it back without building Ruby
  objects or running a serializer. — VERIFIED as observed technique (transcript §[04:02]; recurs in
  §5.3). Rule-compliance: allowed only if byte-exact per `test/test.sh:52-57` golden compare (§7.3).
- At 14,000: P95 50 ms, P99 154 ms, 0 errors, CPU 62 %. At 15,000: falls off a cliff, P99 > 1 s
  (caption says "P99 goes to 2 and 1/2 seconds"), failing condition. — VERIFIED (transcript numbers).
- Bottleneck: tail-latency cliff just above the passing hold (headroom ~1,000 VUs). — VERIFIED
  (transcript observation).

### 4.2 Erlang [§05:03]

- 15,000 users with Cowboy. Lightweight per-connection processes; runtime configured to a single
  scheduler (1 CPU) with one process owning the SQLite connection (spreading across cores would waste
  the single CPU). — VERIFIED (transcript statements).
- JSON generated directly inside SQLite (same shape as Ruby). — VERIFIED (transcript §[05:03]).
- At 15,000: P95 171 ms, P99 320 ms, CPU 83 %, RAM 600 MB (one of the highest in the batch).
  At 16,000: P95 > 4 s — ran out of CPU. — VERIFIED (transcript numbers).
- Bottleneck: CPU. — VERIFIED (transcript attribution).

### 4.3 Python [§05:50–§06:04]

- Original round: FastAPI + Postgres ~2,150. Fastest Python here: 22,000 (~10×). Author explicitly
  flags NOT apples-to-apples (Postgres→SQLite, removed Nginx). [§06:04] — VERIFIED (transcript
  statement + caveat; the 2,150 is a prior-video number, labeled per §8.1).
- First submission: fairly normal FastAPI + SQLite + uvloop + orjson → 11,000. Second developer took
  the same implementation, profiled, found CPU burn inside FastAPI/Starlette (dependency injection,
  middleware, request/response object building — none of it the API work), stripped layers until
  requests go Uvicorn→handler directly → doubled to 22,000. "One of the most interesting results."
  — VERIFIED (transcript statements).
- At 22,000: CPU 91 % (maxed), P95 353 ms, P99 653 ms, 0 errors. At 23,000: P95 > 1 s, P99 3.5 s,
  fail — same failure mode as the original FastAPI, far more users. — VERIFIED (transcript numbers).
- Bottleneck: CPU. Java lesson: framework-layer stripping is a first-order CPU win on 1 vCPU (§9.5).
  — VERIFIED observation → EXPERIMENTAL HYPOTHESIS transfer (method §9.5).

### 4.4 Go [§07:05]

- Tied Python at 22,000 with a single Go submission ("wouldn't put this on Go as a language; limit for
  this implementation"). — VERIFIED (transcript statement + caveat).
- Database handling: every HTTP request does NOT talk to SQLite independently; one Go routine owns the
  DB, collects up to 256 requests, executes, commits writes in a single transaction, responds to all
  afterwards — group commit. Still waits for commit before responding (not cheating). Reduces per-write
  commit overhead. [§07:05] — VERIFIED as observed technique (transcript definition; normative guard in
  `README.md:46-49` rule 6; recurs §5.4).
- Limitation: above ~16,000 open connections tells clients to disconnect/reconnect; at this scale
  reconnect churn is expensive; author suspects untapped headroom, invites Go developers to comment/PR.
  — VERIFIED (transcript limitation + open invitation).
- At 22,000: P95 143 ms, P99 318 ms, error rate 0.5 % (limit 1 %). At 23,000: crossed 1 % errors, fail.
  CPU only 78 % — NOT a CPU bottleneck, a connection bottleneck. — VERIFIED (transcript numbers +
  attribution).

### 4.5 C# [§08:05]

- Original round: 4,400. This implementation: 32,000 (still Kestrel, the normal ASP.NET Core server).
  — VERIFIED (transcript numbers; 4,400 is prior-video, labeled §8.1).
- Optimizations: removed everything else (no middleware, routing layer, regular HTTP-context objects);
  tiny app talks to Kestrel low-level interfaces. Memory allocation: pooled buffers for JSON, token
  verification in stack-allocated memory; thousands of requests without per-request object churn for the
  collector. On 1 CPU avoiding that work matters. — VERIFIED as observed techniques (transcript §[08:05]).
- At 32,000: P95 346 ms, P99 731 ms, 0 errors, CPU 93 % (highest of any top-15 submission) — ran out
  of CPU. Caption "At 3,300 users, P95 went over a second" is read as 33,000 (missing zero in
  auto-captions; audio authoritative). — PARTIALLY VERIFIED (numbers VERIFIED; fail-point magnitude
  inferred from context — 3,300 contradicts the 32,000 pass, so 33,000 is the coherent reading;
  flag for audio check, do not quote 3,300 as a result).
- Bottleneck: CPU. — VERIFIED (transcript attribution).

### 4.6 Fortran [§09:39–§10:07]

- Unexpected entry (language from the 1950s; author's mother was a Fortran developer). Beats
  Python/Go/C# at a web API — architecture over language. — VERIFIED (transcript framing).
- libmicrohttpd (C) handles HTTP with a small C→Fortran bridge. JSON parse/build via SQLite JSON
  functions (no Fortran JSON library); even token validations as SQL queries. Group commit for writes
  (like Go). — VERIFIED as observed techniques (transcript §[09:39]–§[10:07]).
- At 38,000: P99 944 ms, CPU 85 %, RAM 700 MB. Caption "At 3,900 users, P99 went to 1.7 s" is read as
  39,000 (same missing-zero caption pattern as §4.5). — PARTIALLY VERIFIED (same caveat: numbers
  VERIFIED, fail-point magnitude inferred; audio authoritative).
- Bottleneck: CPU-adjacent (85 % at pass; tail exceeds 1 s just above). — VERIFIED (transcript numbers).

### 4.7 PHP [§10:07–§11:08]

- Original round: Laravel 750; author-optimized bare PHP + PHP-FPM 2,700. This submission: 40,000
  (>50× Laravel) in 250 lines of PHP. — VERIFIED (transcript numbers; 750/2,700 are prior-video,
  labeled §8.1).
- Decisive difference: Swoole (C extension) runs PHP as a long-running server, not per-request PHP-FPM.
  Original problem was per-request framework-init overhead; Swoole starts once, opens one DB connection,
  prepares queries, keeps running — later requests skip init. JIT enabled. — VERIFIED as observed
  technique (transcript §[10:07]–§[11:08]).
- Second Swoole submission: 39,000; that developer additionally patched SQL to reduce idle-connection
  memory. — VERIFIED (transcript statement).
- At 40,000: P95 189 ms, P99 853 ms, 0 errors. At 41,000: P95 still fine (182 ms) but P99 ~2.4 s —
  most users fine, tail fails. — VERIFIED (transcript numbers).
- Bottleneck: tail latency (P99-only fail), not median. — VERIFIED (transcript observation).

### 4.8 C [§11:52–§13:09]

- Fastest C: 46,000 with io_uring (submit ops to kernel + collect results in batches; less syscall
  round-trip). — VERIFIED (transcript statements).
- Optimized SQLite itself: ran a representative workload through SQLite, found hot paths, compiled a
  workload-optimized SQLite binary (PGO shape). Rewrote the feed query to avoid per-post like-counts
  where a single range scan suffices. — VERIFIED as observed techniques (transcript §[12:08]).
- Failed at 47,000 while using ~1/3 CPU — NOT raw-processing limited; some other bottleneck (probably
  connection handling or internal queue limits); author sees hidden headroom. — VERIFIED (transcript
  attribution + open question).
- At 46,000: P95 54 ms, P99 178 ms, 0 errors, RAM 39 MB (least of any submission). At 47,000: median
  still 5 ms, P99 1,000+43 ms — missed the 1 s threshold by 43 ms. — VERIFIED (transcript numbers).
- Author's best guess (explicitly NOT profiled): 8 worker threads on 1 CPU taking turns on a single
  lock + waiting for 128 events before processing a batch → unlucky requests queue behind others,
  shows in P99 first. Invites developers to comment. — VERIFIED as acknowledged-unproven guess
  (transcript §[13:09] "I didn't profile it to prove that").
- Bottleneck: lock/queueing structure (hypothesized), not CPU/RAM/generator. Classification:
  EXPERIMENTAL HYPOTHESIS (author's own label: unprofiled).

### 4.9 TypeScript on Bun [§13:09–§14:11]

- Original round: Bun 4,200. This submission: 47,000 (>11×) in ~350 lines TypeScript. — VERIFIED
  (transcript numbers; 4,200 is prior-video, labeled §8.1).
- NOT Bun's normal HTTP server: raw TCP sockets + just-enough HTTP parsing (scan incoming bytes for
  needed headers). SQLite generates JSON, returned as raw bytes (never becomes a JS string before send
  — "allowed according to my rules"). Reusable output buffer. C library call raises connection backlog
  beyond Bun defaults. Extreme strip-everything shape. — VERIFIED as observed techniques
  (transcript §[13:09]–§[14:11]; rule-mapping in §7.3–§7.4).
- Trade-off stated immediately: specialized server for this one API, not a general production API.
  — VERIFIED (transcript §[14:11]).
- At 47,000: P95 96 ms, P99 356 ms, 0 errors, CPU 72 %, RAM 150 MB (less than Erlang/Go/Python).
  At 48,000: P95 12 s + >1 % failures. — VERIFIED (transcript numbers).
- Bottleneck: cliff above 47,000 (latency + errors together). — VERIFIED (transcript observation).

### 4.10 Rust (two implementations) [§14:11–§16:12]

- Axum (same framework as original): 43,000 with one thread + dedicated DB task batching writes
  (like Go). Started closing connections above 40,000, likely contributed to the ceiling. At 43,000
  latency fine but errors appearing; 44,000 P99 1.3 s; 52,000 6 % failing. Most memory: 1.1 GB of 2 GB.
  — VERIFIED (transcript numbers + attribution).
- io_uring: like C, plus kernel-managed socket references directly (less file-descriptor-table overhead).
  Event loop collects a batch of events, executes, commits, sends responses (group-commit shape).
  64,000 with P99 only 224 ms, ~50 % CPU, <100 MB RAM. — VERIFIED (transcript numbers).
- Then the platform ceiling: every k6 VU holds an open connection; Linux connection-tracking table sized
  for 65,000 entries; beyond ~64,000 the kernel drops packets. Author could have tuned the OS but rules
  forbid kernel tuning (same constraints for all) → 64,000 practical maximum. Rust hit it with half its
  CPU free — true ceiling unknown, "could be significantly more". — VERIFIED (transcript §[15:11]–§[16:12];
  rule-mapping: `README.md:59-62` rule 11 "nothing on the box gets tuned").
- CPU/RAM as tiebreakers (author's framing, reused as ground truth): at the shared 64k ceiling, headroom
  decides. — VERIFIED (transcript framing + `FACT_CHECK.md` V24).

### 4.11 Zig [§16:43–§17:12]

- 61,000 in ~680 lines with uSockets (small C networking library); group-commit batching again.
  — VERIFIED (transcript statements).
- Ceiling is self-imposed: hardcoded 60,000 persistent-connection limit; above it the server forces
  disconnect/reconnect (same churn cost as Go §4.4); stale entries linger in the conntrack table toward
  the platform limit. At 62,000 latency great (P99 268 ms) but errors 1.02 % (forced reconnects) → fail.
  CPU 69 % (well below limits). Same developer's two C++ implementations share the limit and land at the
  same 61,000 — author suspects Zig reaches 64,000 with the setting changed. — VERIFIED (transcript
  numbers + attribution); the 64k-with-fix is an EXPERIMENTAL HYPOTHESIS (author's "I suspect", method:
  raise limit, re-run 5-min hold, watch errors + conntrack).
- Bottleneck: connection-limit + error budget, not CPU/latency. — VERIFIED (transcript attribution).

### 4.12 C++ (tied first) [§17:44–§19:14]

- 64,000 tied with Rust: one thread, one event loop, direct SQLite; no worker pools, locks, or reverse
  proxies. — VERIFIED (transcript statements).
- Inside-SQLite optimization: profiled, found SQLite spending 13 % of app CPU scanning its own page
  cache on commit → shrank SQLite internal cache to almost nothing + memory-mapped the DB so reads use
  the OS page cache (less SQLite-side work). Moved checkpoint (WAL housekeeping) off the response path
  so requests never wait on it. — VERIFIED as observed techniques (transcript §[17:44]–§[18:13];
  rule-mapping §7.5).
- Skepticism + ruling: author was skeptical on caching rules, then ruled VALID — not a private in-memory
  structure; warming the OS file cache is normal database behavior and allowed. — VERIFIED
  (transcript ruling; normative form in `README.md:42-45` rule 5: page cache + `mmap` fine).
- Durability asterisk: WAL with `synchronous=NORMAL` — valid, passes rules, but weaker than FULL: a
  commit can acknowledge without guaranteeing survival of a sudden power loss. "Commit before respond"
  does NOT automatically mean power-loss durability. Author asterisks this one. — VERIFIED
  (transcript §[18:13]; normative form `README.md:46-49` rule 6: NORMAL or stronger, no OFF).
- At 64,000 (conntrack ceiling shared with Rust): C++ P99 910 ms vs Rust 224 ms; C++ CPU ~68 % vs Rust
  ~50 %. Tie on score, Rust wins on headroom → "fastest language in the community benchmark".
  — VERIFIED (transcript numbers + tiebreak rationale; reused as 64k ground truth per §0.2).

---

## 5. Recurring optimizations (what came up again and again) [§19:49–§20:15]

Author's own takeaway list. Each is a video observation (VERIFIED as observed); Java transfer is an
EXPERIMENTAL HYPOTHESIS with method (§9) unless SPEC-decided.

1. **Drop Nginx, serve directly.** Almost all fastest served HTTP directly. Nginx is production-useful
   (TLS, load balancing, buffering, routing) but on 1 tiny CPU every request pays an extra process +
   data movement + CPU. Not universal (Axum + one Rails kept it). [§19:49] — VERIFIED observation.
   Normative anchor: `README.md:55-58` rule 9 (14–20 % Nginx CPU in-video; direct must handle ~15k
   keep-alives itself). Transfer: needs-experiment E4 (same build behind Nginx vs direct; §9.1).
2. **Prepare SQLite queries once, reuse a single DB connection.** Avoids re-parsing SQL, more
   predictable access. [§20:15] — VERIFIED observation. Normative anchor: `README.md:42-45` rule 5
   (prepared-statement reuse explicitly fine). Transfer: adopt as baseline (§9.2).
3. **Generate JSON inside SQLite.** Skip app objects + serializer; send SQLite-built bytes. Seen in
   Ruby/Erlang/Bun/Fortran (and C++ page-cache shape adjacent). [§04:02]–[§10:07] + [§20:15] —
   VERIFIED observation. Transfer: EXPERIMENTAL HYPOTHESIS — must stay byte-exact (§7.3; method §9.3).
4. **Group commit.** Bundle multiple writes into one transaction + commit together; on write-heavier
   mixes the win grows. Every instance in-video waits for commit before responding (rule guard).
   Seen in Go/C++/Rust/Zig/Fortran. [§07:05]–[§18:13] + [§20:15] — VERIFIED observation.
   Normative anchor: `README.md:46-49` rule 6. Transfer: EXPERIMENTAL HYPOTHESIS (method §9.4).
5. **Go beyond / replace the framework.** Own servers, memory management, event loops (Python layer
   strip §4.3, C# Kestrel-low-level §4.5, Bun raw-TCP §4.9, C++ single-file server §4.12, Rust unsafe +
   modern-Linux §4.10). [§20:15] — VERIFIED observation. Transfer: measure-first strip (method §9.5);
   framework default is the baseline, not the ceiling.

---

## 6. Bottlenecks, attempted optimizations, acknowledged limits

### 6.1 Bottleneck catalog (per-submission, transcript-attributed)

| Submission (pass → fail) | Bottleneck (author attribution) | Evidence | Verdict |
|---|---|---|---|
| Ruby 14k → 15k | Tail cliff (P99 154 ms → 2.5 s) | §[04:02] | VERIFIED |
| Erlang 15k → 16k | CPU (83 % → P95 4 s) | §[05:03] | VERIFIED |
| Python 22k → 23k | CPU (91 % → P95 1 s / P99 3.5 s) | §[06:04] | VERIFIED |
| Go 22k → 23k | Connections (CPU 78 %; 0.5 % → >1 % errors; >16k forced reconnects) | §[07:05]–§[08:05] | VERIFIED |
| C# 32k → ~33k | CPU (93 %, highest top-15) | §[08:05]–§[09:05] | VERIFIED (fail-point magnitude PARTIALLY VERIFIED per §4.5) |
| Fortran 38k → ~39k | CPU-adjacent tail (85 %; P99 944 ms → 1.7 s) | §[09:39]–§[10:07] | VERIFIED (same caption caveat §4.6) |
| PHP 40k → 41k | Tail-only (P95 flat 182 ms; P99 853 ms → 2.4 s) | §[10:07]–§[11:08] | VERIFIED |
| C 46k → 47k | Structure, not CPU (CPU ~1/3; P99 178 ms → 1,043 ms, miss by 43 ms) | §[12:08] | VERIFIED observation; lock/128-batch cause EXPERIMENTAL HYPOTHESIS (§4.8) |
| Bun 47k → 48k | Cliff (P95 96 ms → 12 s + >1 % errors) | §[13:09]–§[14:11] | VERIFIED |
| Rust Axum 43k → 44k+ | Connections + errors (>40k forced closes; 6 % fail by 52k; 1.1 GB RAM) | §[14:11] | VERIFIED |
| Rust io_uring 64k | Platform conntrack (65k table; half CPU free; true ceiling unknown) | §[15:11]–§[16:12] | VERIFIED |
| Zig 61k → 62k | Self-imposed conn limit (P99 268 ms fine; 1.02 % errors from forced reconnects) | §[16:43]–§[17:12] | VERIFIED |
| C++ 64k | Platform conntrack (shared ceiling; P99 910 ms + CPU 68 % leave Rust more headroom) | §[17:44]–§[19:14] | VERIFIED |

### 6.2 Attempted optimizations that moved the needle (author-highlighted)

- FastAPI→Uvicorn-direct strip doubled Python 11k→22k (§4.3). — VERIFIED.
- Swoole long-running + prepared-once + JIT took PHP 750→40,000 (§4.7). — VERIFIED.
- PGO-compiled SQLite + feed-query rewrite lifted C to 46k (§4.8). — VERIFIED.
- Raw-TCP + SQLite-raw-bytes + reusable buffer + raised backlog lifted Bun 4.2k→47k (§4.9). — VERIFIED.
- io_uring + kernel socket refs + batch-commit lifted Rust to the 64k platform ceiling (§4.10). — VERIFIED.
- Page-cache shrink + `mmap` + off-path checkpoints lifted C++ to the shared 64k ceiling (§4.12). — VERIFIED.
- Group commit wherever writes exist (Go/C++/Rust/Zig/Fortran). — VERIFIED (§5.4).

### 6.3 Acknowledged limits (author-stated, do not treat as results)

- Load generator was the bottleneck before the target for the fastest entries; fixed with 4 VMs (§2.4).
  — VERIFIED.
- C lock/128-batch diagnosis unprofiled (§4.8). — VERIFIED as unproven.
- Zig/C++ 61k limit-setting fix untested (§4.11). — VERIFIED as untested.
- Rust true ceiling above 64k unknown (conntrack stopped the experiment) (§4.10). — VERIFIED as unknown.
- Prior-round comparisons flagged non-comparable where the stack changed (Python Postgres→SQLite +
  no-Nginx, §4.3). — VERIFIED as author-hedged.
- Caption numbers at §[09:05] ("3,300") and §[10:07] ("3,900") are missing-zero reads; audio authoritative
  (this doc uses 33,000/39,000 as coherent readings, flagged PARTIALLY VERIFIED). — PARTIALLY VERIFIED.
- Promo segment (DevMax/Discord) [§22:17] is not experiment content. — VERIFIED as out-of-scope
  (excluded from all claims below).

---

## 7. Transcript-vs-SPEC conflicts (resolved for SPEC/test)

SPEC/test win every row. Transcript is quoted for the observation; the Resolution column is normative.

| # | Apparent conflict | Resolution (SPEC/test win) | Verdict |
|---|---|---|---|
| S1 | Transcript "same server constraints, no kernel tuning" vs SPEC `LimitNOFILE=65535` + `CAP_NET_BIND_SERVICE` | No conflict: SPEC `SPEC.md:18-21` grants file + bind capability inside the process; README rule 11 (`README.md:59-62`) forbids box-level kernel tuning — identical to the video ruling §[15:11]–§[16:12]. Follow both. | VERIFIED |
| S2 | SPEC "expect ~15,000 open connections" (direct) vs video 64,000 open connections at the ceiling | SPEC number is a sizing expectation, not a cap; `LimitNOFILE=65535` covers 64k; video proves a top contender must hold ~64k keep-alives. Java impl sizes for 64k conns AND passes SPEC's 65 s idle rule (`SPEC.md:18-21`). | PARTIALLY VERIFIED (SPEC text real; 15k read as floor-expectation, video as ceiling-evidence) |
| S3 | "SQLite returns JSON as raw bytes, allowed" (Bun §[13:09]) vs byte-exact goldens | Allowed ONLY if bytes are exactly the golden bytes: compact JSON, exact key order, `Content-Type: application/json`, text `created_at` passthrough, live `like_count` (`SPEC.md:23-43`; `test/test.sh:52-57,95-123`). Test wins; any SQLite-JSON shape must pass `test/test.sh` on a fresh DB. | VERIFIED (rule + guard) |
| S4 | "Batch multiple writes into one transaction" (video §[01:01]/§[07:05]) vs durability | Allowed ONLY with the README guard: each response waits for its commit; WAL `synchronous=NORMAL` or stronger; no OFF/OFF/MEMORY/in-memory (`README.md:46-49` rule 6). Test + rule win. | VERIFIED |
| S5 | C++ "shrink SQLite cache + `mmap`, move checkpoints" vs "no in-memory copies" | VALID per the video ruling (§[18:13]) AND README rule 5 (`README.md:42-45`): OS page cache + `mmap` are the database; private response/query caches, table copies, remembered JWT verifications are prohibited. Reusing prepared statements is fine (plan cache, not data). | VERIFIED |
| S6 | `synchronous=NORMAL` asterisk (video §[18:13]) vs "commit before respond" | Both true: NORMAL passes the rules but a 201 is NOT a power-loss guarantee. Follow README rule 6 (NORMAL or stronger); document the asterisk in any NORMAL-based Java design; never silently upgrade the claim to FULL durability. | VERIFIED |
| S7 | "Verify token signature every request" (video §[01:01]) vs JWT edge table | SPEC details win: HS256-only, verify signature + `exp`, `sub` positive-integer string + `username` string or `invalid token payload`, auth-before-id ordering (`SPEC.md:72-79`; `test/test.sh:68-83,113`). | VERIFIED |
| S8 | "5 endpoints" (video §[01:01]) vs error/method edge cases | SPEC error table + out-of-scope note win (`SPEC.md:59-72,81-84`): bodies >16 KB, ids >2^53, hex/exponent ids, wrong methods on known paths do whatever the framework does. | VERIFIED |

SPEC-conflict count this ticket: 8 rows (S1–S8), all resolved for SPEC/test, zero left open.

---

## 8. Video vs later changes (separation log)

### 8.1 Prior video `sQXFhh_PiG4` (do NOT re-attribute)

- Prior-round figures appearing in-transcript or in third-party summaries: 8-language defaults, Rust +
  SQLite ~14,000 peak [§00:00]; FastAPI + Postgres ~2,150, C# ~4,400, Laravel ~750 / bare-PHP ~2,700,
  Bun ~4,200 (each cited at its language section as "original video" baselines). — VERIFIED as
  prior-video statements within the current transcript; OUTDATED as current Java guidance per V14.
- Third-party summary table (Spring 5.1k/10.25k, Go 11.75k, Rust 14.05k) evaluated in
  `REPORT_COMPARISON.md` §3/V14 is NOT in the current transcript and is NOT reused here except as the
  OUTDATED example. — OUTDATED (per parent; no new verification attempted — out of scope).

### 8.2 Oct-13 DB post (adjacent, not the video round)

- Go + SQLite comparison (~13.4k class) per parent V15/`FACT_CHECK.md` V15. Real post, same box class,
  Go app — NOT the QGWKCaglSBI round. Kept ONLY as `NORMAL`-vs-`FULL` durability context for §7/S6;
  never as a Java throughput target. — PARTIALLY VERIFIED (source real, context adjacent; parent verdict
  reused).

### 8.3 Later repo/community changes (after the video snapshot)

- PR #33 Java Spring Boot (`7a9cb997`, 4 commits, 35 files, OPEN/unmerged/0 reviews/7 comments/no
  numbers; MVC/Tomcat behind Nginx; virtual-threads flag absent; WebFlux absent; Hikari 4/1; keep-alive
  70 s; WAL + NORMAL + FK-on + busy 5 s; author self-declares unoptimized baseline). — VERIFIED
  (reused from `REPORT_COMPARISON.md` §1 + `FACT_CHECK.md` V9–V10; SHAs re-verified §10; code detail
  owned by #4, not re-derived here).
- "Zero Java / zero Node" (§3) is time-bounded to the benchmarked video set; PR33's existence (Oct 10–13,
  unmeasured) is no contradiction per parent V23. — VERIFIED both (time-bounded).
- Any post-video merged-submission batches or re-benchmarks are out of this ticket's snapshot (video +
  baseline `026bdb6` + PR head `7a9cb997` at inspection). Future batches belong to #6/#8/#11 tracking,
  not to this reconstruction.

---

## 9. Java-applicable lessons (observation → transfer, classified)

Each lesson states the video observation (classified), the Java transfer (classified), and the method
where measurement is missing. Nothing below is a Java benchmark result — no Java bench exists in
evidence (`FACT_CHECK.md` V19–V20).

- **J1 — Nginx-first for comparability, then direct as experiment.** Observation VERIFIED (§5.1):
  fastest went direct; some kept Nginx. Transfer EXPERIMENTAL HYPOTHESIS: sequence Nginx-first
  (64 idle upstream, `127.0.0.1:3000`, idle ≥65 s per `SPEC.md:18-19`) then direct (`0.0.0.0:80`,
  ~64k keep-alives, `LimitNOFILE=65535`, non-root bind per `SPEC.md:20-21`). Method E4
  (`FACT_CHECK.md` §2): same build both modes, 5-min holds, p95/p99/err/CPU/RSS spread.
- **J2 — Single DB-owner connection + prepare-once/reuse.** Observation VERIFIED (§5.2 + Erlang §4.2 /
  PHP §4.7): one owner, prepared reuse, predictable access. Transfer: adopt as Java baseline
  (rule-5-explicit: prepared reuse is plan cache, not data). Pool size/split stays EXPERIMENTAL
  HYPOTHESIS: Method E5 — pool sweep + `busy_timeout` + p99 under write load; pool-4 is a starting
  point explicitly not optimum (parent V10/V19); keep write sections short, judge tail not mean.
- **J3 — JSON built in SQLite.** Observation VERIFIED (§5.3). Transfer EXPERIMENTAL HYPOTHESIS:
  must preserve exact key order, escaping, UTF-8, per-request HMAC semantics and pass
  `test/test.sh` goldens on a fresh DB (§7/S3). Method E7-part: SQL-baseline first, then SQLite-JSON
  variant, byte-compare + 5-min holds.
- **J4 — Group commit.** Observation VERIFIED (§5.4 + §4.4/§4.10–§4.12). Transfer EXPERIMENTAL
  HYPOTHESIS (second-order at ~100 writes/s under the realistic mix per parent §4.4 arithmetic from
  `load.js` probabilities — not a bench result): single-writer queue + drain +
  `BEGIN IMMEDIATE` … `COMMIT` with "respond only after the shared commit" guard. Method E8
  (`FACT_CHECK.md` §2): same-SQL baseline vs grouped variant, write-heavy + realistic mixes, p99/err
  gates; failure-atomicity (one txn fail → all waiters fail together) and slow-head-of-line scheduling
  must be designed, not assumed (§6.3 + video §[21:15]).
- **J5 — Strip framework layers after profiling (measure-first).** Observation VERIFIED (§5.5 + Python
  11k→22k §4.3 / C# low-level Kestrel §4.5): layers that only move requests (DI, middleware, context
  objects) burn 1-CPU budget. Transfer EXPERIMENTAL HYPOTHESIS: baseline on the chosen stack, profile,
  then strip what the profile indicts (handler-direct routing, pooled JSON buffers, stack-scoped token
  scratch). Do not pre-strip for style; the video's strip wins were all profile- or
  measurement-driven. Method: profile-gated strip with 5-min holds per step (downstream #7/#8 own the
  schedule).
- **J6 — Allocation discipline (pooled buffers, scoped scratch).** Observation VERIFIED (C# §4.5, Bun
  reusable buffer §4.9): fewer short-lived objects → less collector work on 1 CPU. Transfer
  EXPERIMENTAL HYPOTHESIS for Java: measure allocation rate + GC time alongside p99 (not just
  throughput); sweep heap × GC on target (parent E6: pin JDK 25, stay well under 2 GB incl.
  native/SQLite/Nginx/OS, no swap; RSS + GC-log + p99 gates).
- **J7 — Long-running process with warm prepared state (Swoole shape).** Observation VERIFIED (§4.7):
  start once, open conn, prepare queries, serve. Transfer: JVM servers already have this shape — adopt
  as baseline expectation, not an experiment (startup ≤60 s per `README.md:59-60` rule 10; warm-up
  1000/2 min absorbs JIT warm-up per parent V8). Classification: VERIFIED baseline posture.
- **J8 — Concurrency shape: readers parallel (WAL), single writer serializes.** Observation VERIFIED
  (all group-commit entries + Erlang single-owner §4.2): more threads do not create CPUs or remove the
  single-writer limit. Transfer EXPERIMENTAL HYPOTHESIS: virtual threads / platform threads / NIO tuning
  A/B on target with identical SQL/pragmas/pool (parent E1); WebFlux only if a profile shows
  thread-blocking as the bottleneck (parent E2); NIO/raw path only profile-gated for conn-count
  (parent E3). No winner declared here.
- **J9 — SQLite-internal wins (PGO, `mmap`/page-cache, checkpoint placement, feed-query rewrite).**
  Observation VERIFIED (§4.8/§4.12): PGO binary, cache-shrink + `mmap` to OS cache, checkpoints off the
  response path, range-scan feed rewrite. Transfer EXPERIMENTAL HYPOTHESIS, late-stage only after a
  correct baseline + profile (parent E7): JVM memory accounting differs (cache-shrink legality reasoning
  does not transfer automatically); each change single-variable with byte-compare + durability-rule
  (S5/S6) gates.
- **J10 — Connection handling is a first-class bottleneck.** Observation VERIFIED (Go §4.4, Axum §4.10,
  Zig §4.11, conntrack §4.10/§4.12): forced reconnects and backlog/limit settings fail runs with healthy
  latency/CPU. Transfer EXPERIMENTAL HYPOTHESIS: size backlog/keep-alive/file limits for 64k conns from
  the start (SPEC §S1–S2); never gate a Java conclusion on a run with forced disconnections or a full
  conntrack table — fix the harness first (§2.4/§6.3).
- **J11 — Headroom decides ties; report it.** Observation VERIFIED (§4.10/§4.12 tiebreak): at a shared
  ceiling, lower P99 + lower CPU + lower RAM wins the "fastest" call. Transfer: every Java experiment
  reports P95/P99/err + CPU + RSS at pass AND at fail+1 step (table shape of §6.1), not just the passing
  VU number. Classification: VERIFIED reporting policy.

---

## 10. Verification steps run (this session)

- `git status` clean on `research/java-analysis`; `HEAD = main = origin/research/java-analysis =
  820db12`; `backup/main-baseline-2026-10-13 = 026bdb6` intact; `reference/spring-boot-pr33 = 7a9cb99`
  untouched (fresh `git status` + `git rev-parse` + `git log --graph` before work; re-verified before
  commit per gate below).
- Precondition: `gh issue view 3` OPEN; `gh issue view 2` + `gh issue view 5` CLOSED; Epic #1 OPEN
  with Decisions for #2/#5. If #3 had not been OPEN the session would have stopped (it was OPEN).
- Claimed #3 via `gh issue comment 3` before work (parallel-session guard).
- Inputs in §0.4 read in full; 64k + video-ID ground truth reused from parent §1/§4.10 (not re-derived);
  no prior-video numbers re-attributed (V14 lesson observed: §4.3/§4.5/§4.7/§4.9 + §8.1 all date
  baselines to the prior round).
- Timestamp-coverage check: every section §1–§9 carries §mm:ss stamps in the Ch. 0:00–22:17 range;
  promo [§22:17] explicitly excluded (§6.3); caption-slip magnitudes flagged PARTIALLY VERIFIED (§4.5,
  §4.6) rather than silently rounded.
- SPEC-conflict check: §7 holds S1–S8 (8 rows), all resolved for SPEC/test, zero left open.
- Video-ID separation check: QGWKCaglSBI (current) vs sQXFhh_PiG4 (prior, OUTDATED) vs Oct-13 DB post
  (adjacent, PARTIALLY VERIFIED) vs PR33/later repo (time-bounded, §8.3) — four buckets, no mixing.
- Classification summary: VERIFIED 38 / PARTIALLY VERIFIED 5 (§4.5, §4.6, §7/S2, §8.2, caption notes) /
  OUTDATED 2 (prior-video guidance §8.1 + V14 reuse) / EXPERIMENTAL HYPOTHESIS 11 (J1–J6, J8–J10 +
  §4.8-cause + §4.11-fix + §2.4-transfer) / INCORRECT 0 / UNSUPPORTED 0 as standalone video claims
  (PR-comment hypotheses V11–V12/E1–E3 reused by reference, not re-scored — owned by #2/#5).
  Counts are descriptive of this doc's rows, not invented benchmarks.
- Forbidden-word grep over the draft and over the final file before commit — empty both times.
- Skills: see §0.3 for invoked vs skipped-with-reason. Standing gate
  `verification-before-completion` observed before every claim above and before commit/push/close below.

*No implementation started. Next: frontier #4/#8 remain for parallel sessions; this doc feeds #9
JAVA_ARCHITECTURE_DECISION + #10 synthesis downstream (wiring in Epic #1 comment).*
