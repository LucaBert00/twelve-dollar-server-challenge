# Spring Boot PR #33 — Source Record

> Immutable source record for Task 1. Do not edit as a living design doc.
> Retrieval date: 2026-10-13 (UTC). All timestamps below are GitHub UTC.

## 1. Identity

- PR: https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33
- Title: `feat: add Java 25 Spring Boot 4 submission`
- Author: [@nb-99](https://github.com/nb-99) (Niklas Beilmann, `niklas@beilmann.dev`)
- State at inspection: **OPEN**, `mergeable: MERGEABLE`, `mergedAt: null`
- Base: `arjaythedev/twelve-dollar-server-challenge@main`
- Head source: `nb-99/twelve-dollar-server-challenge` branch `feat/java25-spring-boot4`
  - Separate fork, not a branch of the upstream repo.
- Head SHA inspected: `7a9cb997ac2d981c321899cb82bd5f8c1b7ca6c9`
- Created: 2026-10-10T11:15:06Z · Updated: 2026-10-13T19:31:34Z
- Local preservation: branch `reference/spring-boot-pr33` = exact head SHA above.
  Do not merge into research or implementation branches.

## 2. Scope

- `additions: 3232`, `deletions: 0`, `changedFiles: 35`
- All new files under `submissions/java-spring-boot-nb-99/`
- No changes outside the submission folder (per challenge submission rule).

Changed files (35):

```text
submissions/java-spring-boot-nb-99/.env-sample
submissions/java-spring-boot-nb-99/.envrc
submissions/java-spring-boot-nb-99/.gitignore
submissions/java-spring-boot-nb-99/Dockerfile
submissions/java-spring-boot-nb-99/Dockerfile.dockerignore
submissions/java-spring-boot-nb-99/Makefile
submissions/java-spring-boot-nb-99/OPTIMIZATIONS.md
submissions/java-spring-boot-nb-99/README.md
submissions/java-spring-boot-nb-99/benchmark.py
submissions/java-spring-boot-nb-99/benchmark_report.py
submissions/java-spring-boot-nb-99/build.sh
submissions/java-spring-boot-nb-99/compose.yaml
submissions/java-spring-boot-nb-99/container-start.sh
submissions/java-spring-boot-nb-99/install.sh
submissions/java-spring-boot-nb-99/k8s/k6.yaml
submissions/java-spring-boot-nb-99/k8s/kustomization.yaml
submissions/java-spring-boot-nb-99/k8s/server.yaml
submissions/java-spring-boot-nb-99/maven-settings.xml.template
submissions/java-spring-boot-nb-99/pom.xml
submissions/java-spring-boot-nb-99/scripts/direct.sh
submissions/java-spring-boot-nb-99/scripts/docker.sh
submissions/java-spring-boot-nb-99/scripts/k8s.sh
submissions/java-spring-boot-nb-99/scripts/lib.sh
submissions/java-spring-boot-nb-99/seed-local.sh
submissions/java-spring-boot-nb-99/src/main/java/challenge/ApiException.java
submissions/java-spring-boot-nb-99/src/main/java/challenge/Application.java
submissions/java-spring-boot-nb-99/src/main/java/challenge/Authentication.java
submissions/java-spring-boot-nb-99/src/main/java/challenge/ErrorHandler.java
submissions/java-spring-boot-nb-99/src/main/java/challenge/FallbackController.java
submissions/java-spring-boot-nb-99/src/main/java/challenge/FeedController.java
submissions/java-spring-boot-nb-99/src/main/java/challenge/PostRepository.java
submissions/java-spring-boot-nb-99/src/main/java/challenge/Responses.java
submissions/java-spring-boot-nb-99/src/main/resources/application.properties
submissions/java-spring-boot-nb-99/src/test/java/challenge/ApiTest.java
submissions/java-spring-boot-nb-99/start.sh
```

## 3. Commits (4)

| SHA | Date (UTC) | Headline |
|-----|------------|----------|
| `fa5a0a2e52111c085db520eab11298e938b80dde` | 2026-10-10T11:14:10Z | `feat: add Java 25 Spring Boot 4 submission` |
| `83d355110d5ecce9b77b19ae593a69eda69866f1` | 2026-10-11T16:51:50Z | `build(java): add autonomous Makefile workflow` |
| `2879838a9c9eefe6f3f3b391a6c994336655c2dd` | 2026-10-11T19:39:12Z | `build(java): split benchmark modes and enforce VPS limits` |
| `7a9cb997ac2d981c321899cb82bd5f8c1b7ca6c9` | 2026-10-12T10:27:46Z | `build(java): add Kubernetes benchmark mode` |

Commit bodies (abbreviated):

- `fa5a0a2e`: SQLite-backed API behind Nginx with Docker Compose; contract tests, bounded k6 limit-search, Markdown/HTML reports, documented future optimizations.
- `83d3551`: autonomous Makefile workflow (no body).
- `2879838a`: builds with installed Maven/Java; challenge suite + benchmark in Docker or host via MODE; `benchmark.py` only warms up, searches, samples, reports; app+Nginx in one container limited to 1 vCPU / 2 GiB, no swap, using challenge `bench/nginx.conf`; `BENCH_K6=local` runs k6 on host.
- `7a9cb997`: `MODE=k8s` builds/pushes multi-arch images, runs Nginx+app as two containers of one pod sharing 1 vCPU / 2 GiB, k6 in own pod on another node via `kubectl exec`; restricted Pod Security Standard; registry/namespace/pull secret from `K8S_*`.

## 4. Original PR description (verbatim)

> Implement the SQLite-backed challenge API behind Nginx with Docker Compose. Include contract tests, bounded k6 limit-search automation, Markdown and HTML reports, and documented future optimizations.

Source: `gh pr view 33 --json body`, 2026-10-13.

## 5. Stack snapshot (from head commit)

- Java: Eclipse Temurin 25.0.1+8, `java.version=25`
- Spring Boot: 4.0.3 (`spring-boot-starter-parent`)
- Web layer: `spring-boot-starter-webmvc` (blocking Servlet on Tomcat), Jackson 3, `spring-boot-starter-jdbc`, HikariCP via Boot defaults
- SQLite: Xerial `sqlite-jdbc` 3.53.4.0
- Build: Maven 3.9.11, `spotless-maven-plugin` 3.1.0 (googleJavaFormat AOSP, `check` in `validate`)
- `src/main/resources/application.properties` at head:

```properties
server.address=${HOST:127.0.0.1}
server.port=${PORT:3000}
server.tomcat.keep-alive-timeout=70s
spring.web.resources.add-mappings=false
spring.jackson.deserialization.fail-on-trailing-tokens=true
spring.main.banner-mode=off
```

Notably absent: `spring.threads.virtual.enabled=true` — the subject of the discussion below.
- `server.tomcat.keep-alive-timeout=70s` satisfies the SPEC rule (Nginx keeps ≤64 idle conns; server must keep idle ≥65 s).
- Mode: behind Nginx (`127.0.0.1:3000`), not direct `:80`.
- `OPTIMIZATIONS.md` explicitly lists virtual threads, Tomcat thread tuning, pool sizing, page cache/`mmap_size`, WAL checkpoints, prepared-statement reuse, reader/writer split, group commit, query variants, like check+insert combining, keep-alive max-requests, heap/GC, allocation reduction, and direct-vs-Nginx as *measure-first candidates*, with rule-6/5 non-negotiables restated.

## 6. Discussion material — complete at inspection

Access method: `gh pr view 33 --json comments` + `gh api repos/.../pulls/33/comments|reviews` + `gh api repos/.../issues/33/comments` on 2026-10-13.

- Issue/conversation comments: **7**
- Inline code-review comments (`pulls/33/comments`): **0**
- Submitted formal reviews (`pulls/33/reviews`): **0**
- Review threads / resolution state: **none** (no review API objects to resolve)
- Commit discussions: none separate from the 7 comments
- Maintainer (`arjaythedev`) comments: **none** observed
- All commenters have `authorAssociation: NONE`
- Benchmark results posted in discussion: **none**

### 6.1 Comment 1

- Author: [@zitreF](https://github.com/zitreF) · 2026-10-13T17:53:47Z
- URL: https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33#issuecomment-6151765247
- Body (verbatim):

```text
you forgot:
spring.threads.virtual.enabled=true
:)
```

- Technical implication: claims the submission runs on platform threads, missing the one-line Boot switch to virtual threads on the Tomcat connector. Verified true at head SHA: `application.properties` contains no `spring.threads.virtual` key. Whether it helps on 1 vCPU with SQLite's single-writer is unproven — see OPTIMIZATIONS.md caution ("extra concurrency can increase the SQLite wait queue on one CPU").

### 6.2 Comment 2

- Author: [@stohirov](https://github.com/stohirov) · 2026-10-13T19:02:15Z
- URL: https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33#issuecomment-6152991472
- Body:

```text
@nb-99 configs...
```

- Technical implication: terse prompt for config disclosure/discussion; no concrete claim. Leads to 6.4.

### 6.3 Comment 3 (author reply)

- Author: `nb-99` · 2026-10-13T19:09:59Z
- URL: https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33#issuecomment-6153132205
- Body:

```text
> you forgot: spring.threads.virtual.enabled=true :)

@zitreF Have not done much optimizing so far, will run a bench and see if there are gains
```

- Technical implication: author confirms the submission is a correctness baseline, not yet tuned; commits to benchmarking virtual threads rather than accepting the claim on faith. Aligns with OPTIMIZATIONS.md "measure before changing".

### 6.4 Comment 4 (author reply)

- Author: `nb-99` · 2026-10-13T19:11:11Z
- URL: https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33#issuecomment-6153154430
- Body:

```text
> @nb-99 configs...

@stohirov any suggestions for config adjustments?
```

- Technical implication: author solicits tuning advice; no decision taken in-thread.

### 6.5 Comment 5

- Author: `zitreF` · 2026-10-13T19:11:12Z
- URL: https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33#issuecomment-6153154645
- Body:

```text
it will surely bring optimizations, if you want even more you can try spring-webflux
```

- Technical implication: asserts virtual threads "will surely bring optimizations" (no numbers posted) and proposes Spring WebFlux (reactive Netty) as a further step. For this workload the claim is contested by 6.7 (NIO/Netty framing) and by the SQLite reality: the bottleneck is the single SQLite writer + 1 vCPU, not Tomcat thread count. WebFlux would also change the programming model (non-blocking JDBC does not exist for SQLite; R2DBC-SQLite still serializes on the file). No benchmark posted to settle it.

### 6.6 Comment 6 (author reply)

- Author: `nb-99` · 2026-10-13T19:14:26Z
- URL: https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33#issuecomment-6153220267
- Body:

```text
Sure, I'll run both and see how much is in it.
```

- Technical implication: author agrees to test both (virtual threads on MVC + WebFlux variant). No results posted as of inspection.

### 6.7 Comment 7

- Author: `stohirov` · 2026-10-13T19:31:10Z (edited; `includesCreatedEdit: true`)
- URL: https://github.com/arjaythedev/twelve-dollar-server-challenge/pull/33#issuecomment-6153521191
- Body:

```text
for this specific case, it is better to go with nio. I will submit mine soon. currently testing.

@nb-99 you can check out raw Netty as well but framework will add too much latency in the code. 

currently, you have not enabled virtual threads in config
```

- Technical implication:
  - Recommends NIO (i.e. Tomcat NIO connector, already the Boot default) over virtual-thread-per-request for this specific case, and hints a competing submission is in testing.
  - Suggests raw Netty over full framework to cut latency ("framework will add too much latency") — relevant to our independent-Java track: minimal servlet/Netty/JDK-HttpServer surface vs Spring MVC overhead is an open question to benchmark, not a settled fact.
  - Reiterates the virtual-threads-off observation (confirmed true at head SHA).
  - No numbers, no profile, no config diff posted.

## 7. Review status summary

- No `CHANGES_REQUESTED` / `APPROVED` reviews.
- No inline threads to resolve.
- No maintainer ruling on correctness, rule compliance, or merge timeline.
- CI state not captured in this record (Task 1 scope is discussion + metadata; CI logs are out of scope and change over time).

## 8. Performance claims vs evidence

| Claim | Source | Evidence posted | Status |
|-------|--------|-----------------|--------|
| Virtual threads will improve the score | zitreF (6.1, 6.5) | None (no bench numbers) | Unproven; author to test (6.3, 6.6) |
| WebFlux beats MVC here | zitreF (6.5) | None | Unproven; conflicts with 6.7 NIO advice and SQLite single-writer reality |
| NIO is better "for this specific case"; raw Netty beats framework | stohirov (6.7) | None ("currently testing", competing submission pending) | Unproven; directionally plausible for connection count but must pass rule 5/6 and SPEC byte-exactness |
| Submission is unoptimized baseline | nb-99 (6.3), OPTIMIZATIONS.md | Consistent with code (default pool/threads, no virtual threads) | Accepted as author statement |

No benchmark results, profiles, or technical corrections beyond the missing-flag observation appear in the discussion at inspection time.

## 9. Challenge-rule observations (from code, not from discussion)

- Rule 5 (no cross-request caches): discussion does not allege a violation; OPTIMIZATIONS.md explicitly forbids response/query caches, table copies, remembered JWT verifications.
- Rule 6 (WAL + `synchronous=NORMAL` or stronger, durable commit before response): same — documented as non-negotiable; actual pragma wiring lives in code to be inspected in Task 2, not judged here.
- Schema fixed: no allegation of schema change.
- Submission hygiene (install/build/start, pinned versions, no binaries): README + scripts present; CI verdict not recorded here.

## 10. What this reference is and is not

- It **is**: an open, unmerged, unreviewed community submission — a useful correctness baseline and a catalog of stated-but-unmeasured optimization candidates.
- It **is not**: an accepted reference implementation, a benchmark result, or a ruling on virtual threads vs NIO vs WebFlux vs raw Netty for this workload.
- Our fork must treat it as read-only evidence (`reference/spring-boot-pr33`), never as a merge source.

## 11. Retrieval limits

- Bodies above are complete for the 7 issue comments at inspection time; edits after 2026-10-13T19:31:10Z are not captured.
- Reactions: none on any comment at inspection.
- GitHub web UI may render additional timeline events (force-pushes, CI statuses, cross-links) not exposed via the `comments`/`reviews` APIs used here; where the APIs return empty, this record reports empty rather than inventing content.
- Suggested re-check before Task 2 benchmarking: re-run `gh pr view 33 --json comments,reviews` and `gh api .../pulls/33/{comments,reviews}` and diff against §6.
