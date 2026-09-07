# AGENTS.md — Spring with OpenTelemetry (spring-with-otel)

## Build & verification

```bash
# Full build (docker image + helm packaging, ITs incl. compose)
./mvnw clean install

# Fast verify (skip app start/stop + docker image build/publish)
./mvnw clean verify -Dskip.start.stop.springboot=true -Dskip.docker.build=true -Dskip.docker.publish=true

# Single unit test class (surefire)
./mvnw test -Dtest=HelloControllerTest

# Non-Docker ITs only (no compose; OTel exporters disabled via properties)
./mvnw verify -Dskip.start.stop.springboot=true -Dskip.docker.build=true -Dskip.docker.publish=true -Dit.test='SpringApplicationIT,ActuatorInfoIT,HelloControllerIT'

# Observability ITs (require Docker: spring-boot starts compose with the full stack)
./mvnw verify -Dskip.start.stop.springboot=true -Dskip.docker.build=true -Dskip.docker.publish=true -Dit.test='ch.dboeckli.example.otel.observability.*IT'

# Format check (validate phase): spring-javaformat + spotless
./mvnw validate
```

Build order: `validate` (format) → `compile` → `test` (surefire, `*Test`) → `verify` (failsafe, `*IT`,
compose + helm lint/template) → `install` (docker image build + helm dry-run/package) → `deploy`
(push docker image + helm chart; CI only). CI runs `mvn -B -e deploy` (master) and
`mvn -B -e verify -Dskip.start.stop.springboot=true -Dskip.docker.build=true -Dskip.docker.publish=true`
+ Sonar (analyze job).

After changing code, always verify: run the relevant Maven goal above and report its output (evidence,
not just "done").

## Sandbox build quirks (background)

- This sandbox mounts the repo via filesystem passthrough, which blocks symlinks — Spotless's
  `npm install` (prettier) would fail with `EPERM` unless npm skips bin links. The sandbox kit sets
  `npm_config_bin_links=false` globally, so no manual export is needed. On a normal host
  (Windows/CI) this does not apply either.
- The SNAPSHOT dependency `ch.dboeckli:spring-6-rest-mvc-api:0.0.1-SNAPSHOT` is resolved from
  GitHub Packages (`maven.pkg.github.com`). The kit writes `~/.m2/settings.xml` with server `github`
  (`<password>${env.GITHUB_MAVEN_TOKEN}</password>`); the sandbox proxy injects a classic
  `read:packages` PAT. `mvn dependency:get` ignores the settings `<proxies>` and 401s there are a
  false alarm — only real builds (validate/compile/...) are representative.

## Test quirks

- `*Test` classes (surefire, `test` phase) run before `*IT` classes (failsafe, `verify` phase) —
  enforced by `TestClassOrderer`.
- `LocaleExtension` (auto-discovered) sets `Locale.US` globally.
- Plain ITs (`SpringApplicationIT`, `ActuatorInfoIT`, `HelloControllerIT`) disable the OTel exporters
  via `otel.traces.exporter=none` / `otel.metrics.exporter=none` / `otel.logs.exporter=none` and need
  no collector or docker. `SpringApplicationIT` uses `useMainMethod = ALWAYS`.
- Observability ITs (`observability` package) set `spring.docker.compose.skip.in-tests=false` and
  therefore start `compose.yaml` themselves (elasticsearch, kibana, apm-server, otel-collector,
  prometheus, jaeger, zipkin). They trigger a `/hello` call and poll the real backends via Awaitility
  (Elasticsearch `localhost:9200`, Prometheus `localhost:9090`, Jaeger `localhost:16686`). They need
  Docker with enough memory and run slowly. `management.otlp.metrics.export.step` /
  `otel.metric.export.interval` are lowered for faster metric export in those tests.
- Log assertions on MDC: `HelloControllerIT` checks `trace_id`/`span_id`/`trace_flags` in the log
  events (logback `ListAppender`).

## Architecture

- **Stack**: Spring Boot 3.5.16 (spring-boot-starter-web, actuator, aop), Java 25, Lombok,
  `ch.dboeckli.opentelemetry:spring-with-otel`. Spring Boot docker-compose support (`compose.yaml`).
- **Observability**: `opentelemetry-spring-boot-starter` (OTel BOM 2.31.x) exports traces/metrics/logs
  via OTLP HTTP to the collector at `http://localhost:4318` (profile `local`). Additional encoders:
  logstash-logback + ECS encoder. Metrics also via `micrometer-registry-prometheus`.
- **API**: `GET /hello` → `{"message":"hello"}`. `HelloController` → `HelloService` (tracer created
  from injected `OpenTelemetry`); tracing/observability filters in `tracing/` (baggage, trace-parent,
  debug); log messages + change listener in `log/`.
- **Actuator**: all endpoints exposed locally (`/actuator/*`, health probes, info incl. build/git,
  `/actuator/prometheus`). Configprops/env show-values are enabled for local observability demos — do
  not copy that to production.
- Profiling: active profile is `local` (`src/main/resources/application-local.yaml`, server port 8080).

## Docker & K8s

`docker compose up` starts the full stack (see compose.yaml): `otel-collector` (OTLP 4318), `jaeger`
(16686), `zipkin` (9411), `prometheus` (9090, scrapes `otel-collector:8889`), `elasticsearch` (9200),
`kibana` (5601), `apm-server` (8200). Run the app with `./mvnw spring-boot:run` (profile `local`) or the
`SpringApplication.run.xml` run config.

Helm packaging is part of the `install` phase (exec-maven-plugin runs the `helm` binary directly). Charts
live in `helm-charts/`; during the build Maven copies them to `target/helm-charts`, merges
`dependencies-values.yaml`, and versions them `<project.version>` (SNAPSHOT → `-snapshot.<git.abbrev>` on
feature branches, `-snapshot` on main/master; the `v` prefix and `-SNAPSHOT` suffix are dropped). The
chart is renamed to `<artifactId>-chart`, packaged as `target/helm/repo/<artifactId>-chart-<version>.tgz`,
and pushed via `helm registry login`/`helm push` to `oci://registry-1.docker.io/${env.DOCKER_USER}` in the
`deploy` phase. K8s deploy on the host goes through the PowerShell scripts `.run/scripts/deploy-k8s.ps1` /
`uninstall-k8s.ps1` / `test-k8s.ps1` (IntelliJ run configs).

## Dependency management

- **Dependabot**: GitHub Actions only (`.github/dependabot.yml`, daily).
- **Renovate**: maven, maven-wrapper, docker-compose, kubernetes, helm-values, helmv3, custom.regex
  (Java LTS via Adoptium). All updates PR-only, no automerge. Details: `.github/renovate-strategy.md`.

## CI (GitHub Actions)

- `maven-build.yml`: `setup` (version calc) → `build` (`mvn -B -e deploy` incl. docker/helm push) →
  `analyze` (`verify` with docker/start-stop skips + SonarCloud) → `Trigger-Deploy` (runs
  `deploy-and-test-cluster.yml`, push only).
- `release.yml`: Maven release flow (main/master only, requires SNAPSHOT).
- CI profile `ci-cd` auto-activates via `env.GITHUB_ACTIONS=true`.

