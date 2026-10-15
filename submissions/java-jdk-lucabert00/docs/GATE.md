# Gate D — fresh-DB discipline (Phase 6, #19)

Standing pre-bench permission. Nothing benchmarks until green.

## Run book (per §3.1, copy-paste)

```bash
# 0. Fresh database per gate attempt (suite compares exact bytes).
# Rebuild only if needed (50k users / 500k posts / 2M likes):
bash seed/make-seed.sh
rm -f /tmp/bench.db && cp seed/feed.db /tmp/bench.db

# 1. Start THIS build on the fresh copy.
SQLITE_PATH=/tmp/bench.db JWT_SECRET=twelve-dollar-challenge HOST=127.0.0.1 PORT=3000 \
  bash submissions/java-jdk-lucabert00/start.sh &
SRV=$!; sleep 3

# 2. Gate (42 checks). Must print failed=0.
bash test/test.sh http://127.0.0.1:3000; echo "suite_exit=$?"

# 3. Record the gate line (suite SHA, DB md5, pass line). Required.
kill $SRV
```

CI discipline (`test/run.sh`) does build → fresh copy → start → suite → kill
in one command and is what CI runs:

```bash
bash test/run.sh submissions/java-jdk-lucabert00   # IS the verification
```

## JUnit harness

`src/test/java/challenge/` (`*Test`, AAA, `@Tag("gate")` vs `@Tag("bench")`):

| Class | Tag | Covers |
|---|---|---|
| `JsonTest` | gate | key order, escaping, wrappers |
| `AuthTest` | gate | auth matrix (`@ParameterizedTest`), `positiveInteger` id partition |
| `DbTest` | gate | goldens, pragmas, schema, id matrix (reflection), create/like/read-back, feed-20 |
| `HttpGateTest` | gate | HTTP black-box mirror of `test/test.sh` (golden, health/404/id, auth, create, like, feed) via spawned `challenge.Main` |
| `BenchSmokeTest` | bench | tag separation only (no benchmarking here) |
| `TestTokens` | helper | independent HS256 signer (not the verifier) |

```bash
mvn test                                  # all (gate + bench) green; gate tags green within
mvn -Dgroups=gate test                    # gate only
mvn -Dgroups=bench test                   # bench only
```

Report: `target/surefire-reports/` (XML + TXT per class).

## Records

- Suite file: `test/test.sh` (126 lines, gate at lines 125-126).
- Suite SHA: `sha256sum` `828c762be0032862b5763edd4bbfea04a5de86c92e090e34cd4863adf3cecf58`;
  git blob `381effb8a522989b4ba64a08471d9d08126e0152` (`git rev-parse HEAD:test/test.sh`).
- Runner: `test/run.sh` git blob `3d08303f5fa4925357cf32daf9e797d013236681`.
- Seed: `seed/feed.db` md5 `e1c4bcb0f8aad922ca8f9a49939cfb1b`
  (50000 users / 500000 posts / 2016005 likes).
- JUnit: `mvn test` → `Tests run: 75, Failures: 0, Errors: 0, Skipped: 0`
  (Auth 34, Db 11, HttpGate 25, Json 4, BenchSmoke 1); report `target/surefire-reports/`.
- Suite (Ubuntu/CI): `passed=42 failed=0` pasted verbatim on #19.
- Suite (Windows dev): `passed=41 failed=1` (single miss special-char via mingw
  `curl` argv code-page conversion; JUnit `HttpGateTest` same round-trip passes
  via explicit UTF-8, proving handler byte-exact; see Windows note below).

## Freshness proof

Second run on the SAME database fails golden checks by design (suite writes one
post + likes, so goldens no longer match). Paste the failure to prove the gate
is real; every gate attempt uses a fresh copy.

Measured 2026-10-15 (fresh copy md5 `e1c4bcb0f8aad922ca8f9a49939cfb1b`):

- First run (fresh DB): `passed=41 failed=1` (single miss special-char via
  Windows `curl` argv conversion; see Windows note).
- Second run (SAME DB): `passed=39 failed=3`:
  - `FAIL GET /feed == golden` (got posts 500002 + 500001 on top, not golden 500000...)
  - `FAIL GET /posts/1 == golden` (got `like_count:12`, want `11`)
  - `FAIL special characters round-trip` (same Windows quirk as first run)

Golden failures on re-run prove freshness discipline: stale DB never greens.

## Windows note (dev machine only)

`bash test/run.sh` on Windows Git Bash passes 41/42; the single miss is the
special-char round-trip because the mingw `curl` argv path converts UTF-8 to
the system code page before sending (server receives Latin-1 bytes and
faithfully stores them). The JUnit `HttpGateTest` sends explicit UTF-8 via
`HttpClient` and passes the same round-trip, proving handler byte-exactness.
Authoritative gate runs on Ubuntu (benchmark box / CI) where `curl` preserves
UTF-8 and the suite prints `passed=42 failed=0`.
