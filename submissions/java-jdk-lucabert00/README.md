# java-jdk-lucabert00 (Phase 1 skeleton)

Plain-Java baseline skeleton for Epic #13. Phase 1 only: pinned toolchain,
lifecycle scripts, and a 404-only JDK `HttpServer`. No endpoint logic, no
database code, no JWT code (those land in #15-#18).

## Stack

| Component | Pinned version |
|---|---|
| Language | Java 25 (`maven.compiler.release=25`) |
| JDK distribution | Eclipse Temurin `25.0.1+8` (Java 21+ is the floor, 25 is the approved line) |
| Build tool | Maven `3.9.11` (chosen because it matches the reference build shape) |
| SQLite driver | `org.xerial:sqlite-jdbc:3.53.4.0` (declared, not yet wired; wiring lands later) |
| Pool | `com.zaxxer:HikariCP:6.2.1` (declared, not yet wired; pool-4 is the starting posture per OPT-07) |
| Fat-jar plugin | `org.apache.maven.plugins:maven-shade-plugin:3.6.0` (build-time only) |
| JSON library | none in Phase 1 (decision owned by #17; no Jackson here) |

## Pins + checksums

| Artifact | Source | Checksum |
|---|---|---|
| Temurin JDK `25.0.1+8` x86_64 (`OpenJDK25U-jdk_x64_linux_hotspot_25.0.1_8.tar.gz`) | `https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.1%2B8/OpenJDK25U-jdk_x64_linux_hotspot_25.0.1_8.tar.gz` | SHA-256 `8daf77d1aacffe38c9889689bc224a13557de77559d9a5bb91991e6a298baa0d` |
| Temurin JDK `25.0.1+8` aarch64 (`OpenJDK25U-jdk_aarch64_linux_hotspot_25.0.1_8.tar.gz`) | same release, `aarch64` asset | SHA-256 `5c83b7d2121ed482fd06831a1eba1633dbab818aba6addddf48e075b97e6e9b8` |
| Maven `3.9.11` (`apache-maven-3.9.11-bin.tar.gz`) | `https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.tar.gz` | SHA-512 `bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978` |
| `org.xerial:sqlite-jdbc:3.53.4.0` jar | Maven Central `org/xerial/sqlite-jdbc/3.53.4.0/` | SHA-1 `47e21dd53efe1524fe0df6965cb65003ffe17172` |
| `com.zaxxer:HikariCP:6.2.1` jar | Maven Central `com/zaxxer/HikariCP/6.2.1/` | SHA-1 `2d60b91e74ee4ff10fc9a395d10b3b9c7061ef87` |

Re-print pins with one command (from the repo root):

```bash
/opt/java-challenge-25.0.1/bin/java -version; /opt/maven-challenge-3.9.11/bin/mvn -version; grep -E 'sqlite-jdbc.version|hikaricp.version|maven-shade-plugin|<artifactId>maven-shade-plugin</artifactId>' submissions/java-jdk-lucabert00/pom.xml
```

## Scripts contract

| File | Runs as | What it does |
|---|---|---|
| `install.sh` | root, once, clean Ubuntu 24.04 | installs the pinned JDK + Maven above, verifying archive checksums; idempotent via `.installed` markers |
| `build.sh` | normal user | `mvn clean package` with the pinned toolchain; downloads only pinned Maven Central deps; produces `target/server.jar`; no prebuilt binaries |
| `start.sh` | normal user, foreground | `exec java -Xmx512m -jar target/server.jar`; honors `SQLITE_PATH` / `JWT_SECRET` (required, not yet consumed) / `HOST` (default `127.0.0.1`) / `PORT` (default `3000`); `JAVA_OPTS` is the append point for later JVM flags |

`start.sh` binds `HOST:PORT` via `challenge.Main` and answers every path with
byte-exact `{"error":"not found"}` (`Content-Type: application/json`, status
404). `SIGTERM` stops the server via a shutdown hook and exits `0`.
JVM-flag changes (idle/keep-alive posture) are placeholders for later tickets,
not this one.

## Serve mode

Nginx-first (`127.0.0.1:3000` behind `bench/nginx.conf`). Direct `0.0.0.0:80`
is not served in Phase 1; mode comparison belongs to a later phase.

## Optimizations

Empty until measured. No tuning in Phase 1 (minimal-diff senior-dev mode:
smallest set that serves later phases).

## License notices

- This submission's own code is under the repo `MIT` license on merge (per challenge submitting rules).
- `org.xerial:sqlite-jdbc` — Apache-2.0 (manifest-noted direct dependency).
- `com.zaxxer:HikariCP` — Apache-2.0 (manifest-noted direct dependency).
- Reference shape precedent only: `reference/spring-boot-pr33:submissions/java-spring-boot-nb-99/{pom.xml,install.sh,build.sh,start.sh}` (Maven + script shape, read via `git show`, never merged, nothing copied wholesale).
