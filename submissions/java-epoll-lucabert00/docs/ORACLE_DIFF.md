# ORACLE DIFF - against the C-pin `7a9cb99`

Oracle: `reference/spring-boot-pr33` at the pin `7a9cb99`, read only through
`git show` and `git ls-tree`. Never live head, never checked out, never merged.
Shapes from `arjaythedev/twelve-dollar-server-challenge` (MIT); re-implemented,
no wholesale copy.

The oracle's response shapes are the records in
`submissions/java-spring-boot-nb-99/src/main/java/challenge/Responses.java` at
that pin:

```java
@JsonPropertyOrder({"id", "body", "created_at", "author", "like_count"})
record Post(long id, String body, String created_at, String author, long like_count) {}

@JsonPropertyOrder({"liked", "already_liked", "post_id"})
record Like(boolean liked, boolean already_liked, long post_id) {}

@JsonPropertyOrder({"status", "db", "uptime_s"})
record Health(String status, String db, long uptime_s) {}

@JsonPropertyOrder({"status", "db", "error"})
record DegradedHealth(String status, String db, String error) {}

record Error(String error) {}
```

## Agreement table

| Area | Oracle shape at `7a9cb99` | This submission | Verdict |
|---|---|---|---|
| Post key order | `Post` with `@JsonPropertyOrder` | `Json.postObject` writes `id, body, created_at, author, like_count` in that order | Agree - byte-compare via the goldens |
| Like key order | `Like` ordered `liked, already_liked, post_id` | `Json.like` the same three keys in that order | Agree |
| Health 200 | `Health(status, db, uptime_s)` | `Json.health` the same three keys | Agree - the suite regex |
| Degraded 503 | `DegradedHealth(status, db, error)` | `Json.degraded` the same three keys | Agree - the probe message is the `<string>` SPEC allows |
| Error shape | `Error(error)` -> `{"error":"..."}` | `Json.error` the same | Agree - every error row |
| Feed order and limit | the reference SQL, `ORDER BY created_at DESC, id DESC LIMIT 20` | `Db.FEED_SQL`, the same reference SQL unchanged | Agree - `test/golden/feed.json` |
| Single lookup | `WHERE p.id = ?` on the base plus the correlated `like_count` | `Db.GET_POST_SQL`, the same | Agree - `test/golden/post-*.json` |
| Create | trim, then validate, 500 code points, `201` with `like_count: 0` | `Http.prepareWrite` then `Http.executeWrite`, the same order | Agree - the suite's create rows |
| Like | an existence check, `201` first and `200` on a repeat | `Db.insertLikeInTxn`, one guarded statement, the same three outcomes | Agree - the suite's like rows |
| Id matrix | digits only and `>= 1`, so `0 -1 abc 1.5` are `400` | `Http.parsePostId`, the same partition, and an overflowing digit string is `400` too | Agree |
| Auth shapes | Bearer check, three parts, HS256 only, HMAC compared in constant time, `exp` numeric and in the future, `sub` and `username` strings | `Jwt.verify`, the same checks, plus `crit` and `b64` rejection | Agree - the suite's auth matrix |
| Auth before id | authenticate, then the id | `Http.prepareWrite` authenticates, then validates the id | Agree - `abc` answers `401` and `400` |
| Unknown path | `{"error":"not found"}` with `404` | `Json.error(ERR_NOT_FOUND)`, the same bytes | Agree |
| Trim semantics | the framework's trim | `Http.validatedBody` uses a JS `trim()`-equivalent over the reference whitespace set | Agree - see the note below |

## Deliberate allowed-strict extras

Each is SPEC-consistent and suite-green, and each is informational beyond the
required feed / single / error triple.

1. **The 503 body carries the probe's message.** The oracle returns a fixed
   `"database unavailable"`; this submission returns the exception text, as
   `{"status":"degraded","db":"unreachable","error":"<string>"}`. SPEC allows
   any `<string>` and the suite checks only the `200` shape. More diagnostic,
   still SPEC-exact.

2. **Bodies over 16 KB are rejected.** The oracle caps the body at 16 KiB;
   this submission rejects a body over the guard with `400` and the SPEC's
   `body must be at most 500 characters`. SPEC puts bodies over 16 KB out of
   scope, so neither shape is tested.

3. **A top-level non-object JSON body answers `malformed JSON body`.** The
   oracle parses it and answers `body is required`. Both are `400`, and the
   suite sends only `{bad json` for the malformed case and `{}` or
   `{"body":...}` for the required case. The edge is outside the suite
   partition.

4. **A like that finds no post answers `post not found`.** The oracle checks
   the post's existence first and this submission folds the check into the
   insert's `WHERE EXISTS` guard, then probes once more only when no row was
   inserted. The three outcomes - `201`, `200`, `404` - are the same bytes.

## No suite-versus-SPEC contradiction

None was found. Where the suite and the SPEC could be read differently, the
suite wins, as `SPEC.md:3-4` says.
