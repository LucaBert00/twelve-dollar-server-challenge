# Wayfinder Map Prompt — Base for Building the Initial Map (TASK 2)

> This file is the single base for building the initial GitHub Issues Wayfinder map.
> Do NOT create the entire map yet from this file alone unless explicitly told to.
> When told to build, follow this prompt exactly.
> Working repo: `LucaBert00/twelve-dollar-server-challenge` (fork of `arjaythedev/twelve-dollar-server-challenge`).

## 0. Do Wayfinder Issues comport file changes?

YES.

- Creating the Issue shells themselves (via `gh issue create`) produces NO file changes.
- Executing each Wayfinder Issue DOES produce file changes: each Issue owns one or more Markdown deliverables under `research/analysis/`, `research/findings/`, or `docs/`.
- Rule: one Issue = its file(s) = one commit = one push = then close. Never batch multiple Issues into one commit. Never close an Issue without its commit pushed to `origin`.

Expected mapping:

| Issue | File(s) produced |
|---|---|
| 1 REPORT_COMPARISON | `research/analysis/REPORT_COMPARISON.md` |
| 2 FACT_CHECK | `research/analysis/FACT_CHECK.md` |
| 3 VIDEO_ANALYSIS | `research/analysis/VIDEO_ANALYSIS.md` |
| 4 SPRING_BOOT_CODE_REVIEW | `research/analysis/SPRING_BOOT_CODE_REVIEW.md` |
| 5 GITHUB_RESEARCH | `research/findings/GITHUB_RESEARCH.md` |
| 6 OPTIMIZATION_CATALOG | `research/findings/OPTIMIZATION_CATALOG.md` |
| 7 JAVA_ARCHITECTURE_DECISION | `research/findings/JAVA_ARCHITECTURE_DECISION.md` |
| 8 BENCHMARK_STRATEGY | `research/findings/BENCHMARK_STRATEGY.md` |
| 9 FINAL_RESEARCH_REPORT | `research/findings/FINAL_RESEARCH_REPORT.md` |
| 10 IMPLEMENTATION_ROADMAP | `docs/IMPLEMENTATION_ROADMAP.md` |
| 0 Epic + 11 Verification | No new doc, only tracking + verification comments |

## 1. Git model — linear progress via main

Current state (2026-10-13):

- `main @ 026bdb6` was the frozen upstream baseline.
- Backup exists: `backup/main-baseline-2026-10-13` = exact pre-Wayfinder `main`. Do NOT delete it. It is the rollback point.
- Work happens on `research/java-analysis` (currently 1 commit ahead: `cc5bfb6`).
- `reference/spring-boot-pr33 @ 7a9cb99` is read-only. Never merge it anywhere.
- `origin` = fork (`LucaBert00`), `upstream` = original (`arjaythedev`). Never push to `upstream`.

Linear-view rule requested by owner:

1. All research work commits go to `research/java-analysis` first.
2. After each Issue's commit is pushed to `origin/research/java-analysis`, bring it to `main` linearly so progress is viewable as a straight line:
   ```powershell
   git checkout main
   git pull origin main
   git merge --ff-only research/java-analysis
   git push origin main
   ```
3. `main` must stay fast-forwardable. If `main` has diverged (non-ff error), STOP, report, do NOT force-push, do NOT create a merge commit without approval. The owner will decide between rebase vs merge.
4. The backup branch is never moved. If a rollback is needed: `git checkout main; git reset --hard backup/main-baseline-2026-10-13` only on explicit owner approval.
5. Remote tracking must stay clean: after each bring-to-main, `git fetch --prune` and verify `git log --oneline --graph --all -8` shows a straight line from `backup/main-baseline-2026-10-13` through `main`.

Why this deviates from `docs/PROJECT_CONTEXT.md`: that doc declared `main` frozen. The owner explicitly overrides it for linear visibility. The backup branch preserves the frozen baseline so nothing is lost.

## 2. Context the builder must load

Read completely before creating any Issue:

- `research/inputs/chatNormalJava12.md`
- `research/inputs/chatWorkJava12.md`
- `research/inputs/claudeJava12.md`
- `research/inputs/I-Asked-Expert-Programmers-to-Max-Out-a-12-Dollar-Server_QGWKCaglSBI_transcript.md`
- `research/sources/SOURCE_MANIFEST.md`
- `research/sources/SPRING_BOOT_PR33.md`
- `docs/PROJECT_CONTEXT.md`
- `SPEC.md`, `schema.sql`, `bench/load.js`, `test/test.sh`

