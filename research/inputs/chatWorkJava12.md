# Twelve Dollar Server Challenge: Java Implementation Report

**Prepared:** 13 October 2026  
**Purpose:** A practical blueprint for building a Java implementation of the challenge API.

> **Reconstruction note.** The referenced ChatGPT history available to this task contains only an opaque reference to the earlier report, not its text. This is a fresh, source-based reconstruction; it cannot preserve the earlier report's exact wording or recover examples that appeared only there. The supplied YouTube page could not be fetched in this session. Video-only claims are identified as unverified, and no Java benchmark score is invented. All content here is static.

## Executive summary

The challenge serves a small, exact JSON API backed by a fixed SQLite database on a DigitalOcean Basic VM with **1 vCPU, 2 GB RAM, and no swap**. The goal is to maximize simulated users while staying within fixed latency and error limits. Every request must read current data from SQLite; writes must be durable before success is returned.

The linked pull request, **#33**, is a Java reference implementation using **Java 25, Spring Boot 4, Spring MVC/Tomcat, Spring JDBC, Xerial SQLite JDBC, and HikariCP**. Its current head is <code>7a9cb997</code>; GitHub reported it open and unmerged when retrieved on 13 October 2026. It contains server code and benchmark/deployment tooling. Its documentation describes how to run measurements, but it does **not** publish a confirmed user-capacity score.

For a first version, use Spring MVC with direct JDBC and a small Hikari pool, keep the fixed schema, pass the full contract suite, and then tune one variable at a time on the challenge-sized machine. SQLite allows concurrent readers but serializes writes. More threads or a reactive framework cannot remove that database limit by itself.

## Contents

