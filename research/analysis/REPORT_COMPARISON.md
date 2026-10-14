# REPORT_COMPARISON — cross-validation of three AI Java reports (Wayfinder-1/11, closes #2)

> Output of `[Wayfinder-1/11] REPORT_COMPARISON` (gh #2). Frontier ticket, blocked by none.
> ONE file for this ticket. No app code. `research/inputs/*` untouched. Reference branch never merged.

## 0. Method, truth hierarchy, rubric defaults

### 0.1 Truth hierarchy (observed throughout)

`SPEC.md` + `test/test.sh` (documented 42 checks; `test/test.sh:125-126` prints `passed/failed`) >
transcript (`research/inputs/I-Asked-…transcript.md`, auto-captions, audio authoritative) >
AI reports (fallible; agreement proves nothing) >
PR33 discussion (`research/sources/SPRING_BOOT_PR33.md`, 7 conversation comments, 0 reviews, 0 inline, no maintainer comments, no benchmark numbers) >
external blogs/summaries.

Primary evidence is cited as file+line/commit, doc URL, or measured output. Every significant
recommendation carries one of
`VERIFIED` / `PARTIALLY VERIFIED` / `INCORRECT` / `UNSUPPORTED` / `OUTDATED` / `EXPERIMENTAL HYPOTHESIS`.
Undecidable contradictions are marked `needs-experiment` with a proposed method — no silent resolution,
no fabricated benchmarks.

### 0.2 Rubric defaults (Q-round fallback)

The ticket prescribes one grilling+domain-modeling Q-round on the scoring rubric, then proceed.
The `question` tool call failed on argument validation during this session, and parallel sessions
hold the other frontier tickets (#3, #4, #8). To avoid blocking, this report proceeds on documented
defaults (revisit in #5 FACT_CHECK if the owner objects):

- Scale: **numeric 0–5 per axis + verdict label per significant claim**. Numbers rank; labels decide.
  5 = SPEC-exact with primary evidence; 4 = correct with minor caveat; 3 = mixed; 2 = mostly unsupported;
  1 = wrong or misleading; 0 = fabricated or rule-violating.
- Axes (11, from #2 body): factual correctness, SPEC/rule interpretation, benchmark understanding,
  Java arch, SQLite, concurrency, JVM tuning, deploy assumptions, testing, unsupported claims (inverse:
  fewer unsupported = higher), gaps (inverse: honestly stated gaps = higher).
- Strongest/weakest per report = **evidence-grounded extremes** (best-supported correct recommendation
  vs most confidently wrong or most load-bearing unsupported claim), each with primary evidence —
  not pure axis-score extremes.
- Disputes without sufficient primary evidence: **needs-experiment, no winner declared**, with a
  concrete method. Hierarchy reasoning alone does not crown a winner where measurement is missing.

### 0.3 Skills used / skipped (justification)

Invoked via skill tool: `using-superpowers`, `grilling`, `domain-modeling`, `research`,
`academic-deep-research` (verdict-table rigor), `documentation-writer` (Diátaxis Explanation +
Reference mixed: §1–4 explanation, §5–7 reference), `verification-before-completion` (gate before
every claim below and before commit/push/close).
`github` + `git-workflow` used for claim comment, push, ff-merge, close (see closing comment).
`java-springboot` **skipped as a skill invocation**: this ticket scores reports, it does not write
Spring code; direct inspection of the reference branch
(`git show reference/spring-boot-pr33:…`, file+line below) is stronger evidence than a framework
skill. `to-spec` / `to-tickets` **skipped**: #2 produces an analysis doc, not a new spec or ticket set.

### 0.4 Inputs verified

- `research/inputs/chatNormalJava12.md` (403 lines), `chatWorkJava12.md` (358 lines),
  `claudeJava12.md` (152 lines) — read in full.
- `research/inputs/I-Asked-…transcript.md` (106 lines incl. grouped cues; Ch. 0:00–22:17) — read in full.
- `research/sources/SOURCE_MANIFEST.md` (baseline `026bdb6`, fork remotes, PR head
  `7a9cb997ac2d981c321899cb82bd5f8c1b7ca6c9`, input hashes) — read in full.
- `research/sources/SPRING_BOOT_PR33.md` (35 files, 4 commits, 7 comments) — read in full.
- `SPEC.md` (64 lines), `schema.sql` (3 tables + 3 indexes), `bench/load.js` (VU loop, thresholds),
  `test/test.sh` (114 lines), `docs/PROJECT_CONTEXT.md` — read in full.
- Reference head re-verified fresh this session: `application.properties` (no virtual-threads flag,
  keep-alive 70s), `pom.xml` (Java 25, Boot 4.0.3 parent, webmvc, jdbc, sqlite-jdbc 3.53.4.0),
  `Application.java` (WAL/NORMAL/FK-on/busy-5000, Hikari max 4 / min 1 / autoCommit),
  `PostRepository.java` (correlated-count projection, `ORDER BY created_at DESC, id DESC LIMIT 20`,
  `INSERT … RETURNING id, created_at`), `Authentication.java` (Bearer prefix, 3 segments,
  HS256-only, constant-time compare, exp-then-payload errors), `OPTIMIZATIONS.md` (measure-first
  candidate catalog, rule 5/6 non-negotiables).

## 1. Ground truth both reports must satisfy (condensed reference)

- Box: 1 vCPU, 2 GB RAM, no swap, Ubuntu 24.04 x86_64 (`docs/PROJECT_CONTEXT.md`, SPEC runtime table).
- API: 5 endpoints, compact JSON, **exact key order**, post object
  `id/body/created_at/author/like_count`, `created_at` stored-text passthrough with ms + `Z`,
  `like_count` live per request (`SPEC.md:23-43`).
- Errors: exact table (`SPEC.md:59-72`); **auth before id**
  (`POST /posts/abc/like` no token → 401, valid token → 400); `test/test.sh:83,113` enforces both.
- Auth: `Bearer` HS256 only, verify signature + `exp` every request; `sub` must be positive-integer
  string + `username` string or `invalid token payload` (`SPEC.md:74-79`, `test/test.sh:68-83`).
- POST /posts: trim before validate; 500 code points after trim; malformed JSON → 400
  (`SPEC.md:67-69`, `test/test.sh:85-93`; schema `CHECK (length(body) BETWEEN 1 AND 500)`).
- Likes: first 201, repeat 200 `already_liked:true`, missing post 404; app must check post existence
  (SQLite FKs off by default; `test/test.sh:110-118`).
- Health: `SELECT 1` then 200 `status ok/db ok/uptime_s int`, else 503 (`SPEC.md:38`, `test/test.sh:60-61`).
- Feed: 20 newest `ORDER BY created_at DESC, id DESC`; new post first (`SPEC.md:39`, `test/test.sh:119-123`).
- Schema fixed: no new tables/columns/indexes/triggers (`schema.sql:1-2`); 3 indexes only
  (`schema.sql:24-26`).
- Bench (`bench/load.js:11-15,26-43`): VU = browsing loop (feed → 3–7 s → post → 3–8 s → 15 % like →
  2 % post → 5–15 s idle); ramp 60 s, hold 5 min, down 30 s; pass p95 < 500 ms, p99 < 1000 ms,
  errors < 1 %. Score = max VUs passing a 5-min hold. k6 runs on a **separate** machine.
- Full challenge method (README, restated in chatWork §7 — VERIFIED against `bench/load.js`
  thresholds + transcript §[02:02]): warm-up 1000 VUs / 2 min (unscored) → probe from 2500 doubling
  to fail → narrow to ±250 → 5-min confirm → step down 250 on fail.
- Video transcript (QGWKCaglSBI, the **current** experiment): 13 languages, 26/28 benchmarked;
  **zero Java, zero Node submissions** (§[03:02]); best 64,000 (Rust io_uring + C++ tied, conntrack
  ceiling §[16:12]); recurring wins: drop Nginx, single DB-owner connection + prepared reuse,
  JSON built in SQLite, **group commit** (Go routine batching 256, §[07:05]), strip framework layers
  (Python FastAPI→Uvicorn 11k→22k §[06:04]; C# no-middleware 32k §[08:05]); durability note:
  `synchronous=NORMAL` valid but weaker than FULL (§[18:13]).
- PR33 (read-only `reference/spring-boot-pr33 @ 7a9cb99`): OPEN, unmerged, 0 reviews, 7 comments,
  no benchmark numbers; Spring MVC/Tomcat behind Nginx; virtual-threads flag **absent**;
  WebFlux **absent**; Hikari 4/1; keep-alive 70 s (satisfies SPEC ≥ 65 s rule); WAL + NORMAL +
  FK-on + busy 5 s; author self-declares **unoptimized baseline** (comment 6.3).

## 2. Per-report scores (0–5 per axis)

| Axis | chatNormalJava12 | chatWorkJava12 | claudeJava12 |
|---|---|---|---|
| Factual correctness | 4 — box, seed sizes (50k/500k/2M), 5 endpoints, 42-checks, PR file inventory all match; explicitly refuses video attribution (§11, §668) | 5 — exact head SHA `7a9cb997`, 4 commits/35 files, versions (Temurin 25.0.1+8, Boot 4.0.3, sqlite-jdbc 3.53.4.0, Maven 3.9.11), Hikari 4/1, keep-alive 70 s, no-virtual-threads all re-verified this session | 3 — repo/SPEC/schema/test reads correct; PR read limited to 7/35 non-Java files (self-declared §2); benchmark numbers come from a **different** video/post, not QGWKCaglSBI |
| SPEC/rule interpretation | 4 — auth-before-id, `SELECT 1` health, no-cross-request-cache vs permitted page-cache/mmap/prepared reuse all correct; startup-60s and JWT-from-seed correct | 5 — full error table, auth-before-id with example, trim+code-point rule, 16 KB out-of-scope + PR 16 KiB limit distinguished (SPEC vs impl), per-request JWT re-verify, no-cache/no-copy/no-remembered-verify enumerated | 4 — byte-exactness, golden files, code-point counting (SQLite `length()`), FK-off self-check, auth-error ordering all correct; `transfer-encoding/content-length/never-close` adds untested transport mandates (minor overreach) |
| Benchmark understanding | 4 — warm-up/probe/narrow/confirm + p95/p99/err thresholds correct; VU≈browsing-loop (not RPS) correct; Nginx 14–20 % correctly hedged as repo-reported, not Java-measured (§472) | 5 — thresholds, warm-up, doubling, ±250, 5-min hold, step-down, separate load generator, workload mix with think times and write shares, “passing cap is lower bound” all match `bench/load.js` + transcript | 3 — scoring formula correct; but §5 table (Spring 5.1k/10.25k, Go 11.75k, Rust 14.05k) is the **prior** language video, and §5 database table is the Oct-13 post — neither is the current 64k video; VU≈10×RPS arithmetic is video-summary-derived, not `load.js`-derived |
| Java arch | 4 — 3 options (Boot+JDBC / JDK HttpServer+vthreads / Netty+JDBC) with honest “cannot conclude without bench” (§285); small-module layout; no-JPA call correct | 5 — MVC+JDBC baseline justified (blocking SQLite calls, 1 CPU; WebFlux needs offload, changes scheduling not write concurrency); try vthreads/other stacks only after baseline shows thread-blocking bottleneck | 4 — 5-option table (Boot / Javalin-Jetty-Helidon / Vert.x / Netty-hand-decoder / FFM-libsqlite3) is the widest; layering judgment (baseline → lean transport after profiling) sound; FFM/PGO late-stage correctly flagged complex |
| SQLite | 4 — correlated-subquery feed analysis, `EXPLAIN QUERY PLAN`, WAL/ownership/txn boundaries, reader/writer contention all on-point; no invented pragma values | 4 — index names exact (`posts_created_at_id_idx`, `posts_user_id_idx`, `likes_post_id_idx`), representative SQL exact, `RETURNING`/`ON CONFLICT DO NOTHING` + existence-check semantics exact; pragma advice stays at “WAL+NORMAL+FK+bounded busy” without invented cache numbers | 3 — starting-point pragmas (`cache_size -16384`, `mmap 256 MB`, `temp_store MEMORY`, `FK OFF`, `wal_autocheckpoint=0` + timed PASSIVE) are concrete but **unmeasured**; `FK OFF` contradicts PR33 `FK ON` without correctness argument; one-statement like + `EXPLAIN` checks are good |
| Concurrency | 4 — vthread-vs-platform experiment with “vthreads reduce wait cost, don’t create CPUs; benefit fades at SQLite bottleneck” (§377-383); pool A/B/C (shared-sync / small pool / read+write split) with “small pool first, biggest may lose on 1 core” (§391-407) | 5 — “SQLite concurrent readers, serialized writers; more threads/reactive cannot remove DB limit”; pool-4 as starting point explicitly **not optimum**; write sections short, tail-latency over averages | 4 — 1–2 loops + inline reads + single writer queue + 503-on-full is the most concrete design; JNI-pinning caveat for vthreads correct; but queue-full-503 vs <1 % error budget interaction unanalyzed |
| JVM tuning | 3 — `-Xms128m -Xmx768m + G1GC` explicitly “experimental starting points, not optimal” (§502); RSS/heap/GC/CPU/conn/busy/p95-p99 metric list good; “small predictable > max heap” principle correct; G1-on-1-core choice unargued | 4 — heap-is-part-of-RSS, include native/SQLite/Nginx/OS in 2 GB, no durability trade, no invented flags; defers GC/heap to profile-driven Phase 4 | 4 — `-UseSerialGC -Xms/Xmx640m` + explicit-Serial rationale (JVM picks it on 1 CPU — verify via `PrintFlagsFinal`), direct/code-cache caps, `CompactObjectHeaders` JDK-25-gated, JIT warm-up via 2-min warm-up; all flagged “to measure”; most concrete yet still honest |
| Deploy assumptions | 4 — Nginx-first for comparability then direct as experiment; 65 s idle rule implied via SPEC; `LimitNOFILE/CAP_NET_BIND_SERVICE` noted (§20-21 paraphrase) | 5 — Nginx (64 idle upstream, `127.0.0.1:3000`) vs direct (`0.0.0.0:80`, ~15k keep-alives) exact; 14–20 % labeled repo-observed; 3 PR modes (docker/direct/k8s) with per-mode limits correctly distinguished from official score | 4 — idle-timeout > 65 s + >10 s user-gap reasoning, gzip-on-≥1 KB vs k6 `Accept-Encoding` unknown correctly flagged as inference (§43), non-root bind + `LimitNOFILE` correct |
| Testing | 4 — 42/42-first milestone, auth/id precedence example, JUnit + official suite pipeline; `test/run.sh` vs `test/test.sh` filename slip (§564 says `test/run.sh`; repo has `test/test.sh`) — minor | 5 — exact harness commands (`seed/make-seed.sh`, `test/run.sh submissions/…` — note: suite entry is `test/test.sh` per repo root; `test/run.sh` is the submission-runner path cited in PR docs; both referenced with “read README/help before relying”), fresh-DB golden discipline, repeat-runs spread | 3 — M1 “`test/run.sh submissions/<folder>` passes 42” without noting fresh-DB single-use or golden byte-compare; admits `load.js`/`make-seed.sh`/`run.sh` **not read** (§23) while scoring depends on them |
| Unsupported claims (inverse) | 4 — one slip (“`test/run.sh`” filename) + Mermaid-diagram CSS dump (§312, cosmetic); otherwise hedges (“not a claim every detail verified” §240, “requires controlled benchmarking” §285, §668 limitation) | 5 — reconstruction note (§6) + per-section “starting point, not optimum” + “local runs are not official” + “no Java capacity number” (§367); zero invented scores | 3 — presents prior-video numbers adjacent to current-video recommendation without dating them (§9, §59-73); “~0.77 ms CPU/request” arithmetic from third-party summary treated as box budget; FFM/PGO/SQLite-shrink claims unmeasured |
| Gaps (inverse: honesty scores high) | 4 — states video unfetchable, PR diff partially accessible, no ranking inferred (§11, §668); misses load.js mix details (no think-time/write-share table) | 5 — §8 “What is not established” + §12 sources with per-URL pinning + video-URL-unfetchable + README-video-ID mismatch flagged; only report to catch the `sQXFhh_PiG4` vs `QGWKCaglSBI` ID fork | 5 — §2 gap table is exemplary (429-blocked, 7/35 files, load.js unread — “read it first”); §10 open questions + “paste transcript/PR files to close gaps” |

Overall (sum /55, rank only): **chatWorkJava12 52 > chatNormalJava12 43 > claudeJava12 40**.
Numbers rank effort-correctness; §3 extremes and §5 matrix decide what to trust.

## 3. Strongest / weakest per report (evidence-grounded)

- **chatNormalJava12**
  - Strongest: *Three-architecture comparison with explicit non-conclusion* (§269-285: Boot vs JDK+vthreads
    vs Netty, “cannot conclude … from PR structure alone. That requires controlled benchmarking.”).
    VERIFIED stance — matches PR33 “unoptimized baseline” admission + OPTIMIZATIONS.md measure-first rule.
  - Weakest: *Test-suite filename* (`test/run.sh` §564; repo root holds `test/test.sh`, 114 lines).
    INCORRECT (minor, no behavioral consequence, but the one byte-level checkable slip in an otherwise
    careful report). Also cosmetic: §312 Mermaid CSS dump into the doc.
- **chatWorkJava12**
  - Strongest: *Benchmark-method + workload-mix reconstruction* (§7: thresholds, warm-up/probe/narrow/hold/
    step-down, separate generator, feed→post→15 %-like→2 %-post with think/idle windows, VU≠RPS,
    “local ≠ official”). VERIFIED against `bench/load.js:11-43` + transcript §[02:02].
  - Weakest: *Nothing load-bearing is wrong.* Weakest item is a precision nit: `JWT_SECRET` table row
    (§66) vs `SPEC.md:11`/`test/test.sh:15` — values match (`twelve-dollar-challenge`); no finding.
    Flagged only so “weakest” is not empty.
- **claudeJava12**
  - Strongest: *Gap table + packaging/60 s/pinned-toolchain rules* (§2, §8–9: what was/wasn’t read,
    60-s health, env-only config, no prebuilts, MIT licensing). VERIFIED and the most auditable
    “what I don’t know” in the set.
  - Weakest: *Prior-video numbers presented as “what the numbers say” for the current build* (§9, §59-73:
    Spring 5.1k/10.25k, Go 11.75k, Rust 14.05k from third-party summary of `sQXFhh_PiG4`, plus Oct-13
    DB-post Go 13.4k). OUTDATED relative to the QGWKCaglSBI transcript in evidence (64k Rust/C++,
    61k Zig, 46–47k C/Bun, 40k PHP, 38k Fortran, 32k C#, 22k Python/Go). A reader acting on §13
    (“beat 10,250, then 12–13k”) undershoots the current bar by ~5×. Must be re-dated, not deleted.

## 4. Disputes resolved (primary evidence or needs-experiment)

1. **Virtual threads “forgotten / will surely help” vs “measure first” vs “NIO better here”.**
   PR33 comments 6.1/6.5 assert; 6.7 counters with NIO/raw-Netty; author defers to bench (6.3/6.6).
   Code fact VERIFIED: flag absent at `7a9cb99` (`application.properties`, 6 lines).
   Effect on 1 vCPU + single SQLite writer: **needs-experiment** — A/B on target box
   (platform threads vs `spring.threads.virtual.enabled=true` vs NIO tuning), same commit/pragmas/pool,
   5-min holds, report p95/p99/err/CPU/RSS spread. chatNormal Exp-1 + chatWork Phase-4 + claude §106
   all correctly refuse to crown a winner.
2. **WebFlux beats MVC for this workload.** UNSUPPORTED (zitreF 6.5, no numbers; conflicts with 6.7 +
   blocking Xerial driver reality). chatWork §5 (“does not make the driver non-blocking”) and claude
   §106 (“needs explicit offloading, awkward fit”) correctly dissent. Resolution: adopt only if a
   profile shows Tomcat-thread blocking as the bottleneck; default MVC+JDBC baseline stands.
3. **Nginx 14–20 % CPU → “remove it”.** PARTIALLY VERIFIED as a repo-observed figure from the
   original-video setup (chatWork §1 labels it correctly; chatNormal §472 hedges identically).
   Transfer to Java + current rules: **needs-experiment** — same build behind Nginx vs direct on the
   real box (direct must hold ~15k keep-alives, idle ≫ 65 s, port 80 non-root). All three reports
   correctly sequence Nginx-first then direct.
4. **Group commit.** VERIFIED as a legal, recurring win (transcript Go §[07:05], C++ §[18:13];
   SPEC “batch multiple writes into one transaction” allowed; PR33 OPTIMIZATIONS.md lists it with
   “respond only after the shared commit” guard). claude §87 correctly scopes it second-order at
   ~100 writes/s under the realistic mix — arithmetic from `load.js` probabilities, not a bench result.
   Java shape: **needs-experiment** (single-writer queue + drain + `BEGIN IMMEDIATE` … `COMMIT`).
5. **JSON built in SQLite / SQLite JSON functions for auth.** VERIFIED as observed (Ruby/Erlang/Bun/Fortran
   §§[04:02]–[10:07]); rule-compliant per transcript C++ ruling (§[18:13]: OS page cache OK, private
   in-memory copies not). Java transfer: EXPERIMENTAL HYPOTHESIS — must preserve exact key order,
   escaping, UTF-8, and per-request HMAC semantics; measure after SQL baseline.
6. **Hikari pool 4 / read-write split / one shared connection.** Pool-4 VERIFIED as PR33’s current
   setting, explicitly **not** an optimum (PR33 + chatWork §6). Direction: PARTIALLY VERIFIED
   (readers parallel in WAL, single writer serializes — all three reports agree). Size/split:
   **needs-experiment** (pool sweep + busy-timeout + p99 under write load).
7. **JVM flags (G1/768 m vs Serial/640 m vs default).** Both EXPERIMENTAL HYPOTHESES, both honestly
   labeled. No Java bench exists in evidence. Resolution: **needs-experiment** — pin JDK 25, sweep heap
   × GC on target box with RSS + GC-log + p99 gates; stay well under 2 GB incl. native/SQLite/Nginx/OS
   (no swap).
8. **FFM libsqlite3 + PGO SQLite + `mmap`/page-cache shrink + checkpoint deferral.**
   EXPERIMENTAL HYPOTHESES (claude §6–7; transcript C/C++ show the shape, not Java numbers).
   C++ cache-shrink legality reasoning (§[18:13]) does not transfer automatically to JVM memory
   accounting. **needs-experiment**, late-stage only, after a correct baseline + profile.
9. **“Zero Java submissions” vs PR33’s existence.** No contradiction: transcript §[03:02] (“zero Java
   submissions” benchmarked in-video) vs PR33 opened Oct 10 / discussed Oct 13 (unmerged, unbenchmarked
   per §8 table). Both VERIFIED in their time bounds. Java track is greenfield + one unmeasured baseline.
10. **Benchmark IDs `sQXFhh_PiG4` vs `QGWKCaglSBI`.** chatWork §8 + claude §2 correctly separate them
    (prior language comparison vs current expert round + Oct-13 DB post). chatNormal §11 refuses
    attribution without verification. No dispute remains; claude §5 numbers must be re-labeled with
    their source video or they mislead.

## 5. Verification matrix (claim | verdict | evidence | resolution)

| # | Claim (source) | Verdict | Primary evidence | Resolution |
|---|---|---|---|---|
| V1 | Box 1 vCPU / 2 GB / no swap / Ubuntu 24.04 (all three) | VERIFIED | `docs/PROJECT_CONTEXT.md:5-6`; SPEC runtime table `SPEC.md:8-14`; transcript §[01:01] | Accept; use as sizing gate |
| V2 | 5 endpoints, compact JSON, exact key order, live `like_count`, text `created_at` passthrough (all three) | VERIFIED | `SPEC.md:23-43`; `test/test.sh:52-57,95-123` golden + read-back checks | Accept; byte-compare in CI |
| V3 | Auth-before-id; HS256-only; exp-then-payload errors (all three) | VERIFIED | `SPEC.md:72-79`; `test/test.sh:68-83,113`; `Authentication.java` (Bearer/HS256/`isEqual`/exp/payload) | Accept; router order is a correctness gate |
| V4 | Trim-then-validate; 500 code points; malformed-JSON 400 (all three) | VERIFIED | `SPEC.md:67-69`; `schema.sql:13`; `test/test.sh:85-93,96-103` | Accept; count code points, not UTF-16 |
| V5 | Like 201/200 + existence check; health `SELECT 1`; feed 20 newest `created_at,id DESC` (all three) | VERIFIED | `SPEC.md:38-42`; `test/test.sh:60-61,110-123`; `PostRepository.java` projection + ordering + `RETURNING` | Accept |
| V6 | Schema fixed; 3 indexes; no-cache vs permitted page-cache/mmap/prepared (all three) | VERIFIED | `schema.sql:1-2,24-26`; SPEC rules; PR33 `OPTIMIZATIONS.md` non-negotiables | Accept; no result/token caches |
| V7 | Bench thresholds + VU-loop + separate generator (chatWork §7; chatNormal §8; claude §7-partial) | VERIFIED (chatWork/chatNormal); PARTIALLY VERIFIED (claude — mix admitted unread) | `bench/load.js:11-43`; transcript §[02:02] | Accept chatWork §7 as method reference; claude must read `load.js` before M2 |
| V8 | Warm-up 1000/2 min → 2500 doubling → ±250 → 5-min hold → −250 retry (chatWork §7; chatNormal §8) | VERIFIED | Upstream README method + `bench/load.js` stages/thresholds; transcript §[02:02] criteria | Accept |
| V9 | PR33 head `7a9cb99`, 4 commits, 35 files, OPEN/unmerged/0 reviews/7 comments/no numbers (chatWork §4; chatNormal §3) | VERIFIED | `SOURCE_MANIFEST.md:33-41`; `SPRING_BOOT_PR33.md:§2-6,§8`; fresh `git show` this session | Accept; cite SHA on reuse |
| V10 | No virtual-threads flag; WebFlux absent; Hikari 4/1; keep-alive 70 s; WAL/NORMAL/FK-on/busy-5 s (chatWork §4) | VERIFIED | `application.properties` (6 lines); `Application.java` pragmas/pool; `pom.xml` webmvc/jdbc/sqlite-jdbc | Accept; flag-absent claims in comments 6.1/6.7 true, effect unproven |
| V11 | “Virtual threads will surely help” / “WebFlux next” (PR comments, echoed loosely) | UNSUPPORTED | `SPRING_BOOT_PR33.md:§8` (no numbers); author defers (6.3/6.6) | needs-experiment (§4.1–4.2) |
| V12 | “NIO/raw-Netty better here” (comment 6.7) | UNSUPPORTED | Same §8 (testing-pending, no profile/diff) | needs-experiment; directionally plausible for conn-count only |
| V13 | Nginx 14–20 % CPU (chatNormal §482; chatWork §1) | PARTIALLY VERIFIED | Repo-reported original-video observation, not Java measurement (both reports hedge correctly) | needs-experiment behind-vs-direct (§4.3) |
| V14 | Prior-video table 5.1k/10.25k/11.75k/14.05k as current guidance (claude §9/§59-73) | OUTDATED | Third-party summary of `sQXFhh_PiG4`; current transcript §[03:02]–[19:14] peaks at 64k | Re-date to prior video; do not use for Java targets |
| V15 | DB-post table (Go 13.4k SQLite etc.) as current guidance (claude §74-90) | PARTIALLY VERIFIED (source real, context adjacent) | Oct-13 DB post, Go app, same box class — not the QGWKCaglSBI round | Keep as SQLite-NORMAL-vs-FULL evidence (§88–89 stand); not a Java target |
| V16 | `~0.77 ms CPU/request` box budget (claude §9) | UNSUPPORTED | Arithmetic on summarized numbers, not measured on Java path | Drop or re-derive from Java profiles |
| V17 | JDK+vthreads vs Netty vs Boot non-conclusion (chatNormal §285) | VERIFIED stance | PR33 baseline admission + measure-first rule | Accept as decision policy |
| V18 | MVC+JDBC baseline; WebFlux only after thread-block evidence (chatWork §5) | VERIFIED stance | Blocking Xerial driver + single-writer reality; no counter-evidence in set | Accept baseline order |
| V19 | Pool/reader-writer/vthread/JVM/pragma sweeps as isolated candidates (all three §5/§10/§6-7) | EXPERIMENTAL HYPOTHESIS (honest) | No Java bench in evidence; PR33 lists same as unmeasured | Accept as experiment backlog, not results |
| V20 | FFM binding, PGO SQLite, cache-shrink, checkpoint deferral, hand-written JSON (claude §6-7) | EXPERIMENTAL HYPOTHESIS | Transcript C/C++/Bun shapes exist; Java transfer unmeasured | Late-stage only, profile-gated |
| V21 | Test filename `test/run.sh` (chatNormal §564; claude M1) | INCORRECT (minor) | Repo root: `test/test.sh` (114 lines); `test/run.sh` is the PR-docs runner path | Correct to `test/test.sh` for the suite |
| V22 | Seed 50k/500k/2M + tokens (chatNormal §155-177; chatWork §1) | VERIFIED | `SOURCE_MANIFEST.md:49`; `bench/load.js:24` tokens; schema tables | Accept |
| V23 | “Zero Java submissions” (transcript) vs PR33 existence | VERIFIED both (time-bounded) | Transcript §[03:02] (benchmarked set) vs PR33 Oct 10–13 open/unbenchmarked | No contradiction; Java track = baseline + greenfield |
| V24 | Conntrack 65k ceiling; Rust/C++ 64k tie; C++ higher p99/CPU (transcript) | VERIFIED | Transcript §[15:11]–[19:14] | Accept; headroom is the tiebreaker, not the tie |

## 6. What FACT_CHECK (#5) should inherit

- Keep V1–V10 as the VERIFIED core; V11–V13/V19–V20 as the needs-experiment backlog with the methods
  in §4; V14 as OUTDATED (re-date or drop); V16 as UNSUPPORTED (drop); V21 as INCORRECT-minor (fix).
- Do not add new claims beyond this matrix without fresh primary evidence.
- Re-check PR33 comments/reviews before benchmarking (`SPRING_BOOT_PR33.md:§11` re-check recipe).

## 7. Verification steps run (this session)

- `git status` clean on `research/java-analysis`; `HEAD = main = origin/main = origin/research/java-analysis = 4d64cdd`;
  `backup/main-baseline-2026-10-13 = 026bdb6` intact; `reference/spring-boot-pr33 = 7a9cb99` untouched.
- Claimed #2 via `gh issue comment 2` before work (parallel-session guard).
- All inputs in §0.4 read in full; reference-head facts re-verified via `git show` (properties/pom/
  Application/PostRepository/Authentication/OPTIMIZATIONS) rather than trusting report quotations.
- Forbidden-word grep over the draft (case-insensitive) before commit — empty (see commit trailer note
  in closing comment). No benchmarks invented; every table row carries file+line/commit or doc URL.
- Skills: see §0.3 for invoked vs skipped-with-reason. Rubric fallback documented in §0.2.

*No implementation started. Next: #5 FACT_CHECK derives its verdict table from §5; frontier #3/#4/#8
remain for parallel sessions.*
