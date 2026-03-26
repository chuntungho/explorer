# Tech Stack

## Language & Runtime
- Java 21 (virtual threads enabled)
- Spring Boot 4.0.2 (Spring MVC, not WebFlux)

## Build System
- Gradle with Groovy DSL
- GraalVM Native Image support via `org.graalvm.buildtools.native`

## Key Dependencies
- `spring-boot-starter-webmvc` – servlet-based web layer
- `spring-boot-starter-restclient` / `RestTemplate` – outbound HTTP proxying
- `jsoup 1.22.1` – HTML parsing and DOM manipulation
- `org.brotli:dec` – Brotli decompression for proxied responses
- `openrewrite` – migration tooling (Spring Boot 4 upgrade recipe)

## Common Commands

```bash
# Run locally
./gradlew bootRun

# Run tests
./gradlew test

# Build fat JAR
./gradlew bootJar

# Build Docker image (native)
./gradlew bootBuildImage

# Apply OpenRewrite recipes
./gradlew rewrite
```

## Configuration
- `application.yml` – base config, activates profile via `APP_ENV` env var (default: `local`)
- `application-local.yml` – local overrides (excluded from native image)
- `docker/application-prod.yml` – production overrides mounted at runtime
- `ingress.properties` – sets ingress server port (default: 2024, override via `ingress.port`)
- `proxy.properties` – sets proxy server port (default: 2025, override via `proxy.port`)

## Testing
- JUnit 5 via `spring-boot-starter-test`
- `@SpringBootTest` for integration tests