1. [Challenge and constraints](#1-challenge-and-constraints)
2. [API contract](#2-api-contract)
3. [Data model and query shape](#3-data-model-and-query-shape)
4. [What PR #33 implements](#4-what-pr-33-implements)
5. [Recommended Java architecture](#5-recommended-java-architecture)
6. [Code patterns](#6-code-patterns)
7. [Benchmark method and thresholds](#7-benchmark-method-and-thresholds)
8. [Assessment and risks](#8-assessment-and-risks)
9. [Build and delivery workflow](#9-build-and-delivery-workflow)
10. [Optimization roadmap](#10-optimization-roadmap)
11. [Architecture diagram](#11-architecture-diagram)
12. [Sources](#12-sources)

## 1. Challenge and constraints

The challenge is a stripped-down social feed API. All implementations use the same API contract, seed data, SQLite schema, and benchmark workload.

| Constraint | Requirement |
|---|---|
| Benchmark machine | Ubuntu 24.04 x86_64, DigitalOcean Basic droplet, 1 vCPU, 2 GB RAM, no swap |
| Database | SQLite file named by <code>SQLITE_PATH</code>, opened in-process |
| Schema | Fixed; do not add or change tables, columns, indexes, or triggers |
| Services | No PostgreSQL, Redis, Memcached, or other external service |
| Request caching | No response cache, result cache, in-memory table snapshot, or remembered JWT verification |
| Allowed reuse | SQLite page cache and mmap; prepared statements and query-plan reuse |
| Write semantics | A successful write response must follow a durable commit |
| SQLite durability | WAL mode and <code>synchronous=NORMAL</code> or stronger |
| Startup | Application must become healthy within 60 seconds |
| Packaging | Build from source on the target box; pin dependencies; submit source rather than prebuilt output |
| Concurrency | Any number of processes or threads, subject to the shared 1-vCPU/2-GB budget |

The seed builder creates **50,000 users, 500,000 posts, and 2,000,000 likes**, plus signed tokens for users. These sizes make feed reads and like counts meaningful without requiring a large database.

### Reverse proxy choice

Nginx is optional. Behind Nginx, the app listens on <code>127.0.0.1:3000</code>; the benchmark proxy keeps up to 64 idle upstream connections. Direct service listens on <code>0.0.0.0:80</code>, avoids the proxy's CPU cost, and must handle roughly **15,000** open keep-alive connections itself. The challenge README reports Nginx used about **14–20% CPU** in its original video experiment. That is a repository-reported observation, not a Java-specific measurement.

For a first Java submission, keep Nginx to make comparison with the challenge setup simpler. Direct serving is a separate benchmark candidate and has a larger connection-management burden.

## 2. API contract

Runtime configuration is provided through environment variables:

| Variable | Behind Nginx | Direct mode |
|---|---|---|
| <code>SQLITE_PATH</code> | Path to a fresh seeded database | Path to a fresh seeded database |
| <code>JWT_SECRET</code> | <code>twelve-dollar-challenge</code> on the benchmark box | Same |
| <code>HOST</code> | <code>127.0.0.1</code> | <code>0.0.0.0</code> |
| <code>PORT</code> | <code>3000</code> | <code>80</code> |

All responses use compact JSON and <code>Content-Type: application/json</code>. JSON key order is part of the expected contract.

| Request | Authentication | Success response |
|---|---|---|
| <code>GET /health</code> | None | <code>200 {"status":"ok","db":"ok","uptime_s":&lt;integer&gt;}</code> after <code>SELECT 1</code> succeeds |
| <code>GET /feed</code> | None | <code>200 {"posts":[...]}</code> containing the 20 newest posts |
| <code>GET /posts/:id</code> | None | <code>200 {"post":&lt;post&gt;}</code> |
| <code>POST /posts</code> | Bearer JWT | <code>201 {"post":&lt;post&gt;}</code> |
| <code>POST /posts/:id/like</code> | Bearer JWT | First like: 201; repeat: 200 |

A post object has this key order:

~~~json
{"id":17,"body":"Example","created_at":"2025-12-31T23:59:19.409Z","author":"alice","like_count":3}
~~~

<code>author</code> is the username stored for the post's author. A newly created post uses the JWT username. <code>like_count</code> must reflect the database at request time. Timestamps are UTC with millisecond precision and a trailing <code>Z</code>.

### Error contract

Every error body is compact JSON shaped as <code>{"error":"message"}</code>.

| Condition | Status | Error message |
|---|---:|---|
| Unknown path | 404 | <code>not found</code> |
| ID is not a positive integer | 400 | <code>invalid post id</code> |
| Post does not exist | 404 | <code>post not found</code> |
| Missing or non-Bearer authorization header | 401 | <code>missing bearer token</code> |
| Invalid, expired, malformed, or non-HS256 JWT | 401 | <code>invalid or expired token</code> |
| JWT subject or username has wrong type/value | 401 | <code>invalid token payload</code> |
| Invalid JSON | 400 | <code>malformed JSON body</code> |
| Missing, non-string, or whitespace-only <code>body</code> | 400 | <code>body is required</code> |
| Trimmed body exceeds 500 characters | 400 | <code>body must be at most 500 characters</code> |
| Other unexpected failure | 500 | <code>internal server error</code> |

Authenticated endpoints must authenticate **before** parsing the post ID. For example, <code>POST /posts/abc/like</code> without a token returns 401; with a valid token it returns 400.

The <code>POST /posts</code> body is trimmed before storage and validation. The maximum is 500 Unicode code points after trimming. The challenge specification describes bodies over 16 KB as out of scope; PR #33 additionally places a 16-KiB request limit in its implementation.

## 3. Data model and query shape

The fixed schema includes three tables:

| Table | Important columns | Constraints and purpose |
|---|---|---|
| <code>users</code> | <code>id</code>, <code>username</code>, <code>created_at</code> | Integer primary key; unique, non-null username |
| <code>posts</code> | <code>id</code>, <code>user_id</code>, <code>body</code>, <code>created_at</code> | User foreign key; body length 1–500; generated UTC timestamp |
| <code>likes</code> | <code>user_id</code>, <code>post_id</code>, <code>created_at</code> | Composite primary key <code>(user_id, post_id)</code> enforces one like per user/post |

The supplied indexes are:

| Index | Supports |
|---|---|
| <code>posts_created_at_id_idx</code> on <code>(created_at DESC, id DESC)</code> | Newest-first feed ordering |
| <code>posts_user_id_idx</code> on <code>posts(user_id)</code> | Post/user lookups |
| <code>likes_post_id_idx</code> on <code>likes(post_id)</code> | Per-post like counts |

A representative read joins a post to its author and counts likes with a correlated subquery:

~~~sql
SELECT p.id, p.body, p.created_at, u.username,
       (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count
FROM posts p
JOIN users u ON u.id = p.user_id
WHERE p.id = ?;
~~~

The feed uses the same projection, orders by <code>p.created_at DESC, p.id DESC</code>, and applies <code>LIMIT 20</code>. The <code>id</code> tiebreaker matters when timestamps match.

Post creation uses SQLite's generated ID and timestamp:

~~~sql
INSERT INTO posts (user_id, body)
VALUES (?, ?)
RETURNING id, created_at;
~~~

The like insert is idempotent through the existing composite primary key:

~~~sql
INSERT INTO likes (user_id, post_id)
VALUES (?, ?)
ON CONFLICT (user_id, post_id) DO NOTHING;
~~~

The application still checks that the post exists so it can distinguish a missing post from a repeated like. First insertion returns 201; conflict returns 200. Do not replace this with an in-memory counter or cached count.

## 4. What PR #33 implements

The PR is titled **“feat: add Java 25 Spring Boot 4 submission”**. Its retrieved metadata identifies four commits and 35 changed files. The current head SHA is <code>7a9cb997ac2d981c321899cb82bd5f8c1b7ca6c9</code>. GitHub reported it open and unmerged at retrieval time on 13 October 2026.

### Stack and versions

| Component | Version or choice |
|---|---|
| Java | Eclipse Temurin 25.0.1+8 |
| Spring Boot | 4.0.3 |
| HTTP | Spring MVC with embedded Tomcat |
| Database access | Spring JDBC <code>JdbcTemplate</code> |
| SQLite driver | Xerial <code>sqlite-jdbc</code> 3.53.4.0 |
| Connection pool | HikariCP; maximum 4, minimum idle 1 |
| Build | Maven 3.9.11 |
| Reverse proxy | Challenge Nginx configuration |

Spring Boot manages Tomcat, Jackson, HikariCP, JDBC, and test dependencies. The PR pins Java and Maven downloads and verifies their hashes.

### Application design

- <code>FeedController</code> maps the five endpoints and validates request bodies.
- <code>Authentication</code> parses every JWT, enforces HS256, compares the HMAC signature in constant time, checks expiration and claims, and returns a user identity.
- <code>PostRepository</code> performs parameterized SQL through <code>JdbcTemplate</code>.
- <code>Responses</code> contains response records with explicit JSON property order.
- <code>ErrorHandler</code> maps challenge exceptions and unknown paths to exact JSON errors.

The datasource configures WAL, <code>synchronous=NORMAL</code>, foreign keys, and a 5-second SQLite busy timeout. JDBC is in auto-commit mode. For post creation, the <code>RETURNING</code> result is closed before the response is sent so the statement's auto-commit can complete first.

The PR's <code>application.properties</code> sets host, port, and a 70-second Tomcat keep-alive timeout. It does **not** enable Spring's virtual-thread setting in the retrieved head. A PR commenter suggested <code>spring.threads.virtual.enabled=true</code>; the author replied that they intended to benchmark it. This is a candidate experiment, not a measured result.

### Tooling beyond the server

The PR adds a Makefile, Docker Compose and multi-stage Docker configuration, benchmark runner and report generator, plus direct-host and Kubernetes scripts.

| Mode | What it runs | Main limitation |
|---|---|---|
| <code>docker</code> | App and Nginx in one container limited to 1 vCPU / 2 GiB; k6 and tests outside that limit | Docker networking and host conditions can affect results |
| <code>direct</code> | JAR plus Nginx on the current machine; optional local k6 | Does not enforce the challenge CPU/memory limits |
| <code>k8s</code> | Nginx and app containers in one pod with shared limits; k6 on a different node | Needs a configured cluster, registry, and supported pod-level resources |

The added tooling records settings, latency, errors, and resource samples. It helps with repeatability, but a laptop, Mac Docker VM, or unrelated Kubernetes cluster does not produce the official challenge score.

## 5. Recommended Java architecture

Keep the request path straightforward:

1. Tomcat accepts HTTP/1.1 keep-alive traffic.
2. A controller validates route, authentication, and body in contract order.
3. Hikari provides a small pool of SQLite connections.
4. <code>JdbcTemplate</code> executes parameterized SQL against the database file.
5. Response records serialize to ordered JSON.
6. Nginx forwards requests when using the proxy configuration.

Start with Spring MVC and JDBC. The workload includes blocking SQLite calls, and the app has one CPU. WebFlux may change request scheduling, but it does not make the SQLite driver non-blocking or increase SQLite write concurrency. Try virtual threads or another HTTP stack only after a baseline shows request-thread blocking is the bottleneck.

A compact package layout:

~~~text
src/main/java/challenge/
  Application.java
  FeedController.java
  Authentication.java
  PostRepository.java
  Responses.java
  ApiException.java
  ErrorHandler.java
src/main/resources/application.properties
~~~

A large ORM layer is not necessary for this fixed, query-sensitive API. Direct SQL is easy to compare to the challenge queries and avoids hidden query changes.

## 6. Code patterns

These examples are newly written summaries of the design, not verbatim copies of the PR.

### Runtime properties

~~~properties
server.address=127.0.0.1
server.port=3000
server.tomcat.keep-alive-timeout=70s
spring.main.banner-mode=off
~~~

Use the environment for the database path and secret. Fail fast if required settings are missing or the database file does not exist.

### SQLite datasource

~~~java
SQLiteConfig sqlite = new SQLiteConfig();
sqlite.setJournalMode(SQLiteConfig.JournalMode.WAL);
sqlite.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
sqlite.enforceForeignKeys(true);
sqlite.setBusyTimeout(5_000);

SQLiteDataSource fileDataSource = new SQLiteDataSource(sqlite);
fileDataSource.setUrl("jdbc:sqlite:" + databasePath.toAbsolutePath());

HikariConfig pool = new HikariConfig();
pool.setDataSource(fileDataSource);
pool.setMaximumPoolSize(4);
pool.setMinimumIdle(1);
pool.setAutoCommit(true);

DataSource dataSource = new HikariDataSource(pool);
~~~

This is a starting point, not a proven optimum. Four connections can serve concurrent readers, but more connections do not add SQLite writers and carry memory cost. Keep WAL and durability settings within the rules.

### Repository query

~~~java
private static final String POST_PROJECTION = """
    SELECT p.id, p.body, p.created_at, u.username,
           (SELECT count(*) FROM likes l WHERE l.post_id = p.id) AS like_count
      FROM posts p
      JOIN users u ON u.id = p.user_id
    """;

List<Post> newestPosts() {
    return jdbc.query(
        POST_PROJECTION + " ORDER BY p.created_at DESC, p.id DESC LIMIT 20",
        postRowMapper);
}
~~~

Use parameter placeholders for user-controlled values. Reuse statements or prepared statements where useful, but never reuse query results across requests.

### JWT verification essentials

The expected token is a compact JWT signed with HS256. For every authenticated request:

1. Require an authorization value starting with <code>Bearer </code>.
2. Require exactly three non-empty token segments.
3. Base64URL-decode the header and require <code>alg</code> to be <code>HS256</code>.
4. Compute HMAC-SHA256 over the original <code>header.payload</code> bytes.
5. Compare signatures with <code>MessageDigest.isEqual</code>.
6. Decode and validate <code>exp</code>, <code>sub</code>, and <code>username</code>.
7. Reject expired tokens and malformed or incorrectly typed claims.

Use JCA <code>Mac</code> and the UTF-8 secret. Do not cache a previous verification result: the challenge requires signature verification on every request. Do not accept <code>alg=none</code>, other algorithms, invalid subjects, or expired tokens.

### Error handling and response shape

Use response records or DTOs with explicit property-order annotations. Keep one exception mapper responsible for exact challenge error bodies. Verify raw response bytes for all 42 challenge checks; semantically equivalent JSON with a different key order may still fail.

For request validation, follow the required order: authenticate first, then parse the route ID or body. Trim the body before checking emptiness and code-point length. Handle unknown routes separately from known route errors.

## 7. Benchmark method and thresholds

The official score is the **largest virtual-user count that passes a five-minute hold** while meeting all three limits:

| Metric | Pass threshold |
|---|---:|
| p95 response latency | < 500 ms |
| p99 response latency | < 1,000 ms |
| Failed-request rate | < 1% |

The repository's challenge process is:

1. Warm up with **1,000 virtual users for 2 minutes**; do not score this run.
2. Probe from **2,500 users**, doubling until a run fails.
3. Narrow the last-passing/first-failing interval to within **250 users**.
4. Confirm with a **5-minute hold** at the candidate count.
5. If confirmation fails, reduce by **250 users** and try again.

The benchmark runs k6 on a separate machine from the server. Every implementation in the original language comparison ran behind Nginx. The Java PR's helper can run local searches, but these results are not official unless the challenge maintainer runs the submission on the same VM and workload.

The k6 workload models browsing, not a fixed requests-per-second rate:

- <code>GET /feed</code>, then a randomly selected <code>GET /posts/:id</code>.
- Think times of about 3–7 seconds and 3–8 seconds between those reads.
- A like on roughly 15% of iterations.
- A post creation on roughly 2% of iterations.
- An idle interval of roughly 5–15 seconds before repeating.
- A 60-second ramp-up and 30-second ramp-down around each hold.

A virtual user is a simulated browsing loop, not one request and not a fixed RPS number. Report VUs, duration, latency percentiles, error rate, server CPU, and memory together. A passing cap is only a lower bound if the search stops at its configured maximum.

### Reproducible commands

The repository documents commands such as:

~~~bash
bash seed/make-seed.sh
bash test/run.sh submissions/java-spring-boot-nb-99
k6 run -e VUS=1000 bench/load.js
~~~

The PR adds commands such as:

~~~bash
make test
make benchmark
make benchmark MODE=direct BENCH_MAX_VUS=40000 BENCH_CONFIRM_RUNS=3
~~~

Read the PR's README and make help before relying on its optional arguments. Do not compare runs unless server limits, load-generator placement, data, warm-up, workload, and duration are controlled.

## 8. Assessment and risks

### Strong choices in the Java reference

- SQL is visible and close to the spec.
- The datasource uses permitted durability settings: WAL and <code>synchronous=NORMAL</code>.
- JWT verification is repeated per request as required.
- Response ordering is explicit.
- Tests cover malformed JWT/JSON, Unicode length, concurrent likes, timestamps, health failure, and database pragmas.
- Build and benchmark workflows are documented and distinguish test, benchmark, direct, Docker, and Kubernetes modes.

### What is not established

- The PR documents an experiment framework but gives **no Java capacity number** or official benchmark result.
- It was open and unmerged at retrieval time. The repository benchmarks merged submissions in batches.
- Virtual threads were suggested in discussion but were not enabled in the retrieved code; no comparison was available.
- The sources do not show that WebFlux outperforms Spring MVC for this SQLite workload.
- Docker, direct-host, and Kubernetes runs have different CPU, memory, networking, and load-generator conditions.
- The supplied YouTube URL could not be read here. The repository README links to another video ID for its original language comparison, so check the supplied video separately before attributing measurements to it.

### Practical cautions

1. **SQLite write serialization:** keep write sections short. More request threads can create a longer writer queue.
2. **Tail latency:** averages hide lock contention, checkpoint stalls, and pool waits; use p95 and p99.
3. **Memory budget:** JVM heap is only part of RSS. Include native JVM memory, SQLite caches, Nginx, and the OS in the 2-GB budget.
4. **Durability:** do not trade away required commit behavior to improve a benchmark.
5. **Exactness:** validation order, error status, JSON order, timestamps, and authentication are part of correctness.
6. **No benchmark shortcuts:** do not hard-code output, detect k6, cache results, or weaken verification.

## 9. Build and delivery workflow

The repository expects a folder like:

~~~text
submissions/java-spring-boot-yourname/
  install.sh
  build.sh
  start.sh
  README.md
  pom.xml
  src/
~~~

| File | Responsibility |
|---|---|
| <code>install.sh</code> | Install the pinned toolchain on clean Ubuntu 24.04 |
| <code>build.sh</code> | Compile and package source on the target host |
| <code>start.sh</code> | Start in the foreground using the challenge environment variables |
| <code>README.md</code> | State Java/framework/driver versions, proxy choice, settings, and measured optimizations |

CI runs installation and the challenge test runner. Follow the repository rule to add only your submission folder; do not modify the harness or shared schema. Pin Java and Maven downloads and verify checksums if the installer fetches archives.

A sensible personal workflow:

1. Implement the smallest complete server.
2. Run the exact harness against a fresh database copy.
3. Save a baseline commit and its environment/settings.
4. Benchmark on a target-size VM with k6 on a separate machine.
5. Change one tunable at a time and rerun correctness plus measurement.
6. Keep only repeatable improvements that preserve contract behavior.
7. Document that local runs are not official scores.

## 10. Optimization roadmap

### Phase 1 — Contract-first baseline

Implement all five routes and exact error behavior; use the fixed schema and challenge SQL; configure WAL, <code>synchronous=NORMAL</code>, foreign keys, and a bounded busy timeout; define ordered response DTOs; pass every supplied check before tuning.

### Phase 2 — Establish a baseline

Record commit SHA, JDK/vendor, Spring Boot, SQLite driver, JVM flags, Hikari pool size, Tomcat thread settings, Nginx mode, and SQLite pragmas. Record confirmed VUs, warm-up/probe/hold durations, p95/p99, error rate, CPU, and total memory. Repeat runs and report spread rather than selecting a lucky run.

### Phase 3 — Profile before tuning

Inspect CPU profiles, allocation/GC, Hikari wait time, SQLite lock/busy behavior, query plans, WAL size/checkpoints, and process RSS. Identify whether the bottleneck is HTTP handling, JSON/auth CPU, SQL reads, write serialization, pool wait, GC, or memory pressure.

### Phase 4 — Run isolated candidates

| Candidate | Measure | Trade-off to watch |
|---|---|---|
| Hikari pool size | Connection wait, lock waits, p95/p99, RSS | More connections do not add SQLite writers and use memory |
| Tomcat request-thread count | CPU, blocked time, throughput, tail latency | Excess concurrency can lengthen the SQLite queue |
| Virtual threads | Throughput, blocked time, memory, p99 | Changes scheduling, not SQLite's write model |
| SQLite page cache or <code>mmap_size</code> | Page faults, RSS, feed latency | Competes with JVM, Nginx, and OS memory |
| WAL checkpoint behavior | WAL growth and write tail latency | Delayed checkpoints can cause later spikes |
| Prepared-statement reuse | SQL preparation CPU and allocation | Statement ownership and lifetime must be safe |
| Reader/writer connection strategy | Busy errors, queue delay, read latency during writes | More scheduling complexity; writes still serialize |
| Group commit | Commit cost, queue delay, write p99 | Every response must wait for the shared durable commit |
| Like-check/insert SQL shape | SQL time and concurrent-like correctness | Preserve missing-post versus repeat-like semantics |
| JSON/auth allocation | CPU and allocation after SQL analysis | Keep exact validation and per-request verification |
| Direct serving | Whole-host CPU and 15k-connection behavior | Removes Nginx but transfers connection work to Java |

Do not add schema indexes or derived counts because the schema is fixed. Do not cache request results or token verification. Every candidate must pass correctness checks before its score is considered.

### Phase 5 — Freeze and submit

Keep the simplest repeatably fast implementation. Pin dependencies, document measurements and environment, verify startup within 60 seconds, and submit only the source folder. Wait for the maintainer's official benchmark batch before calling a result the challenge score.

## 11. Architecture diagram

~~~mermaid
graph TD
    Client["k6 virtual users on a separate load-generator machine"] -->|HTTP/1.1 keep-alive| Nginx["Nginx reverse proxy (optional)"]
    Nginx -->|HTTP to 127.0.0.1:3000| Tomcat["Spring MVC / embedded Tomcat"]
    Client -. "Direct mode: bind 0.0.0.0:80" .-> Tomcat
    Tomcat --> Controller["Controller and contract validation"]
    Controller --> Auth["Per-request HS256 JWT verification"]
    Controller --> Repo["Spring JDBC / JdbcTemplate repository"]
    Repo --> Pool["HikariCP small connection pool"]
    Pool --> Driver["Xerial SQLite JDBC"]
    Driver --> DB[("Seeded SQLite database file")]
    DB --- Schema["Fixed users, posts, likes tables and indexes"]
    Controller --> JSON["Ordered response records and JSON errors"]
    JSON --> Tomcat
    Tomcat --> Nginx
    Nginx --> Client
~~~

## 12. Sources

Primary sources used for this reconstruction:

- [Challenge repository and README](https://github.com/arjaythedev/twelve-dollar-server-challenge)
- [API specification](https://github.com/arjaythedev/twelve-dollar-server-challenge/blob/main/SPEC.md)
- [Fixed SQLite schema](https://github.com/arjaythedev/twelve-dollar-server-challenge/blob/main/schema.sql)
- [Load-test script](https://github.com/arjaythedev/twelve-dollar-server-challenge/blob/main/bench/load.js)
- [Nginx benchmark configuration](https://github.com/arjaythedev/twelve-dollar-server-challenge/blob/main/bench/nginx.conf)
- [Pull request #33 and its changes](https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33/files)
- [PR #33 Java README at retrieved head](https://github.com/arjaythedev/twelve-dollar-server-challenge/blob/7a9cb997ac2d981c321899cb82bd5f8c1b7ca6c9/submissions/java-spring-boot-nb-99/README.md)
- [PR #33 datasource configuration](https://github.com/arjaythedev/twelve-dollar-server-challenge/blob/7a9cb997ac2d981c321899cb82bd5f8c1b7ca6c9/submissions/java-spring-boot-nb-99/src/main/java/challenge/Application.java)
- [PR #33 SQL repository](https://github.com/arjaythedev/twelve-dollar-server-challenge/blob/7a9cb997ac2d981c321899cb82bd5f8c1b7ca6c9/submissions/java-spring-boot-nb-99/src/main/java/challenge/PostRepository.java)
- [PR #33 optimization notes](https://github.com/arjaythedev/twelve-dollar-server-challenge/blob/7a9cb997ac2d981c321899cb82bd5f8c1b7ca6c9/submissions/java-spring-boot-nb-99/OPTIMIZATIONS.md)
- [YouTube URL supplied in the request](https://www.youtube.com/watch?v=QGWKCaglSBI) — page not fetchable in this session.

