# Project Structure

## Overview

The project is a single Gradle module (`explorer`) that boots three Spring application contexts from one JVM process.

```
src/main/java/com/chuntung/
├── explorer/               # Shared core library (no Spring Boot entry point)
│   ├── config/             # Spring config, properties, RestTemplate setup, exception handling
│   ├── handler/            # BlockHandler interface + implementations (request/HTML filtering)
│   ├── manager/            # Core business logic (ProxyManager, HtmlResolver, BlockManager, AssetManager)
│   └── util/               # UrlUtil (host encoding/decoding), CustomFileTypeDetector
├── ingress/                # Port 2024 – entry point app context
│   ├── IngressApplication  # @ComponentScan + @PropertySource(ingress.properties)
│   └── IngressController   # Handles ?url= redirect and static asset serving
└── proxy/                  # Port 2025 – proxy app context
    ├── ProxyApplication    # @ComponentScan + @PropertySource(proxy.properties)
    └── ProxyController     # Handles subdomain-encoded proxy requests

src/main/resources/
├── application.yml         # Base configuration + explorer.* properties
├── application-local.yml   # Local dev overrides
├── ingress.properties      # ingress server port
├── proxy.properties        # proxy server port
├── static/                 # Frontend assets (browser.html, browser.js, browser.css, interceptor.js)
└── META-INF/services/      # SPI: CustomFileTypeDetector registration

src/test/java/com/chuntung/explorer/
├── controller/             # Integration tests (ExplorerControllerTest)
└── util/                   # Unit tests (CustomFileTypeDetectorTest)

docker/
├── docker-compose.yml
├── application-prod.yml    # Production config (mounted as volume)
└── nginx-explorer.conf     # Nginx reverse proxy config (wildcard SSL)
```

## Key Architectural Patterns

- **Multi-context boot**: `MainApplication` uses `SpringApplicationBuilder` to launch `IngressApplication` (child) and `ProxyApplication` (sibling), sharing beans from the parent context (e.g. `ExplorerProperties`, `ProxyManager`).
- **Host encoding**: Remote hostnames are encoded into subdomains by replacing `.` with `-` and `-` with `--`. Hosts >63 chars are MD5-hashed and cached in `UrlUtil.hostCache`.
- **BlockHandler interface**: Implement `BlockHandler` to intercept requests (`preHandle`) or mutate parsed HTML (`postHtmlHandle`). Handlers are matched by URI via `match(URI)`.
- **ExplorerProperties**: All configuration lives under the `explorer.*` namespace, bound via `@ConfigurationProperties("explorer")`.
- **No redirect on proxy**: `RestTemplate` is configured with `NoRedirectSimpleClientHttpRequestFactory` — redirects are rewritten and returned to the browser rather than followed server-side.
- **HTML rewriting**: `HtmlResolver` uses jsoup to rewrite `src`, `href`, `action`, and `data-*` attributes, and handles gzip/brotli content encoding transparently.
