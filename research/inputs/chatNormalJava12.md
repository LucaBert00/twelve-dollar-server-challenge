# Technical Research Report: Building a Java Version of the $12 Server Challenge

Project: Twelve Dollar Server Challenge Target language: Java Objective: Build a highly optimized HTTP API capable of supporting as many concurrent users as possible on an inexpensive, resource-constrained server.

The interesting part of this project isn't simply implementing a REST API in Java. It's discovering how much performance you can extract from the JVM, SQLite, HTTP networking, and application architecture when hardware resources are severely limited.

I would approach your version as a performance-engineering project rather than another conventional Java backend.

The repository also has an existing Java 25 / Spring Boot 4 contribution in PR #33. That gives us a useful starting point, but it doesn't mean you should copy its architecture. A more interesting objective would be to implement a competing Java solution, compare different approaches experimentally, and establish which design performs best under the challenge's constraints.

Research scope: I could access the challenge repository, its specification and the Java pull request. The supplied YouTube watch page did not return usable video content, so I won't attribute specific technical claims or benchmark results to that video without verification.

## 1. Understanding the challenge

The project is maintained by arjaythedev.

Its central question is:

How many concurrent users can a $12/month server support when running a relatively simple, database-backed API?

The author originally benchmarked eight programming languages using basic, largely unoptimized implementations. The repository then opened the experiment to community submissions using different languages, frameworks and optimization strategies.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub



### Hardware constraints

