# GATE - fresh-DB discipline and the JUnit harness

The entry ticket. Rule 4 is not a test preference: a submission that fails
one check of `test/test.sh` scores zero, and no amount of speed compensates.
Nothing benchmarks until this is green.

Build: `submissions/java-epoll-lucabert00` on `main`, commit `bcaff56`.

## Run book (copy-paste)

```bash
# 0. Fresh database per gate attempt. The suite compares exact bytes with
#    test/golden/*.json, so a stale database cannot green.
#    Rebuild only if needed (50k users / 500k posts / 2M likes):
bash seed/make-seed.sh
rm -f /tmp/bench.db && cp seed/feed.db /tmp/bench.db

# 1. Build, as a normal user. This also runs the JUnit suite: once against an
#    instrumented SQLite to collect the profile, then again against the
#    -fprofile-use library that ships. Needs seed/feed.db from step 0.
bash submissions/java-epoll-lucabert00/build.sh

# 2. Start THIS build on the fresh copy.
SQLITE_PATH=/tmp/bench.db JWT_SECRET=twelve-dollar-challenge HOST=127.0.0.1 PORT=3000 \
  bash submissions/java-epoll-lucabert00/start.sh > /tmp/server.log 2>&1 &
SRV=$!

# 3. Gate (42 checks). Must print failed=0.
for _ in $(seq 60); do curl -sf http://127.0.0.1:3000/health >/dev/null && break; sleep 1; done
bash test/test.sh http://127.0.0.1:3000; echo "suite_exit=$?"

# 4. Record the gate line (suite SHA, DB identity, pass line). Required.
kill $SRV
```

CI discipline (`test/run.sh`) does build -> fresh copy -> start -> suite ->
kill in one command, and it is what CI runs on every push:

```bash
bash test/run.sh submissions/java-epoll-lucabert00   # IS the verification
```

## The two-phase SQLite build

`build.sh` compiles SQLite 3.54.0 twice. The first compile is instrumented
(`-fprofile-generate=build/pgo -fprofile-update=atomic`) and is installed only
so the JUnit gate can train it; the second compile consumes that profile
(`-fprofile-use=build/pgo -Werror=missing-profile -Werror=coverage-mismatch`)
and is the library the server ships. The profile lives under the git-ignored
`build/`, is deleted before every generation, and a gate that leaves no
`.gcda` behind fails the build. A missing or incompatible profile is a hard
failure: there is no silent `-O2` fallback. `build/sqlite.stamp` records both
flag sets plus the `pgo=two-phase-gate` marker, so any flag change rebuilds.

The gate is the training workload, so the build needs `seed/feed.db` and
`test/golden/` - the same prerequisite as the gate itself (step 0 above).

## The JUnit harness

`src/test/java/challenge/`, Arrange-Act-Assert throughout, `@Tag("gate")`
against `@Tag("bench")`. The runner is the SHA256-pinned JUnit console
launcher that `build.sh` downloads; `build.sh` runs the gate-tagged suite, so
CI covers it without any file outside this folder changing.

| Class | Tag | Covers |
|---|---|---|
| `JsonTest` | gate | key order, escaping, the wrappers |
| `AuthTest` | gate | the auth matrix, the `positiveInteger` id partition |
| `DbTest` | gate | goldens, pragmas, schema, the id matrix, create/like/read-back, the feed of 20, the group commit, the batch failure, the commit observer, the like matrix |
| `HttpGateTest` | gate | the HTTP black-box mirror of `test/test.sh`, plus the descriptor release on peer FIN and the busy write lock |
| `ServerTest` | gate | the connection budget arithmetic and the wait timeout |
| `BenchSmokeTest` | bench | the tag separation only (no benchmarking here) |
| `TestTokens` | helper | an independent HS256 signer, not the verifier |

```bash
bash submissions/java-epoll-lucabert00/test.sh gate    # the correctness suite
bash submissions/java-epoll-lucabert00/test.sh bench   # the bench-only suite
bash submissions/java-epoll-lucabert00/test.sh all     # everything
```

The suite skips **loudly** when the seed database and the goldens are not
there: a skipped suite prints `test.sh: SKIPPED` and says why. A silently
skipped suite would read as a passing suite, and it must not.

## Records

- Suite file: `test/test.sh` (126 lines, the gate at lines 125-126).
- Runner: `test/run.sh`, the CI path.
- Seed: `seed/feed.db`, 249,192,448 bytes, 50000 users / 500000 posts /
  2016005 likes, with `sqlite_stat1` present.
- JUnit: `bash submissions/java-epoll-lucabert00/test.sh gate`.
- Suite (Ubuntu / CI): `passed=42 failed=0`.
- JUnit (Ubuntu / CI): 82 tests found, 82 successful, 0 failed, at commit
  `c8f897f`. The count moves as tests are added; the run log is the authority.

## Freshness proof

A second run against the **same** database fails the golden checks by
design: the suite writes one post and a few likes, so the goldens no longer
match. That failure is the proof that the gate is real - a stale database
never greens. Every gate attempt uses a fresh copy.

## What the gate proves, and what it does not

It proves the wire format: exact response bytes against
`test/golden/*.json`, the error matrix, the three 401 shapes, auth before id,
the trim and 500-code-point rules, the create/like/read-back round trip and the
feed of 20.

It does **not** prove performance. A green gate is permission to bench, not a
score. The benchmark evidence is `BENCHMARK_BENCH.md`, and it is produced by
the harness in `research/findings/BENCHMARK_STRATEGY.md`.