Truth hierarchy: `SPEC.md` + `test/test.sh` (42 checks) > transcript > AI reports > PR discussion > external blogs.
Treat the three AI reports as fallible secondary sources. Agreement between models proves nothing.
Distinguish video results from later repo/community changes.
PR #33 (`https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33`): OPEN, unmerged, 0 formal reviews, 0 inline comments, 7 conversation comments, no maintainer comments, no benchmark numbers posted. It is a correctness baseline, not a benchmark result.

## 3. Mandatory skill discovery and utilization

Catalog paths `Documents\skills` and `.opencode\skills` are the SAME mirrored tree (`_pool` is the physical home). Inventory once, do not double-discover.

- Before any action, invoke relevant skills via the skill tool. If a skill applies, its use is mandatory.
- Use ALL genuinely useful skills across the run, including research, codebase exploration, code review, Java, architecture/domain modeling, performance/benchmarking, GitHub workflows, planning, and technical documentation.
- Follow each invoked skill's prescribed workflow exactly.
- Use specialist skills per phase, not one generic workflow for everything.
- Prefer direct repo inspection + `gh` CLI/API over screenshots. Use Playwright/browser automation only when browser inspection materially helps (GitHub timeline, docs, benchmark renderings).
- Never claim a skill was used unless it was actually invoked via the skill tool.

## 4. Global hard rules for ALL Wayfinder Issues

1. DO NOT write final application code. Research + architecture docs only.
2. DO NOT edit `research/inputs/*`. DO NOT merge `reference/spring-boot-pr33`. DO NOT touch `upstream`. DO NOT rewrite history / force-push.
3. DO NOT fabricate benchmarks. Classify every significant claim as VERIFIED / PARTIALLY VERIFIED / INCORRECT / UNSUPPORTED / OUTDATED / EXPERIMENTAL HYPOTHESIS with primary evidence (source file + commit/line, doc URL, or measured output). If evidence is insufficient, propose an experiment — do not invent a conclusion. Do not silently resolve contradictions.
4. FORBIDDEN WORD: never write the word p-o-n-y-t-a-i-l (any casing, any spacing/hyphenation) in any Issue title, body, comment, or commit message. Hard filter. If that concept is needed, write `minimal-diff senior-dev mode` instead. Before every push, run a case-insensitive grep for that word over changed files + Issue body draft; if hit, rewrite first.
5. SUGGESTED-SKILLS LIST: every Issue body MUST contain a `Suggested skills for this task:` line listing the skills below for that Issue. The agent executing the Issue MUST invoke them via the skill tool.
6. COMMIT + PUSH + CLOSE + BRING-TO-MAIN: when an Issue's doc(s) are complete and verified:
   a. `git add` ONLY that Issue's file(s).
   b. Commit: `docs(research): <short-title> closes #<N>`.
   c. `git push origin research/java-analysis`.
   d. Bring to main linearly per §1 (ff-only, push `main`).
   e. Post closing comment on the Issue with: commit SHA(s) on both branches, file list, verification steps run.
   f. `gh issue close <N>`.
   Keep research commits separate from future implementation commits.
7. Use `gh` for all GitHub ops. Verify each step (`gh issue view`, `gh issue list`, `git log --graph`).

## 5. Step 1 — Bootstrap labels + milestone (no doc output)

Create if missing (ignore already-exists errors):

- Labels: `wayfinder`, `epic`, `research`, `analysis`, `architecture`, `benchmark`, `roadmap`, `blocked-needs-evidence`
- Milestone: `Task-2-Research` (+14 days)
- Verify: `gh label list`, `gh milestone list`

Suggested skills for this task: `github, git-workflow`

## 6. Step 2 — Create Epic + 11 sub-issues