[DigitalOcean - Scalable Cloud Hosting Solutions – ToolPilot](https://images.openai.com/static-rsc-4/R2vx-KVDzvFCAEagN_W6hy5YzGirDyOYotfzpJ5a2A3gRoe6HB1EGMlAoI2mwmZJ60bqXoHbcmLGFy_EQTRZMzxuEopNEtjtPCLTefcq3f15M5X8V8140fo7jtpihIUAuRmGLOhscLTWuB7_I35A5tNMHPHK9kdywncopV8jyPc?purpose=inline)

DigitalOcean Basic Droplet

Ubuntu 24.04 · x86-64

# $12/mo

PROCESSOR

# 1

vCPU

MEMORY

# 2 GB

RAM

SWAP

# None

DATABASE

# SQLite

The entire application must share these resources with the operating system and, if enabled, Nginx.

This is particularly interesting for Java because the JVM introduces several variables that are less prominent in some native-language implementations: heap sizing, garbage collection, JIT compilation, thread stacks, startup behavior and native memory.

### Rules that influence your architecture

The competition is not simply about producing the fastest response under artificial conditions.

| Constraint                      | Architectural consequence                              |
| ------------------------------- | ------------------------------------------------------ |
| SQLite is mandatory             | No switching to PostgreSQL                             |
| Fixed database schema           | No additional indexes or tables                        |
| No caching across requests      | Every request must retrieve its data from SQLite       |
| Durable writes                  | A successful write must be committed before responding |
| No external services            | Everything must run on the target VM                   |
| Any language/framework          | You can use Spring, Netty, Vert.x or plain Java        |
| Any number of threads/processes | Concurrency architecture is your choice                |
| 60-second startup deadline      | Startup and initialization matter                      |

SQLite's own page cache, memory mapping and prepared-statement reuse are explicitly permitted.

The distinction between database-level caching and application-level caching is important: you can optimize SQLite's internal behavior, but cannot store previously retrieved responses in a Java `HashMap` to bypass subsequent database reads.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub



### How performance is scored

The official benchmark follows a structured procedure:

1. Warm up with 1,000 virtual users for two minutes.
2. Start testing at 2,500 users, doubling the load until a run fails.
3. Narrow the maximum passing load to within 250 users.
4. Confirm the result with a five-minute sustained run.

A successful run must satisfy all three conditions:

p95 latency

# 500 ms

Below

p99 latency

# 1 s

Below

Error rate

# 1%

Below

This changes the optimization objective considerably. Maximizing requests per second while allowing extreme tail latency is not enough. Your implementation must remain responsive and reliable under sustained concurrency.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub



## 2. The API you need to implement

The challenge uses a small social-feed API, similar to a simplified Twitter backend.

It has five endpoints. The API contract is intentionally strict: exact response structure, JSON field order, error messages and authentication behavior matter.

### Endpoints

| Method | Endpoint          | Purpose                                 |
| ------ | ----------------- | --------------------------------------- |
| GET    | `/health`         | Verify application and database health  |
| GET    | `/feed`           | Retrieve the 20 newest posts            |
| GET    | `/posts/:id`      | Retrieve an individual post             |
| POST   | `/posts`          | Create an authenticated post            |
| POST   | `/posts/:id/like` | Like a post, preventing duplicate likes |

For example, the feed returns a compact JSON object containing an array of posts. Every post includes its ID, body, creation timestamp, author username and current number of likes.

The health endpoint must actually execute `SELECT 1`. Returning a hard-coded healthy response would violate the specification.

The authentication mechanism is JWT with HS256. Tokens are supplied through the `Authorization: Bearer` header, and their signatures and expiration must be checked on every authenticated request.

There is no login endpoint. Tokens are generated as part of the seed data.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub



### Database structure

The provided SQLite database contains three tables:

users

50,000 seeded records

`id` · `username` · `created_at`

posts

500,000 records

`id` · `user_id` · `body` · `created_at`

likes

2,000,000 records

`user_id` · `post_id` · `created_at`

Initial dataset generated by the repository's seed script.

The schema already includes indexes supporting feed ordering, post ownership and like lookups. You cannot introduce additional indexes, so query design becomes particularly important.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub

+1



### The database is probably where your most valuable optimization work will happen

Consider the feed endpoint.

The reference query retrieves the newest posts and calculates the number of likes for each post using a correlated subquery.

That is reasonable because only 20 posts need to be returned. However, the performance of this approach depends heavily on whether SQLite uses the appropriate indexes and how the query planner executes it.

You should investigate:

- `EXPLAIN QUERY PLAN` for each endpoint.
- Whether queries unnecessarily materialize intermediate results.
- Prepared-statement reuse.
- SQLite connection ownership.
- WAL configuration and transaction boundaries.
- Reader/writer contention.

The objective is to minimize CPU time per request without violating the no-caching rule.

## 3. Analysis of the existing Java contribution: PR #33

PR #33 — Java 25 / Spring Boot 4 submission

Open when inspected

This contribution is especially relevant because somebody has already attempted to solve the same problem using Java.

The PR is authored by `nb-99` and contains four commits and 35 changed files. Its title identifies Java 25 and Spring Boot 4 as the selected stack. The visible change list includes nine Java files, a Maven project, integration tests, deployment scripts, Docker configuration, Kubernetes manifests and benchmarking utilities.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub



### What the contribution contains

The application is organized around these classes:

| Class                     | Apparent responsibility         |
| ------------------------- | ------------------------------- |
| `Application.java`        | Application entry point         |
| `FeedController.java`     | HTTP endpoint handling          |
| `PostRepository.java`     | Database access                 |
| `Authentication.java`     | JWT authentication              |
| `Responses.java`          | Response representation         |
| `ApiException.java`       | Application-specific exceptions |
| `ErrorHandler.java`       | Error translation               |
| `FallbackController.java` | Unmatched-route handling        |
| `ApiTest.java`            | API testing                     |

These responsibilities are inferred from the visible file structure; they are not a claim that every implementation detail has been independently verified.

The separation is sensible for a relatively small application. It avoids mixing SQL, authentication, HTTP routing and exception handling inside a single large class.

### Its development infrastructure is unusually extensive

The contribution also includes:

- A `Makefile` for development workflows.
- Docker and Docker Compose configuration.
- Separate direct, Docker and Kubernetes benchmark scripts.
- Python benchmark/reporting utilities.
- A dedicated `OPTIMIZATIONS.md`.
- Installation, build and startup scripts.

The later commits specifically introduce separate benchmark modes, VPS-limit enforcement and Kubernetes benchmarking.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub



This suggests the author is treating the challenge as a repeatable engineering experiment, not merely an exercise in implementing five endpoints.

That is the part I would borrow most heavily: the emphasis on repeatable measurements.

### What I would do differently

I would not automatically select Spring Boot simply because the existing Java submission uses it.

Spring Boot is excellent for ordinary backend development, but this challenge has an unusual resource profile: one CPU core, two gigabytes of RAM, SQLite and thousands of concurrent connections.

Every framework feature should justify its cost.

For your version, I would investigate three alternative approaches.

| Approach                          | Main advantage                                    | Main concern                                   |
| --------------------------------- | ------------------------------------------------- | ---------------------------------------------- |
| Spring Boot + JDBC                | Fast development and mature ecosystem             | Framework overhead                             |
| JDK HTTP Server + virtual threads | Minimal architecture and excellent learning value | HTTP server implementation may become limiting |
| Netty + JDBC                      | Greater control over networking and allocations   | More complex concurrency design                |

The existing PR provides a comparison target for the first approach. The other two would make your project more distinctive.

Importantly, I cannot conclude that one is faster than the others from the PR's structure alone. That requires controlled benchmarking.

## 4. Proposed architecture for your Java implementation

I would give your project its own identity, for example TwelveDollarJava, and develop it as a small, modular application.

My preferred starting configuration would be:

| Component                     | Selection                     |
| ----------------------------- | ----------------------------- |
| Language                      | Java 25                       |
| Build system                  | Maven                         |
| HTTP server                   | JDK HTTP Server initially     |
| Concurrency                   | Virtual threads               |
| Database                      | SQLite                        |
| Database access               | JDBC                          |
| Authentication                | JCA (`Mac`, HmacSHA256)       |
| JSON                          | Jackson                       |
| Testing                       | JUnit 5 + official test suite |
| Benchmarking                  | k6                            |
| Deployment                    | Ubuntu 24.04                  |
| Initial network configuration | Behind Nginx                  |

I would deliberately avoid Hibernate/JPA for this project. You have a fixed schema, a handful of SQL statements and strict requirements concerning query behavior. Direct JDBC gives you greater visibility into database interactions.

### Architecture diagram

\#chatgpt-mermaid-\_r_1tu\_{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;font-size:14px;fill:rgb(255, 255, 255);}@keyframes edge-animation-frame{from{stroke-dashoffset:0;}}@keyframes dash{to{stroke-dashoffset:0;}}#chatgpt-mermaid-\_r_1tu\_ .edge-animation-slow{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 50s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_1tu\_ .edge-animation-fast{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 20s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-\_r_1tu\_ .error-icon{fill:rgba(255, 255, 255, 0.03);}#chatgpt-mermaid-\_r_1tu\_ .error-text{fill:rgb(255, 255, 255);stroke:rgb(255, 255, 255);}#chatgpt-mermaid-\_r_1tu\_ .edge-thickness-normal{stroke-width:1px;}#chatgpt-mermaid-\_r_1tu\_ .edge-thickness-thick{stroke-width:3.5px;}#chatgpt-mermaid-\_r_1tu\_ .edge-pattern-solid{stroke-dasharray:0;}#chatgpt-mermaid-\_r_1tu\_ .edge-thickness-invisible{stroke-width:0;fill:none;}#chatgpt-mermaid-\_r_1tu\_ .edge-pattern-dashed{stroke-dasharray:3;}#chatgpt-mermaid-\_r_1tu\_ .edge-pattern-dotted{stroke-dasharray:2;}#chatgpt-mermaid-\_r_1tu\_ .marker{fill:rgba(255, 255, 255, 0.498);stroke:rgba(255, 255, 255, 0.498);}#chatgpt-mermaid-\_r_1tu\_ .marker.cross{stroke:rgba(255, 255, 255, 0.498);}#chatgpt-mermaid-\_r_1tu\_ svg{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;font-size:14px;}#chatgpt-mermaid-\_r_1tu\_ p{margin:0;}#chatgpt-mermaid-\_r_1tu\_ .label{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:rgb(255, 255, 255);}#chatgpt-mermaid-\_r_1tu\_ .cluster-label text{fill:rgb(255, 255, 255);}#chatgpt-mermaid-\_r_1tu\_ .cluster-label span{color:rgb(255, 255, 255);}#chatgpt-mermaid-\_r_1tu\_ .cluster-label span p{background-color:transparent;}#chatgpt-mermaid-\_r_1tu\_ .label text,#chatgpt-mermaid-\_r_1tu\_ span{fill:rgb(255, 255, 255);color:rgb(255, 255, 255);}#chatgpt-mermaid-\_r_1tu\_ .node rect,#chatgpt-mermaid-\_r_1tu\_ .node circle,#chatgpt-mermaid-\_r_1tu\_ .node ellipse,#chatgpt-mermaid-\_r_1tu\_ .node polygon,#chatgpt-mermaid-\_r_1tu\_ .node path{fill:rgb(26, 40, 61);stroke:rgb(31, 78, 148);stroke-width:1px;}#chatgpt-mermaid-\_r_1tu\_ .rough-node .label text,#chatgpt-mermaid-\_r_1tu\_ .node .label text,#chatgpt-mermaid-\_r_1tu\_ .image-shape .label,#chatgpt-mermaid-\_r_1tu\_ .icon-shape .label{text-anchor:middle;}#chatgpt-mermaid-\_r_1tu\_ .node .katex path{fill:#000;stroke:#000;stroke-width:1px;}#chatgpt-mermaid-\_r_1tu\_ .rough-node .label,#chatgpt-mermaid-\_r_1tu\_ .node .label,#chatgpt-mermaid-\_r_1tu\_ .image-shape .label,#chatgpt-mermaid-\_r_1tu\_ .icon-shape .label{text-align:center;}#chatgpt-mermaid-\_r_1tu\_ .node.clickable{cursor:pointer;}#chatgpt-mermaid-\_r_1tu\_ .root .anchor path{fill:rgba(255, 255, 255, 0.498)!important;stroke-width:0;stroke:rgba(255, 255, 255, 0.498);}#chatgpt-mermaid-\_r_1tu\_ .arrowheadPath{fill:rgba(255, 255, 255, 0.498);}#chatgpt-mermaid-\_r_1tu\_ .edgePath .path{stroke:rgba(255, 255, 255, 0.498);stroke-width:1px;}#chatgpt-mermaid-\_r_1tu\_ .flowchart-link{stroke:rgba(255, 255, 255, 0.498);fill:none;}#chatgpt-mermaid-\_r_1tu\_ .edgeLabel{background-color:rgb(24, 24, 24);text-align:center;}#chatgpt-mermaid-\_r_1tu\_ .edgeLabel p{background-color:rgb(24, 24, 24);}#chatgpt-mermaid-\_r_1tu\_ .edgeLabel rect{opacity:0.5;background-color:rgb(24, 24, 24);fill:rgb(24, 24, 24);}#chatgpt-mermaid-\_r_1tu\_ .labelBkg{background-color:rgba(24, 24, 24, 0.5);}#chatgpt-mermaid-\_r_1tu\_ .cluster rect{fill:rgba(255, 255, 255, 0.03);stroke:rgba(255, 255, 255, 0.082);stroke-width:1px;}#chatgpt-mermaid-\_r_1tu\_ .cluster text{fill:rgb(255, 255, 255);}#chatgpt-mermaid-\_r_1tu\_ .cluster span{color:rgb(255, 255, 255);}#chatgpt-mermaid-\_r_1tu\_ div.mermaidTooltip{position:absolute;text-align:center;max-width:200px;padding:2px;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;font-size:12px;background:rgba(255, 255, 255, 0.03);border:1px solid rgba(255, 255, 255, 0.082);border-radius:2px;pointer-events:none;z-index:100;}#chatgpt-mermaid-\_r_1tu\_ .flowchartTitleText{text-anchor:middle;font-size:18px;fill:rgb(255, 255, 255);}#chatgpt-mermaid-\_r_1tu\_ rect.text{fill:none;stroke-width:0;}#chatgpt-mermaid-\_r_1tu\_ .icon-shape,#chatgpt-mermaid-\_r_1tu\_ .image-shape{background-color:rgb(24, 24, 24);text-align:center;}#chatgpt-mermaid-\_r_1tu\_ .icon-shape p,#chatgpt-mermaid-\_r_1tu\_ .image-shape p{background-color:rgb(24, 24, 24);padding:2px;}#chatgpt-mermaid-\_r_1tu\_ .icon-shape .label rect,#chatgpt-mermaid-\_r_1tu\_ .image-shape .label rect{opacity:0.5;background-color:rgb(24, 24, 24);fill:rgb(24, 24, 24);}#chatgpt-mermaid-\_r_1tu\_ .label-icon{display:inline-block;height:1em;overflow:visible;vertical-align:-0.125em;}#chatgpt-mermaid-\_r_1tu\_ .node .label-icon path{fill:currentColor;stroke:revert;stroke-width:revert;}#chatgpt-mermaid-\_r_1tu\_ .node .neo-node{stroke:rgb(31, 78, 148);}#chatgpt-mermaid-\_r_1tu\_ [data-look="neo"].node rect,#chatgpt-mermaid-\_r_1tu\_ [data-look="neo"].cluster rect,#chatgpt-mermaid-\_r_1tu\_ [data-look="neo"].node polygon{stroke:url(#chatgpt-mermaid-\_r_1tu\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_1tu\_ [data-look="neo"].swimlane.cluster rect{filter:none;}#chatgpt-mermaid-\_r_1tu\_ [data-look="neo"].node path{stroke:url(#chatgpt-mermaid-\_r_1tu\_-gradient);stroke-width:1px;}#chatgpt-mermaid-\_r_1tu\_ [data-look="neo"].node .outer-path{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_1tu\_ [data-look="neo"].node .neo-line path{stroke:rgb(31, 78, 148);filter:none;}#chatgpt-mermaid-\_r_1tu\_ [data-look="neo"].node circle{stroke:url(#chatgpt-mermaid-\_r_1tu\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_1tu\_ [data-look="neo"].node circle .state-start{fill:#000000;}#chatgpt-mermaid-\_r_1tu\_ [data-look="neo"].icon-shape .icon{fill:url(#chatgpt-mermaid-\_r_1tu\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_1tu\_ [data-look="neo"].icon-shape .icon-neo path{stroke:url(#chatgpt-mermaid-\_r_1tu\_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-\_r_1tu\_ .node text{font-size:14px;font-weight:600;letter-spacing:normal;fill:rgb(153, 206, 255);}#chatgpt-mermaid-\_r_1tu\_ .edgeLabels text{font-size:13px;font-weight:600;letter-spacing:-0.08px;fill:rgb(153, 206, 255);}#chatgpt-mermaid-\_r_1tu\_ .node tspan[font-weight="normal"],#chatgpt-mermaid-\_r_1tu\_ .edgeLabels tspan[font-weight="normal"]{font-weight:600;}#chatgpt-mermaid-\_r_1tu\_ .edgeLabel .label rect{opacity:1;rx:13px;ry:13px;fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-width:1px;}#chatgpt-mermaid-\_r_1tu\_ .node rect,#chatgpt-mermaid-\_r_1tu\_ .node circle,#chatgpt-mermaid-\_r_1tu\_ .node ellipse,#chatgpt-mermaid-\_r_1tu\_ .node polygon,#chatgpt-mermaid-\_r_1tu\_ .node path{fill:rgb(0, 40, 77);stroke:rgba(255, 255, 255, 0.1);stroke-width:1px;}#chatgpt-mermaid-\_r_1tu\_ .node rect{rx:16px;ry:16px;}#chatgpt-mermaid-\_r_1tu\_ .node.mermaid-decision .label-container{fill:rgb(0, 14, 26);stroke:rgb(26, 62, 95);stroke-dasharray:2,2;}#chatgpt-mermaid-\_r_1tu\_ .edgePaths .flowchart-link{stroke:rgba(255, 255, 255, 0.498);stroke-width:1px;stroke-linecap:round;stroke-linejoin:round;}#chatgpt-mermaid-\_r_1tu\_ .marker{fill:rgba(255, 255, 255, 0.498);stroke:rgba(255, 255, 255, 0.498);}#chatgpt-mermaid-\_r_1tu\_ :root{--mermaid-font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;}#chatgpt-mermaid-\_r_1tu\_ .blue rect{fill:rgb(219, 234, 254)!important;stroke:rgb(37, 99, 235)!important;color:rgb(30, 58, 138)!important;}#chatgpt-mermaid-\_r_1tu\_ .blue polygon{fill:rgb(219, 234, 254)!important;stroke:rgb(37, 99, 235)!important;color:rgb(30, 58, 138)!important;}#chatgpt-mermaid-\_r_1tu\_ .blue ellipse{fill:rgb(219, 234, 254)!important;stroke:rgb(37, 99, 235)!important;color:rgb(30, 58, 138)!important;}#chatgpt-mermaid-\_r_1tu\_ .blue circle{fill:rgb(219, 234, 254)!important;stroke:rgb(37, 99, 235)!important;color:rgb(30, 58, 138)!important;}#chatgpt-mermaid-\_r_1tu\_ .blue path{fill:rgb(219, 234, 254)!important;stroke:rgb(37, 99, 235)!important;color:rgb(30, 58, 138)!important;}#chatgpt-mermaid-\_r_1tu\_ .blue tspan{fill:rgb(30, 58, 138)!important;}#chatgpt-mermaid-\_r_1tu\_ .green rect{fill:rgb(220, 252, 231)!important;stroke:rgb(22, 163, 74)!important;color:rgb(20, 83, 45)!important;}#chatgpt-mermaid-\_r_1tu\_ .green polygon{fill:rgb(220, 252, 231)!important;stroke:rgb(22, 163, 74)!important;color:rgb(20, 83, 45)!important;}#chatgpt-mermaid-\_r_1tu\_ .green ellipse{fill:rgb(220, 252, 231)!important;stroke:rgb(22, 163, 74)!important;color:rgb(20, 83, 45)!important;}#chatgpt-mermaid-\_r_1tu\_ .green circle{fill:rgb(220, 252, 231)!important;stroke:rgb(22, 163, 74)!important;color:rgb(20, 83, 45)!important;}#chatgpt-mermaid-\_r_1tu\_ .green path{fill:rgb(220, 252, 231)!important;stroke:rgb(22, 163, 74)!important;color:rgb(20, 83, 45)!important;}#chatgpt-mermaid-\_r_1tu\_ .green tspan{fill:rgb(20, 83, 45)!important;}k6 Load GeneratorNginxJava HTTP ServerRequest RouterJWT AuthenticationFeed ServicePost RepositorySQLite Connection ManagerSQLite WAL Database

The goal is to keep the request path short, explicit and measurable.

### Suggested project structure

```
twelve-dollar-java/
├── pom.xml
├── README.md
├── install.sh
├── build.sh
├── start.sh
├── docs/
│   ├── ARCHITECTURE.md
│   ├── BENCHMARKS.md
│   └── OPTIMIZATIONS.md
├── src/
│   ├── main/java/dev/challenge/
│   │   ├── Main.java
│   │   ├── config/
│   │   │   └── ServerConfig.java
│   │   ├── http/
│   │   │   ├── Router.java
│   │   │   ├── RequestHandler.java
│   │   │   └── JsonResponse.java
│   │   ├── auth/
│   │   │   └── JwtVerifier.java
│   │   ├── database/
│   │   │   ├── DatabaseManager.java
│   │   │   └── PostRepository.java
│   │   ├── service/
│   │   │   └── FeedService.java
│   │   └── model/
│   │       ├── Post.java
│   │       └── AuthenticatedUser.java
│   └── test/java/
│       └── dev/challenge/
└── benchmarks/
    └── results/
```

Keep the architecture small. You don't need a generic repository framework, dependency-injection framework or elaborate abstraction for five endpoints.

## 5. The five performance experiments I would prioritize

This is where your implementation could become genuinely interesting.

### Experiment 1 — Virtual threads versus platform threads

Java virtual threads are particularly useful for handling large numbers of concurrent blocking operations.

For a first prototype, you could use:

```
HttpServer server = HttpServer.create(
    new InetSocketAddress(host, port),
    0
);

server.setExecutor(
    Executors.newVirtualThreadPerTaskExecutor()
);
```

However, virtual threads are not automatically faster.

With one vCPU, the application still has extremely limited computational capacity. Virtual threads primarily help reduce the cost of waiting; they don't create additional CPU resources.

Hypothesis: Virtual threads will improve concurrency handling compared with an excessively large platform-thread pool, but the benefit may diminish once SQLite becomes the bottleneck.

Test both configurations under identical conditions.

### Experiment 2 — SQLite connection management

SQLite supports multiple concurrent readers in WAL mode, but only one writer at a time.

That makes database access strategy particularly important.

I would test three configurations:

| Configuration | Description                                                |
| ------------- | ---------------------------------------------------------- |
| A             | One shared, synchronized connection                        |
| B             | Small fixed connection pool                                |
| C             | Separate read connections and a dedicated write connection |

Configuration A is the simplest baseline.

Configuration B potentially allows more concurrent database operations.

Configuration C offers more control over writes and may help prevent read requests from becoming unnecessarily blocked behind application-level write synchronization.

Start by testing a small pool rather than opening a connection for every virtual thread.

Also, avoid assuming that the largest pool will win. On a single-core machine, excessive concurrency can increase contention and memory consumption.

### Experiment 3 — Prepared statements

Your application executes a very small collection of SQL statements repeatedly.

Prepared statements are therefore an obvious optimization candidate.

For example:

```
private static final String FIND_POST = """
    SELECT p.id,
           p.body,
           p.created_at,
           u.username,
           (
               SELECT COUNT(*)
               FROM likes l
               WHERE l.post_id = p.id
           ) AS like_count
    FROM posts p
    JOIN users u ON u.id = p.user_id
    WHERE p.id = ?
    """;
```

Investigate whether retaining prepared statements per connection improves performance compared with repeatedly preparing them.

Do not share a mutable `PreparedStatement` across concurrently executing requests.

Prepared-statement reuse is permitted by the challenge rules.

### Experiment 4 — JSON serialization

For such a small API, serialization is another area worth investigating.

You could compare:

- Standard Jackson serialization.
- Jackson with reusable configuration.
- Carefully implemented direct JSON output for fixed response structures.

The third approach is interesting because the post structure is fixed and compact.

However, manually generating JSON introduces correctness risks, especially around string escaping and UTF-8.

I would implement Jackson first, measure it, and only consider custom serialization if profiling demonstrates that it consumes a meaningful percentage of CPU time.

### Experiment 5 — Nginx versus direct HTTP

The challenge allows two deployment strategies.

Configuration A — Nginx

Client → Nginx → Java application

Nginx manages external connections, reducing the number of connections the Java server must handle directly.

Configuration B — Direct

Client → Java application

Removes reverse-proxy processing but requires Java to handle thousands of simultaneous keep-alive connections.

The repository notes that Nginx consumed approximately 14–20% of CPU in the original video benchmark. Removing it therefore presents a potential optimization opportunity, but that does not establish that direct serving will be faster for your implementation.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub

+1



I would initially use Nginx for comparability, then test direct HTTP as a separate experiment.

## 6. JVM memory tuning

The two-gigabyte memory limit deserves special attention.

A common mistake would be to give Java nearly all available RAM through `-Xmx`.

That leaves insufficient space for native memory, thread stacks, JIT-generated code, SQLite, the operating system and possibly Nginx.

For an initial experiment, consider:

```
java \
  -Xms128m \
  -Xmx768m \
  -XX:+UseG1GC \
  -jar target/server.jar
```

These values are experimental starting points, not verified optimal settings.

The JVM's heap size does not represent its total memory consumption.

Track at least the following metrics:

| Metric                  | Why it matters                       |
| ----------------------- | ------------------------------------ |
| Resident Set Size (RSS) | Actual process memory footprint      |
| Heap usage              | Java object allocation pressure      |
| GC pause duration       | Potential impact on tail latency     |
| CPU utilization         | Whether the single core is saturated |
| Active connections      | Networking pressure                  |
| SQLite busy events      | Database contention                  |
| p95/p99 latency         | Actual benchmark success             |

I would also test different heap sizes rather than assume that more memory always improves throughput.

For a challenge this constrained, a small and predictable memory footprint may be more valuable than maximizing heap capacity.

## 7. Correctness comes before optimization

The repository provides an official suite containing 42 checks.

Your first major milestone should be passing all 42, before attempting aggressive performance improvements.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://github.com\&sz=32)

GitHub

+1



Pay particular attention to authentication and error precedence.

For example, the specification requires an unauthenticated like request containing an invalid post ID to return an authentication error first.

That is easy to get wrong if your router validates path parameters before checking authorization.

Other important edge cases include duplicate likes, malformed JSON, empty post bodies, expired tokens and incorrectly signed JWTs.

I would implement an automated test pipeline:

```
Build
  ↓
JUnit tests
  ↓
Generate fresh SQLite seed
  ↓
Start Java application
  ↓
Wait for health check
  ↓
Run official 42 checks
  ↓
Stop application
  ↓
Report result
```

The original repository already provides `test/run.sh`, so reuse it rather than inventing a competing correctness standard.

## 8. Development roadmap

I would organize the work into six milestones, with each producing a measurable result.

Phase 1 — Foundation

First

Set up Maven, Java, configuration loading and SQLite connectivity.

Deliverable: an application that starts successfully and responds to `/health`.

Phase 2 — API implementation

Implement all five endpoints, JWT verification, request validation and exact response formatting.

Deliverable: 42/42 official tests passing.

Phase 3 — Performance baseline

Run the official k6 workload without implementing speculative optimizations.

Record latency, throughput, memory consumption and CPU utilization.

Deliverable: `BENCHMARKS.md` containing the initial results.

Phase 4 — SQLite optimization

Experiment with connection management, prepared statements, transaction behavior and SQL execution plans.

Deliverable: a documented before/after comparison.

Phase 5 — JVM and networking

Experiment with virtual threads, heap configuration, garbage collection and direct versus proxied HTTP.

Deliverable: a reproducible performance configuration.

Phase 6 — Final submission

Validate the implementation on Ubuntu 24.04 under the official resource constraints.

Prepare the installation, build and startup scripts, along with the technical README.

Deliverable: a submission-ready Java implementation.

### One important benchmarking rule

Change one meaningful variable at a time.

If you simultaneously replace the HTTP server, increase the SQLite connection pool and change the JVM's garbage collector, you won't know which modification caused the performance difference.

Each experiment should document its hypothesis, configuration, measured result and conclusion.

## 9. Which Java approach should you actually choose?

There are three realistic directions.

Plain Java + JDK HTTP Server

Minimal dependencies, virtual threads and direct control over application behavior.

Java + Netty

An event-driven networking experiment with more control over HTTP processing.

Java + Spring Boot

A conventional backend architecture that can be compared against PR #33.

Best for building your own version from first principles.

You can focus on Java concurrency, SQLite and the JVM without introducing substantial framework complexity. Its HTTP implementation will still need to prove itself under the benchmark.

Develop this approach&#x20;

## 10. Final assessment

I would build your first implementation using Java 25, virtual threads, direct JDBC and a deliberately small architecture.

The most valuable aspect of this project is the opportunity to learn performance engineering using a language you already know, rather than spending most of your time learning an unfamiliar language.

You will get practical experience with JVM internals, concurrency, SQL optimization, HTTP networking, Linux deployment and benchmarking methodology.

There is also an opportunity to turn this into a stronger GitHub portfolio project by publishing the experimental evidence rather than simply presenting a final benchmark score.

A good final repository would answer not just how many users can my Java server handle?, but also which optimizations helped, which didn't, and why?

### Primary references

These are the resources I would keep open throughout development:

| Resource                    | Purpose                                                     |
| --------------------------- | ----------------------------------------------------------- |
| Your supplied YouTube video | Video reference; content not independently verified here    |
| Challenge repository        | Rules and submission requirements                           |
| SPEC.md                     | Exact API contract                                          |
| schema.sql                  | Database structure                                          |
| Official tests              | Correctness requirements                                    |
| Official benchmark          | Performance workload                                        |
| Java PR #33                 | Existing Java implementation and development infrastructure |

Research limitation: This is an architecture and implementation-planning report, not a completed source-level audit or reproduced benchmark. The GitHub PR's file inventory and portions of its diff were accessible, but its full Java implementation was not independently executed or profiled. No performance ranking or numerical Java benchmark result has been inferred from that incomplete evidence.