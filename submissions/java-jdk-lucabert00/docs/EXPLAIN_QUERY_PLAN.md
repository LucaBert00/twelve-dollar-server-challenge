# EXPLAIN QUERY PLAN baseline (Phase 3, DX-1 input)

Fresh database built from `schema.sql` (empty, 3 tables + 3 indexes, no data).
Pragma block applied before capture: `journal_mode=WAL`, `synchronous=NORMAL`,
`foreign_keys=ON`, `busy_timeout=5000` (same block the pool applies on every acquisition).

## Pragmas observed on the fresh file

```
PRAGMA journal_mode;  -> wal
PRAGMA synchronous;   -> 1 (1 = NORMAL)
PRAGMA foreign_keys;  -> 1 (1 = ON)
PRAGMA busy_timeout;  -> 5000
```

## Feed (20 newest, `ORDER BY created_at DESC, id DESC LIMIT 20`)

SQL:
```sql
SELECT p.id, p.body, p.created_at, u.username, (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count FROM posts p JOIN users u ON u.id = p.user_id ORDER BY p.created_at DESC, p.id DESC LIMIT 20
```

Plan on fresh (empty) database:
```
6|0|0|SCAN TABLE posts AS p USING INDEX posts_created_at_id_idx
9|0|0|SEARCH TABLE users AS u USING INTEGER PRIMARY KEY (rowid=?)
16|0|0|CORRELATED SCALAR SUBQUERY 1
21|16|0|SEARCH TABLE likes AS l USING COVERING INDEX likes_post_id_idx (post_id=?)
```

Reads as: scan `posts` via `posts_created_at_id_idx`, point lookup `users`
by primary key, correlated like-count via covering `likes_post_id_idx`.

## Single-post lookup (`WHERE p.id = ?`, shown with `= 1`)

```sql
SELECT p.id, p.body, p.created_at, u.username, (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count FROM posts p JOIN users u ON u.id = p.user_id WHERE p.id = 1
```

```
3|0|0|SEARCH TABLE posts AS p USING INTEGER PRIMARY KEY (rowid=?)
6|0|0|SEARCH TABLE users AS u USING INTEGER PRIMARY KEY (rowid=?)
13|0|0|CORRELATED SCALAR SUBQUERY 1
18|13|0|SEARCH TABLE likes AS l USING COVERING INDEX likes_post_id_idx (post_id=?)
```

## Like existence probe (`SELECT count(*) FROM posts WHERE id = ?`)

```sql
SELECT count(*) FROM posts WHERE id = 1
```

```
3|0|0|SEARCH TABLE posts USING INTEGER PRIMARY KEY (rowid=?)
```

## Write shapes (no query plan; verbatim SQL for later phases)

```sql
INSERT INTO posts (user_id, body) VALUES (?, ?) RETURNING id, created_at
INSERT INTO likes (user_id, post_id) VALUES (?, ?) ON CONFLICT (user_id, post_id) DO NOTHING
```

## Notes for later phases

- Reference SQL above is the immutable fallback for all later SQL experiments.
- `cache_size`-class settings drift per session like all session pragmas;
  every later variant re-asserts its pragmas per connection and never assumes
  inherited values. No `cache_size`/`mmap`/`checkpoint` knob changes in this ticket.
- Grown-data plans (with seed rows + `ANALYZE`) will be appended when the seed
  database is available; this file is the fresh-DB baseline for DX-1.
