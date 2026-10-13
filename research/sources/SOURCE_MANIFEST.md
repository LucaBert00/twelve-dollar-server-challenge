# Source Manifest

> Retrieval date: 2026-10-13 (UTC). Hashes are SHA-256 of the exact bytes imported into `research/inputs/`.

## 1. Upstream baseline

- Original repository: https://github.com/arjaythedev/twelve-dollar-server-challenge
- Description: "Implement one small API on SQLite in any language; we benchmark it on a $12 server (1 vCPU, 2 GB)"
- Default branch: `main`
- Baseline commit SHA (HEAD at fork time, `main` == `upstream/main`): `026bdb68d94d0d932bb5981321ad82c70618d752`
  - Subject: `Merge pull request #1 from cknutson12/python-fastapi-cknutson12`
  - Pushed: 2026-10-03T21:52:27Z
- License: MIT (`LICENSE`, `Copyright (c) 2026 arjaythedev and the challenge contributors`).
  Redistribution: MIT permits fork/redistribution with copyright + license preserved. Submissions are additionally licensed under the repo MIT by submitting (see upstream README "Submitting").
- Upstream structure at baseline: `.github/`, `bench/`, `seed/`, `submissions/`, `test/`, `.gitignore`, `LICENSE`, `README.md`, `SPEC.md`, `schema.sql`.
- Key upstream docs: `SPEC.md` (5 endpoints, runtime contract, byte-exact JSON, error table, auth, rules 1–11), `README.md` (scoring: warm-up 1000 users/2 min, doubling limit search from 2500, 5-min hold, p95<500 ms, p99<1 s, <1% errors), `bench/load.js`, `bench/nginx.conf`, `test/test.sh` (42 checks), `schema.sql`.

## 2. Fork

- Fork URL: https://github.com/LucaBert00/twelve-dollar-server-challenge
- Fork account: `LucaBert00` (verified via `gh api user`)
- Created: 2026-10-13 via `gh repo fork arjaythedev/twelve-dollar-server-challenge --clone=false`
- Local path: `C:\Users\matte\Documents\LUCA\projects\twelve-dollar-server-challenge`
- Remotes:
  - `origin`: `https://github.com/LucaBert00/twelve-dollar-server-challenge.git` (fetch+push) — verified
  - `upstream`: `https://github.com/arjaythedev/twelve-dollar-server-challenge.git` (fetch+push URL set; push never used) — verified, `upstream/main` == baseline SHA above
- Branches at setup completion:
  - `main` @ `026bdb6` (preserved upstream baseline)
  - `reference/spring-boot-pr33` @ `7a9cb997ac2d981c321899cb82bd5f8c1b7ca6c9` (exact PR head, read-only)
  - `research/java-analysis` (this work; based on `main`)
  - `feature/java-implementation` intentionally **not created** (Task 2+).

## 3. PR #33 inspected revision

- PR URL: https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33
- Title: `feat: add Java 25 Spring Boot 4 submission`
- Source repo/branch: `nb-99/twelve-dollar-server-challenge@feat/java25-spring-boot4` (separate fork)
- Exact inspected head SHA: `7a9cb997ac2d981c321899cb82bd5f8c1b7ca6c9` (2026-10-12T10:27:46Z)
- Full commit list: `fa5a0a2e`, `83d35511`, `2879838a`, `7a9cb997` (see `research/sources/SPRING_BOOT_PR33.md`)
- Merge status at inspection: OPEN, MERGEABLE, unmerged, 0 formal reviews, 0 inline comments, 7 issue comments, no maintainer comments, no benchmark numbers posted.
- Full discussion record: `research/sources/SPRING_BOOT_PR33.md`.

## 4. Video + transcript

