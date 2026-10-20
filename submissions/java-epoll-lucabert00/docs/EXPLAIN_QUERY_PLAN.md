# EXPLAIN QUERY PLAN - the epoll submission's own plans

Captured at commit `bcaff56` from the statements this submission actually prepares
(`Db.java`), against two databases: a fresh one built from `schema.sql`, and the
grown seed database.

Reproduce it yourself:

```bash
# Fresh database: 3 tables, 3 indexes, no rows.
sqlite3 /tmp/fresh.db < schema.sql

# Grown database: the seed, 50000 users / 500000 posts / 2016005 likes, ANALYZE run.
cp seed/feed.db /tmp/grown.db

# The pragma block the server applies, so the capture matches the server's own state.
for db in /tmp/fresh.db /tmp/grown.db; do
  sqlite3 "$db" "PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL; PRAGMA foreign_keys=ON;"
done
```

## The statements

The SPEC reference SQL is the **immutable fallback** (`SPEC.md:46-53`). This
submission uses it unchanged for the reads, and one guarded form for the like.

| Constant | Statement |
|---|---|
| `Db.SELECT_POST_BASE` | `SELECT p.id, p.body, p.created_at, u.username, (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count FROM posts p JOIN users u ON u.id = p.user_id` |
| `Db.FEED_SQL` | the base plus `ORDER BY p.created_at DESC, p.id DESC LIMIT 20` |
| `Db.GET_POST_SQL` | the base plus `WHERE p.id = ?` |
| `Db.CREATE_POST_SQL` | `INSERT INTO posts (user_id, body) VALUES (?, ?) RETURNING id, created_at` |
| `Db.LIKE_UPSERT_SQL` | `INSERT INTO likes (user_id, post_id) SELECT ?, ? WHERE EXISTS (SELECT 1 FROM posts WHERE id = ?) ON CONFLICT (user_id, post_id) DO NOTHING` |
| `Db.HEALTH_SQL` | `SELECT 1` |
| `Db.POST_EXISTS_SQL` | `SELECT count(*) FROM posts WHERE id = ?` |

## Fresh database (no rows)

### Feed - `GET /feed`

```
QUERY PLAN
|--SCAN p USING INDEX posts_created_at_id_idx
|--SEARCH u USING INTEGER PRIMARY KEY (rowid=?)
`--CORRELATED SCALAR SUBQUERY 1
   `--SEARCH l USING COVERING INDEX likes_post_id_idx (post_id=?)
```

Scan `posts` through the covering `posts_created_at_id_idx` in newest-first
order, point-lookup `users` by primary key, and count the likes per post
through the covering `likes_post_id_idx`.

### Single post - `GET /posts/:id`

```
QUERY PLAN
|--SEARCH p USING INTEGER PRIMARY KEY (rowid=?)
|--SEARCH u USING INTEGER PRIMARY KEY (rowid=?)
`--CORRELATED SCALAR SUBQUERY 1
   `--SEARCH l USING COVERING INDEX likes_post_id_idx (post_id=?)
```

### Post existence probe

```
QUERY PLAN
`--SEARCH posts USING INTEGER PRIMARY KEY (rowid=?)
```

### Liveness probe - `GET /health`

```
QUERY PLAN
`--SCAN CONSTANT ROW
```

### Like (shown with `= 1` for the post id)

```
QUERY PLAN
`--SEARCH posts EXISTS USING INTEGER PRIMARY KEY (rowid=?)
```

The insert is guarded by the post's existence, so the plan is the existence
lookup and nothing else. A first like runs **no second statement**.

## Grown seed database

50000 users, 500000 posts, 2016005 likes, `sqlite_stat1` present (the seed
runs `ANALYZE`).

### Feed

```
QUERY PLAN
|--SCAN p USING INDEX posts_created_at_id_idx
|--SEARCH u USING INTEGER PRIMARY KEY (rowid=?)
`--CORRELATED SCALAR SUBQUERY 1
   `--SEARCH l USING COVERING INDEX likes_post_id_idx (post_id=?)
```

### Single post

```
QUERY PLAN
|--SEARCH p USING INTEGER PRIMARY KEY (rowid=?)
|--SEARCH u USING INTEGER PRIMARY KEY (rowid=?)
`--CORRELATED SCALAR SUBQUERY 1
   `--SEARCH l USING COVERING INDEX likes_post_id_idx (post_id=?)
```

### Post existence probe

```
QUERY PLAN
`--SEARCH posts USING INTEGER PRIMARY KEY (rowid=?)
```

### Like

```
QUERY PLAN
`--SEARCH posts EXISTS USING INTEGER PRIMARY KEY (rowid=?)
```

**The plans are identical on the fresh and the grown database.** The indexes
cover every access path, so the feed's cost does not change with the data
volume. That is the property the reference SQL was chosen for.

## Notes for later work

- The reference SQL above is the immutable fallback for every later SQL
  experiment. A different shape has to reproduce the goldens byte for byte.
- `cache_size` and the other session pragmas are set per connection and
  asserted at startup, so no later variant may assume an inherited value.
- The feed is 46 % of all traffic and the heaviest query, and a competitor's
  JFR puts about 59 % of busy time inside SQLite `step` for this shape. If any
  query is ever rewritten, this is the one to start with.
- The `LIKE_UPSERT_SQL` plan shows only the existence lookup. The insert
  itself is covered by the `likes` primary key, so a conflict is a rowid
  lookup and a no-op.