Create ONE epic tracking Issue, then Issues 1–11. Every sub-issue title MUST start with `[Wayfinder-X/11]`. Every body MUST contain: Objective, Inputs to read, Tasks checklist, Output file(s), Acceptance criteria, Suggested skills for this task, Wayfinder links (Epic # + Depends on #). Apply milestone `Task-2-Research` to all.

### Epic `[Wayfinder-0/11] TASK 2 Master Map — Java research to roadmap`
Checklist of Issues 1–11, branch map (§1), stop-gate (see §8). Labels: `epic, wayfinder`.
Suggested skills for this task: `github, git-workflow, writing-plans`

### Issue 1 `[Wayfinder-1/11] REPORT_COMPARISON — cross-validate 3 AI reports`
Objective: independently score each report on factual correctness, SPEC/rule interpretation, benchmark understanding, Java arch, SQLite, concurrency, JVM tuning, deploy assumptions, testing, unsupported claims, gaps. Strongest/weakest per report. Full verification matrix.
Output: `research/analysis/REPORT_COMPARISON.md`
Acceptance: every significant recommendation classified; disputes resolved with primary evidence or marked needs-experiment.
Suggested skills for this task: `research, deep-research, academic-deep-research, documentation-writer, verification-before-completion`

### Issue 2 `[Wayfinder-2/11] FACT_CHECK — claim verdict table`
Objective: condensed reusable verdict table derived from Issue 1.
Output: `research/analysis/FACT_CHECK.md`
Acceptance: table Claim | Verdict | Evidence | Resolution; no new claims beyond Issue 1 without evidence.
Suggested skills for this task: `research, documentation-and-adrs, verification-before-completion`

### Issue 3 `[Wayfinder-3/11] VIDEO_ANALYSIS — reconstruct original experiment`
Objective: from full transcript establish objectives, hardware (1 vCPU / 2 GB / no swap / Ubuntu 24.04), impls tested, methodology, observations, bottlenecks, attempted optimizations, acknowledged limits, Java-applicable lessons. Preserve timestamps. Separate video vs later repo changes.
Output: `research/analysis/VIDEO_ANALYSIS.md`
Acceptance: timestamped, transcript-vs-SPEC conflicts resolved in favor of SPEC/test.
Suggested skills for this task: `research, deep-research, documentation-writer`

### Issue 4 `[Wayfinder-4/11] SPRING_BOOT_CODE_REVIEW — PR33 deep dive`
Objective: inspect exact head `7a9cb99` under `submissions/java-spring-boot-nb-99/` (bootstrap, `application.properties`, controllers, auth, JDBC/SQL, error/serialization, threading, JVM/build/deploy/bench/tests/`OPTIMIZATIONS.md`) + all 7 conversation comments / 0 inline / 0 reviews. Confirm virtual-threads flag absent, WebFlux/NIO-Netty claims unproven, rule 5/6 notes. Confirmed problems vs speculation.
Output: `research/analysis/SPRING_BOOT_CODE_REVIEW.md`
Acceptance: cites file paths + comment URLs; no blind reproduction.
Suggested skills for this task: `acquire-codebase-knowledge, map-codebase, code-review, code-review-and-quality, java-springboot, java-docs`

### Issue 5 `[Wayfinder-5/11] GITHUB_RESEARCH — broad optimization discovery`
Objective: systematic GitHub-wide search Areas A–F: (A) other forks/submissions/benchmark experiments; (B) Netty/Vert.x/Undertow/Helidon/JDK HttpServer/virtual-threads/minimal servers; (C) SQLite WAL/concurrency/JDBC pooling/prepared-statement/busy_timeout/PRAGMA/mmap/query plans; (D) single-core JVM/GC/heap/JIT-warmup/native-mem/allocation/startup; (E) keep-alive/conn-limits/Nginx-vs-direct/parsing/backpressure/tail-latency/JSON/JWT/event-loop; (F) k6 design/coordinated-omission/warmup/repro/monitoring/profiling/latency-stats. Use code+repo search, issues/PRs/discussions, history, dorking. Inspect source beyond READMEs.
For each key finding record: repo URL, commit/version, file, technique, mechanism, evidence, applicability, disadvantages, rule compat, license, test/adopt/reject.
Acceptance: meaningful findings only, licenses checked, no unsafe/obsolete/undocumented tricks, no microbenchmark confusion.
Suggested skills for this task: `deep-research, research, agent-browser, browser-automation, documentation-writer`

### Issue 6 `[Wayfinder-6/11] OPTIMIZATION_CATALOG — decision database`
Objective: build table ID | Area | Technique | Source | Mechanism | Evidence | Trade-off | Rule compat (allowed/prohibited/uncertain) | Priority (high/med/low) | Validation (experiment required) | Status (candidate/rejected/validated/adopted). Split safe-baseline vs experimental vs high-risk vs prohibited.
Output: `research/findings/OPTIMIZATION_CATALOG.md`
Acceptance: highest-value experiments identified, not implement-everything.
Suggested skills for this task: `architecture-blueprint-generator, domain-modeling, documentation-and-adrs, verification-before-completion`

### Issue 7 `[Wayfinder-7/11] JAVA_ARCHITECTURE_DECISION — A vs B vs C`
Objective: compare min. Option A Plain Java (JDK HttpServer + virtual threads + direct JDBC), Option B Netty (event-loop + bounded DB executor), Option C Spring Boot (PR33 ref). Add others only on research evidence. Evaluate on SPEC compat, 1-vCPU, memory, HTTP concurrency, SQLite interaction, tail latency, complexity, observability, deploy, optimization potential, maintainability. Recommend baseline + comparative experiments where evidence insufficient.
Output: `research/findings/JAVA_ARCHITECTURE_DECISION.md`
Acceptance: explicit recommendation with why; principles: Java-first, SPEC-exact, durability preserved, no prohibited caching, minimal deps, measurable.
Suggested skills for this task: `architecture-blueprint-generator, domain-modeling, java-springboot, java-docs`

### Issue 8 `[Wayfinder-8/11] BENCHMARK_STRATEGY — reproducible method`
Objective: correctness-first (`test/test.sh`) then k6 limit-search, warmup, monitoring, profiling, latency distributions, stats reliability, bottleneck ID. Single-variable experiments.
Output: `research/findings/BENCHMARK_STRATEGY.md`
Acceptance: runnable by later agent without re-derivation; no invented targets.
Suggested skills for this task: `research, systematic-debugging, documentation-writer`

### Issue 9 `[Wayfinder-9/11] FINAL_RESEARCH_REPORT — synthesis`
Objective: synthesize Issues 1–8 into the main research reference. Depends on 1–8 closed.
Output: `research/findings/FINAL_RESEARCH_REPORT.md`
Acceptance: self-contained, exact refs, open questions listed.
Suggested skills for this task: `academic-deep-research, documentation-writer, verification-before-completion`

### Issue 10 `[Wayfinder-10/11] IMPLEMENTATION_ROADMAP — phased build plan`
Objective: 13 phases (init, HTTP baseline, SQLite, API, JWT, correctness, initial bench, SQLite exps, concurrency exps, HTTP/net exps, JVM tuning, final bench/deploy, docs/submission). Each: objectives, tasks, deps, acceptance, verification, risks, recommended skills. Measurable, no invented targets, no multi-variable optimization.
Output: `docs/IMPLEMENTATION_ROADMAP.md`
Acceptance: later implementation agent can execute without re-researching.
Suggested skills for this task: `writing-plans, architecture-blueprint-generator, java-junit, git-workflow`

### Issue 11 `[Wayfinder-11/11] Verification push + STOP gate`
Objective: verify all 10 files exist, links resolve, forbidden-word grep empty, `research/java-analysis` + `main` pushed and linear, all sub-issues closed. Post 10-point final response (report eval, top corrections, video+PR findings, GitHub discoveries, arch rec, top experiments, docs created, repo/branch status, open questions, first milestone). Then STOP — no implementation without owner approval.
Suggested skills for this task: `github, git-workflow, git-commit, verification-before-completion`

## 7. Step 3 — Link + verify map

- Edit epic body to list all Issue numbers. Add `Depends:` comments.
- `gh issue list --milestone Task-2-Research` must show Epic + 11.
- Order: 1–4 parallel after bootstrap; 5 after 4; 6 after 5; 7 after 1–6; 8 independent; 9 after 1–8; 10 after 9; 11 last.

## 8. Final response contract (for the run that executes the map)

Provide: (1) report evaluation, (2) most important corrections, (3) video + PR discussion findings, (4) most valuable GitHub discoveries, (5) recommended Java architecture, (6) highest-priority experiments, (7) documents created, (8) repo + branch status (both branches + SHAs + linear proof), (9) unresolved questions, (10) first implementation milestone. Then STOP.