- Original video URL: https://www.youtube.com/watch?v=QGWKCaglSBI
- Title: "I Asked Expert Programmers to Max Out a $12 Server" · Channel: Arjay McCandless · Duration ~22:55 (1376 s)
- Related earlier video (benchmark context, from upstream README): https://www.youtube.com/watch?v=sQXFhh_PiG4
- Transcript filename (original name preserved): `I-Asked-Expert-Programmers-to-Max-Out-a-12-Dollar-Server_QGWKCaglSBI_transcript.md`
  - Scope: auto-generated English captions, 815 cues, via youtube-transcript-api; header notes screenshots in local Downloads (not imported).
  - Redistribution note: auto-caption text of a public YouTube video, imported as private research input with source attribution. Not republished beyond this research fork. If the author requests removal, drop `research/inputs/I-Asked-*.md` from history per policy.

## 5. Imported research inputs (immutable)

| File in `research/inputs/` | Bytes | SHA-256 | Source |
|----------------------------|-------|---------|--------|
| `chatNormalJava12.md` | 36189 | `63D2BCB4EE4D2CD2EEBD23B7FE9182768F7CA76C8127178FCC41CE729BB00460` | Local research dir `C:\Users\matte\Documents\LUCA\projects\` (pre-existing, 2026-10-13 21:34) |
| `chatWorkJava12.md` | 27899 | `6D72AC30290556FCFAE79CE30E22BFED3E2A42DFCFE75AAED13873D8C54C0741` | Same dir (21:32) |
| `claudeJava12.md` | 19945 | `4D55B78345E05C93DBA1C32494AD358A33237141E6B6AE5BE8136A02161EA476` | Same dir (21:25) |
| `I-Asked-Expert-Programmers-to-Max-Out-a-12-Dollar-Server_QGWKCaglSBI_transcript.md` | 65787 | `9829F2850EA4BEA0D559C34B6011EDD9D4B2BCEF1B857B12971B55D159028A68` | Same dir (21:59) |

- Encoding: UTF-8 readable, no BOM (first bytes are `#`, ` ` ASCII); verified via `ReadAllText` char counts.
- Integrity: post-copy hashes match pre-copy hashes exactly (verified 2026-10-13).
- Originals: left intact at `C:\Users\matte\Documents\LUCA\projects\*.md`; imports are copies.
- Mutability: inputs are frozen. Do not rewrite/correct/merge in Task 1. Analysis belongs in `research/analysis/` + `research/findings/` (Task 2).
- Secrets/PII screen (2026-10-13): pattern scan for keys/passwords/tokens/private keys found only the public challenge constant `JWT_SECRET=twelve-dollar-challenge` (also in public SPEC.md) and generic JWT discussion — no private credentials. Email/phone regex found only example payloads (`alice`), dates, and counts — no personal data. Safe to commit to the public fork. The public JWT secret is not a leak; it is the documented benchmark value.

## 6. External documentation referenced

- Upstream `SPEC.md` (raw): https://raw.githubusercontent.com/arjaythedev/twelve-dollar-server-challenge/main/SPEC.md
- Upstream `README.md`: https://github.com/arjaythedev/twelve-dollar-server-challenge (rules, submitting, scoring)
- PR #33 files tab: https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33/files
- SQLite docs (for Task 2): WAL mode, `synchronous=NORMAL`, `mmap`, prepared statements — to be pinned then.

## 7. Availability limitations

- PR #33 discussion captured via `gh` API (authoritative for comments/reviews at inspection time). GitHub web-only timeline decorations (force-push events, CI badges, cross-links) were not scraped; Playwright was not needed because the API returned complete bodies for all 7 comments and definitive empty sets for reviews/inline comments.
- No benchmark numbers exist in the PR discussion to capture — absence is a finding, not a gap.
- Transcript is auto-generated captions (errors expected; header says check video audio for exact wording). Screenshots referenced by the transcript header (`twelve-dollar-server-video_screenshots/` in Downloads) were not imported in Task 1.
- Local toolchain: Java 8 (`1.8.0_401`) present but unsuitable for Java 25 work; Maven absent. No installs performed in Task 1. Task 2 must provision JDK 25 + Maven 3.9.11+ (pinned per PR reference) in an isolated location.
- Fork visibility: forks of a public repo are public; a private fork was not an option, and no secrets required one. If future inputs contain secrets, they go in a local-only dir with `.gitignore`, never in the public fork.
