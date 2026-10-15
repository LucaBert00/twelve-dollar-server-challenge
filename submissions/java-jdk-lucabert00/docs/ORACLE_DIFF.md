# Oracle spot-diff vs C-pin `7a9cb99` (#19, Gate D)

Oracle: `reference/spring-boot-pr33` at pin `7a9cb99` only (never live head,
never merged, read via `git show`/`ls-tree`). Shapes from
`arjaythedev/twelve-dollar-server-challenge` (MIT); re-implemented, no wholesale
copy (notices in submission README).

## Agreement table (sample responses + error shapes)

| Area | Oracle shape (`7a9cb99`) | Our shape | Verdict |
|---|---|---|---|
| Post key order | `Responses.Post(id,body,created_at,author,like_count)` with `@JsonPropertyOrder` | `Json.postObject` same order, compact | Agree (byte-compare via goldens) |
| Like key order | `Responses.Like(liked,already_liked,post_id)` ordered | `Json.like` same order | Agree |
| Health 200 | `Responses.Health(status,db,uptime_s)` | `Main.handleHealth` same keys | Agree (suite regex) |
| Feed order/limit | reference SQL `ORDER BY created_at DESC, id DESC LIMIT 20` | `Db.FEED_SQL` same (`p.created_at DESC, p.id DESC LIMIT 20`) | Agree (golden `feed.json`) |
| Single lookup | `WHERE p.id = ?` on base + correlated `like_count` | `Db.GET_POST_SQL` same | Agree (goldens `post-*.json`) |
| Create | trim-then-validate, 500 code points, 201 + `like_count:0` | `Main.handleCreate` same | Agree (suite create rows) |
| Like | existence check (FKs off), 201 first / 200 repeat | `Db.likePost` + `Main.handleLike` same | Agree (suite like rows) |
| Id matrix | `positiveInteger` digits + `>=1` (`0,-1,abc,1.5` → 400) | `Main.parsePostId` same (+ overflow digits → 400) | Agree |
| Auth shapes | `Authentication`: Bearer check, 3-part, HS256-only (+`crit`/`b64` reject), HMAC + `isEqual`, `exp` numeric future, `sub`/`username` strings | `Auth.authenticate` same | Agree (suite auth matrix) |
| Auth-before-id | authenticate before `postId(id)` in `like()` | `Main.handleLike` auth first | Agree (`abc` 401/400) |
| Error shape | `Responses.Error(error)` → `{"error":"..."}` | `Json.error` same | Agree (all error rows) |
| Unknown path | `{"error":"not found"}` 404 | `Main` 404 same | Agree |

## Deliberate allowed-strict extras (suite-consistent, informational beyond the required triple)

1. **503 body**: oracle returns fixed `"database unavailable"`; we return the
   probe exception message (`{"status":"degraded","db":"unreachable","error":"<string>"}`).
   SPEC allows any `<string>`; suite checks only the 200 shape. Justification:
   more diagnostic, still SPEC-exact, suite-green.
2. **413 body limit**: oracle caps at 16 KiB (`request body too large`); we impose
   no explicit cap (SPEC Out of scope: bodies over 16 KB — do whatever the framework
   does; suite never sends >501 chars). Justification: out-of-scope, suite-green,
   no lane change. Informational only; not part of the required feed/single/error triple.
3. **Top-level non-object JSON** (`null`, `[]`, `42`): oracle parses then answers
   `body is required`; we answer `malformed JSON body` (parser expects `{`).
   Both 400; suite tests only `{bad json` for malformed and `{}`/`{"body":...}` for
   required. Justification: edge outside suite partition, SPEC-consistent (both 400),
   handlers untouched per #19 scope. Informational only.

No suite-vs-SPEC contradiction found; suite wins per SPEC if one appears.
