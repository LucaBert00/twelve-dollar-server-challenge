# Rule 5/6 code-read checklist v1 (#19, Gate D)

Build: `submissions/java-jdk-lucabert00` on `main` (commit recorded on #19).
Read against `challenge/` sources only. Signed per build; re-sign per variant.

## Rule 5 — no cross-request caching (README rules 5, OPT-34)

- [x] No response caches: handlers render per request via `Json` (`Main.handleFeed`,
      `handlePost`, `handleCreate`, `handleLike`); no stored responses.
- [x] No query-result caches: `Db.feed`, `getPost`, `createPost`, `likePost` prepare
      at checkout and close per use; no memoization.
- [x] No table copies: no in-memory collections of tables; `Db` holds pool + SQL
      constants only.
- [x] No remembered verifications: `Auth.authenticate` verifies signature + `exp`
      on every authed request (`Mac` per request, immutable key only).

Allowed reuse (plan/state, not data): prepared-statement plans, `Json` statics,
OS page cache. `ThreadLocal<Mac>` not used (per-request init, simplest correct).

## Rule 6 — durability (README rule 6, WAL + NORMAL floor, OPT-35/36)

- [x] WAL + `synchronous=NORMAL` + `foreign_keys=ON` + `busy_timeout=5000` on EVERY
      pooled conn (`Db.applyPragmas` per acquisition; `Db.init` asserts 4/4 at startup,
      fails fast; JUnit `DbTest.pragmas` re-asserts).
- [x] `synchronous` is 1 (NORMAL) or stronger; never OFF/DELETE/MEMORY/in-memory.
- [x] Commit-before-respond: writes run in auto-commit single statements
      (`CREATE_POST_SQL` via `RETURNING`, `LIKE_UPSERT_SQL`); no group commit here
      (DX-6 owns it); no respond-before-commit.
- [x] Schema diff empty: 3 tables + 3 indexes per `schema.sql` (plus SQLite internals
      `sqlite_stat1` from seed `ANALYZE` and `sqlite_autoindex_*`); app issues no
      DDL (JUnit `DbTest.healthAndSchema` checks names).

## JWT strict (OPT-39 prohibited downgrade)

- [x] HS256-only: `alg` must equal `HS256`; `none`/other algs rejected (incl. `crit`/`b64`).
- [x] `exp` enforced per request (numeric, future); malformed/expired → `invalid or expired token`.
- [x] Payload enforced: `sub` positive-integer string + `username` string, else
      `invalid token payload`. Three 401 shapes byte-exact per SPEC Errors.
- [x] Auth-before-id: authed routes verify before id/body checks
      (`POST /posts/abc/like` → 401 without token, 400 with token).

## Verdict

- [x] PASS — no prohibition violated; standing posture (pool 4, backlog 128,
      `-Xmx512m`, pragmas above) explicitly not optima; sizing belongs to later phases.

Signed: LucaBert00, 2026-10-15 (commit SHA on #19).
Method: minimal-diff senior-dev mode (smallest change serving later phases).
