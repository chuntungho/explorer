# Micronaut Migration Design

**Date:** 2026-05-03
**Branch:** micronaut-migration
**Goal:** Convert the Spring Boot 4 WebFlux application to Micronaut to achieve a smaller native binary footprint in Docker containers (linux/amd64, linux/arm64).

## Context

The Explorer app is a stateless reactive HTTP reverse proxy. It has no database, no Spring Security, no messaging — just Reactor Netty HTTP server + client, JSoup HTML manipulation, and a filter chain for blocking/URL-rewriting. Spring Boot 4.0.5 + WebFlux is already configured for GraalVM native compilation, but Spring's native images are larger (~45–65MB RSS) than Micronaut's (~20–35MB RSS).

Key constraints driving the Micronaut choice:
- Native binary is the primary goal
- Keep Reactor `Mono`/`Flux` (minimal rewrite)
- Docker deployment (linux/amd64 + linux/arm64)

## Architecture

The overall package structure and component boundaries are unchanged. Every Spring component has a direct Micronaut equivalent. Business logic classes are untouched.

### Component Mapping

| Spring | Micronaut | Change |
|---|---|---|
| `@SpringBootApplication` | `@MicronautApplication` | Annotation swap |
| `@Component` / `@Service` | `@Singleton` | Annotation swap |
| `@ConfigurationProperties("explorer")` | `@ConfigurationProperties("explorer")` | None |
| `@RestControllerAdvice` | `@Singleton` + `ExceptionHandler<Throwable>` | Minor |
| `ApplicationContextAware` | `@Inject ApplicationContext` | Inject directly |
| `WebFilter` + `WebFilterChain` | `HttpServerFilter` + `ServerFilterChain` | Rewrite |
| `ServerWebExchange` | `HttpRequest<?>` + `HttpResponse<?>` | Rewrite |
| `WebClient` (Reactor Netty) | `RxHttpClient` + `micronaut-reactor` bridge | Rewrite |
| `@CrossOrigin` | CORS config in `application.yml` | Config |
| GraalVM native plugin (Spring) | `io.micronaut.application` plugin | Replace plugin |

## Build System

**Remove:**
- `org.springframework.boot` plugin
- `io.spring.dependency-management` plugin
- `graalvmNative` plugin from Spring
- All `org.springframework.*` dependencies

**Add:**
- `io.micronaut.application` plugin (4.x) — handles native image, Docker, dependency management
- `io.micronaut.platform:micronaut-platform` BOM (4.7.x)
- `micronaut-http-server-netty` — Netty-based reactive HTTP server
- `micronaut-http-client` — replaces `WebClient`
- `micronaut-reactor` — Reactor `Mono`/`Flux` integration
- `micronaut-jackson-databind` — JSON support

**Keep:** `jsoup:1.22.1`, `org.brotli:dec:0.1.2`

## Code Changes by Tier

### Tier 1 — Annotation swaps (no logic changes)

These files require only annotation replacement, business logic is untouched:
- `MainApplication.java` — `@SpringBootApplication` → `@MicronautApplication`
- All `@Component` beans (`BlockManager`, `BlockRuleHandler`, `BrowserInterceptor`, `DomainBlackListHandler`, `AssetManager`, `HtmlResolver`, `ProxyManager`, `ExplorerSetting`) — `@Component` → `@Singleton`
- `BlockManager.java` — remove `ApplicationContextAware`, inject `ApplicationContext` via `@Inject`
- `WebExceptionHandler.java` — `@RestControllerAdvice` → `@Singleton` + implement `ExceptionHandler<Throwable>`

### Tier 2 — Controller rewrite (`ProxyController.java`)

Replace `ServerWebExchange` with `HttpRequest<?>`. Controllers return `Mono<HttpResponse<?>>`. Multipart handling uses Micronaut's `CompletedFileUpload`. Remove `@CrossOrigin` (moved to `application.yml`).

### Tier 3 — HTTP client rewrite (`ProxyManager.java`)

Replace `WebClient` with Micronaut's `RxHttpClient` (low-level). `RxHttpClient` returns `Flowable` which bridges to `Flux` via `micronaut-reactor`. The proxy logic — header transformation, zero-copy `Flux<DataBuffer>` streaming for non-HTML, body collection for HTML — is preserved. Only the client call sites change.

### Tier 4 — Filter rewrite (`ExplorerFilter.java`)

Replace `WebFilter` + `WebFilterChain` with `HttpServerFilter` + `ServerFilterChain`. The routing logic (static asset detection based on `Host` header) stays identical.

### Unchanged files

`HtmlResolver`, `AssetManager`, `BlockRuleHandler`, `DomainBlackListHandler`, `BrowserInterceptor`, `UrlUtil`, `ExplorerSetting`, `ExplorerProperties` — pure business logic with no reactive framework dependency.

## Configuration

`application.yml` — remove Spring-specific keys, add Micronaut equivalents:

```yaml
micronaut:
  server:
    netty:
      max-request-size: 268435456   # 256MB for multipart
  http:
    client:
      max-content-length: 10485760  # 10MB HTML buffer
cors:
  enabled: true
  configurations:
    all:
      allowed-origins-regex: ".*"
```

Remove `spring.threads.virtual.enabled` — Micronaut on Netty is non-blocking by default.

The `application-local.yml` profile and `APP_ENV` environment variable pattern carry over unchanged.

## Native Image & Docker

Micronaut's AOT compiler generates reflection/serialization metadata at compile time — no manual `reflect-config.json` needed for framework beans. JSoup requires a single manual hint:

```groovy
// build.gradle
graalvmNative {
    binaries.main {
        imageName = 'explorer'
        buildArgs.add('--initialize-at-build-time=org.jsoup')
    }
}
```

Docker multi-arch (`linux/amd64` + `linux/arm64`) via a two-stage `Dockerfile.native`:
1. **Build stage:** GraalVM CE compiles the native binary for the target arch
2. **Runtime stage:** `gcr.io/distroless/static` — no JVM, binary only

Expected image size: **~30–50MB** (vs ~200MB+ JVM image).

## Testing

Replace the broken Spring `MockMvc` test with a Micronaut embedded server test:
- `@MicronautTest` annotation
- Inject `HttpClient` pointing at the embedded server
- Test the multipart proxy route and the `robots.txt` endpoint

## Success Criteria

- `./gradlew nativeCompile` produces a binary for the host arch
- `./gradlew dockerBuildNative` produces a multi-arch image under 60MB
- All proxy routes behave identically to the Spring version
- Native binary RSS under 35MB at idle
