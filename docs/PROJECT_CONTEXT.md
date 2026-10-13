# Project Context — Twelve Dollar Server Challenge (Java track)

## What the challenge is

One $12/month server (1 vCPU, 2 GB RAM, Ubuntu 24.04, no swap) serves a small
feed API backed by SQLite. Five endpoints (`GET /health`, `GET /feed`,
`GET /posts/:id`, `POST /posts`, `POST /posts/:id/like`), byte-exact compact
JSON, strict error table, HS256 JWT auth verified per request. The schema and
data are fixed (`schema.sql`, 50k users / 500k posts / 2M likes seed). Every
request must read from SQLite (no response/query caches, no table copies, no
remembered verifications); writes must be durably committed before responding
(WAL + `synchronous=NORMAL` or stronger). Score = max users passing a 5-minute
k6 hold with p95 < 500 ms, p99 < 1 s, < 1% errors. Nginx in front
(`127.0.0.1:3000`) is optional; going direct (`0.0.0.0:80`) saves Nginx CPU but
requires handling ~15,000 keep-alive connections. Source of truth: upstream
`SPEC.md`; on SPEC-vs-test disagreement the test suite (`test/test.sh`,
42 checks) wins.

Upstream: https://github.com/arjaythedev/twelve-dollar-server-challenge
Baseline: `main @ 026bdb6` (2026-10-03). License: MIT.

## Why this fork exists

To develop an **independent Java implementation** on a clean, reproducible base
that preserves upstream history and attribution, keeps research separate from
code, and never touches the upstream repo. Fork:
https://github.com/LucaBert00/twelve-dollar-server-challenge
(`origin` = fork, `upstream` = arjaythedev). `main` is the frozen baseline.

## Our intention

Build a Java entry from first principles (JDK + SQLite driver + minimal HTTP
layer), informed by — but not copied from — the Spring Boot community
submission. No implementation code is written in Task 1. The future build lives
outside `research/` and `docs/` (exact path decided in Task 2), with its own
`install.sh` / `build.sh` / `start.sh` / `README.md` per challenge rules.

## Role of the three AI reports

`research/inputs/chatNormalJava12.md`, `chatWorkJava12.md`, `claudeJava12.md`
are frozen, immutable inputs: three independent technical briefs on how a Java
entry could meet SPEC (runtime contract, JSON exactness, auth order, SQLite
discipline, JVM/threading/pool options, submission hygiene). Task 1 does not
judge, merge, or correct them. Task 2 cross-analyzes them against SPEC, the
test suite, and the transcript, then derives one buildable plan. Provenance and
hashes: `research/sources/SOURCE_MANIFEST.md`.

## Role of the video transcript

`research/inputs/I-Asked-Expert-Programmers-to-Max-Out-a-12-Dollar-Server_QGWKCaglSBI_transcript.md`
is the auto-caption text of https://www.youtube.com/watch?v=QGWKCaglSBI
("I Asked Expert Programmers to Max Out a $12 Server"). It captures the
challenge framing, rules rationale, and benchmark context in the author's own
words. Captions contain errors; audio is authoritative. It is research evidence,
not a spec. SPEC + `test/test.sh` override any transcript paraphrase.

## Role of the Spring Boot reference branch

`reference/spring-boot-pr33` (@ `7a9cb99`, from
`nb-99/twelve-dollar-server-challenge@feat/java25-spring-boot4`, PR #33, OPEN,
unmerged, unreviewed) is a **read-only reference**: a correctness baseline
(Java 25, Spring Boot 4.0.3, spring-mvc/Tomcat behind Nginx, Xerial SQLite
JDBC) plus a catalog of unmeasured optimization candidates (virtual threads,
WebFlux, NIO/Netty, pool/thread/SQLite/JVM tuning). Its 7 discussion comments
are preserved in `research/sources/SPRING_BOOT_PR33.md`. It is not an accepted
implementation, not a benchmark result, and must never be merged into research
or implementation branches. Inspect and test it via a separate worktree, never
in the working tree.

## Research vs implementation vs benchmarking

- `research/`: inputs (frozen), sources (provenance), analysis + findings
  (Task 2+). No build artifacts, no server code.
- Implementation (Task 2+): new submission folder + scripts, built from source
  on the box, pinned versions, SPEC-byte-exact, rule-5/6 clean.
- Benchmarking (later): upstream `test/test.sh` first, then `bench/load.js`
  k6 limit-search on equivalent hardware. No score is claimed from discussion
  opinions; only measured holds count.

## Non-goals for Task 1

No cross-report analysis, no Java code, no dependency installs, no PR against
upstream, no history rewrites, no force pushes.
