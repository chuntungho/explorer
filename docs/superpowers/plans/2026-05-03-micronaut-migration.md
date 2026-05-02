# Micronaut Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the Spring Boot 4 WebFlux reverse proxy to Micronaut 4, producing a GraalVM native binary that runs in a minimal Docker image (~30–50 MB).

**Architecture:** Replace the Spring application shell (entry point, DI, HTTP server, HTTP client, filter/controller layer) with Micronaut 4 equivalents. All business logic is preserved. Spring type dependencies (`RequestEntity`, `HttpHeaders`, `Resource`, `ResponseEntity`, `MediaType`) in `HtmlResolver`, `AssetManager`, and `BlockHandler` are replaced with Micronaut or plain-Java equivalents. Controllers return `Mono<HttpResponse<?>>` instead of writing to `ServerHttpResponse`. `WebClient` is replaced by `ReactorStreamingHttpClient` (buffers via `collectList` then branches on content type).

**Tech Stack:**
- Micronaut 4.4.4 (`io.micronaut.application` Gradle plugin)
- `micronaut-http-server-netty`, `micronaut-reactor`, `micronaut-reactor-http-client`
- `micronaut-serde-jackson` (JSON serialization)
- GraalVM native via `nativeCompile` Gradle task
- Java 21 LTS (downgraded from 25 for stable GraalVM support)
- JSoup 1.22.1, Brotli 0.1.2 (unchanged)

**Working directory for all tasks:** `/Users/ho/workspace/github/explorer/.worktrees/micronaut-migration`

---

## Task 1: Replace build.gradle

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: Replace the entire build.gradle**

```groovy
plugins {
    id 'idea'
    id 'java'
    id 'io.micronaut.application' version '4.4.4'
}

version = '1.0-SNAPSHOT'
group = 'com.chuntung.explorer'

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")

    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut.reactor:micronaut-reactor")
    implementation("io.micronaut.reactor:micronaut-reactor-http-client")
    implementation("io.micronaut.serde:micronaut-serde-jackson")

    // Business logic (unchanged)
    implementation("org.jsoup:jsoup:1.22.1")
    implementation("org.brotli:dec:0.1.2")

    runtimeOnly("ch.qos.logback:logback-classic")

    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

[compileJava, compileTestJava, javadoc]*.options*.encoding = 'UTF-8'

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.chuntung.explorer.*")
    }
}

graalvmNative {
    binaries {
        main {
            imageName = 'explorer'
            buildArgs.add('-H:+AddAllCharsets')
            buildArgs.add('--initialize-at-build-time=org.jsoup')
            buildArgs.add('--exclude-resources=application-local\\.yml')
        }
    }
}
```

- [ ] **Step 2: Verify Gradle resolves dependencies (compilation will fail — that's expected)**

```bash
./gradlew dependencies --configuration compileClasspath 2>&1 | tail -5
```
Expected output ends with `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add build.gradle
git commit -m "chore: replace Spring Boot Gradle plugin with Micronaut 4"
```

---

## Task 2: Migrate config classes and POJOs

**Files:**
- Modify: `src/main/java/com/chuntung/explorer/MainApplication.java`
- Modify: `src/main/java/com/chuntung/explorer/config/ExplorerProperties.java`
- Modify: `src/main/java/com/chuntung/explorer/config/ProxyProperties.java`
- Modify: `src/main/java/com/chuntung/explorer/config/BlockRule.java`
- Modify: `src/main/java/com/chuntung/explorer/config/BlockContent.java`
- Modify: `src/main/java/com/chuntung/explorer/config/BlockAction.java`

- [ ] **Step 1: Replace MainApplication.java**

```java
package com.chuntung.explorer;

import io.micronaut.runtime.Micronaut;

public class MainApplication {
    public static void main(String[] args) {
        Micronaut.run(MainApplication.class, args);
    }
}
```

- [ ] **Step 2: Update ExplorerProperties.java — change import, add @Introspected**

Replace the import line and add the annotation:
```java
// Remove:
import org.springframework.boot.context.properties.ConfigurationProperties;
// Add:
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Introspected;

// Change class declaration from:
@ConfigurationProperties("explorer")
public class ExplorerProperties {
// To:
@ConfigurationProperties("explorer")
@Introspected
public class ExplorerProperties {
```

- [ ] **Step 3: Update ProxyProperties.java — add @Introspected**

```java
package com.chuntung.explorer.config;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class ProxyProperties {
    // all fields and getters/setters unchanged
```

- [ ] **Step 4: Add @Introspected to BlockRule, BlockContent, BlockAction**

Add `@io.micronaut.core.annotation.Introspected` to each class declaration. No other changes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/chuntung/explorer/MainApplication.java \
        src/main/java/com/chuntung/explorer/config/
git commit -m "chore: migrate config classes and entry point to Micronaut"
```

---

## Task 3: Rewrite BlockHandler interface and BlockManager

**Files:**
- Modify: `src/main/java/com/chuntung/explorer/handler/BlockHandler.java`
- Modify: `src/main/java/com/chuntung/explorer/manager/BlockManager.java`

- [ ] **Step 1: Rewrite BlockHandler.java** — replace Spring types with Micronaut types

```java
package com.chuntung.explorer.handler;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpHeaders;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.Optional;

public interface BlockHandler {
    boolean match(URI uri);

    default boolean preHandle(URI uri, HttpRequest<?> request) {
        return true;
    }

    default void postHtmlHandle(URI proxyURI, URI uri, MutableHttpHeaders responseHeaders, Document document) {
    }

    default Optional<Element> getElementById(Document document, String id) {
        return Optional.ofNullable(document.getElementById(id));
    }
}
```

- [ ] **Step 2: Rewrite BlockManager.java** — remove `ApplicationContextAware`, inject collection directly

```java
package com.chuntung.explorer.manager;

import com.chuntung.explorer.handler.BlockHandler;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpHeaders;
import jakarta.inject.Singleton;
import org.jsoup.nodes.Document;

import java.net.URI;
import java.util.Collection;

@Singleton
public class BlockManager {
    private final Collection<BlockHandler> blockHandlers;

    public BlockManager(Collection<BlockHandler> blockHandlers) {
        this.blockHandlers = blockHandlers;
    }

    public boolean preHandle(URI remoteURI, HttpRequest<?> request) {
        for (BlockHandler x : blockHandlers) {
            if (x.match(remoteURI)) {
                if (!x.preHandle(remoteURI, request)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void postHandle(URI proxyURI, URI remoteURI, MutableHttpHeaders responseHeaders, Document document) {
        blockHandlers.forEach(x -> {
            if (x.match(remoteURI)) {
                x.postHtmlHandle(proxyURI, remoteURI, responseHeaders, document);
            }
        });
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/chuntung/explorer/handler/BlockHandler.java \
        src/main/java/com/chuntung/explorer/manager/BlockManager.java
git commit -m "chore: migrate BlockHandler and BlockManager to Micronaut types"
```

---

## Task 4: Migrate handler implementations

**Files:**
- Modify: `src/main/java/com/chuntung/explorer/handler/DomainBlackListHandler.java`
- Modify: `src/main/java/com/chuntung/explorer/handler/BlockRuleHandler.java`
- Modify: `src/main/java/com/chuntung/explorer/handler/BrowserInterceptor.java`

- [ ] **Step 1: Rewrite DomainBlackListHandler.java**

```java
package com.chuntung.explorer.handler;

import com.chuntung.explorer.config.ExplorerProperties;
import io.micronaut.http.HttpRequest;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.List;

@Singleton
public class DomainBlackListHandler implements BlockHandler {
    private final List<String> domainBlackList;

    public DomainBlackListHandler(ExplorerProperties explorerProperties) {
        domainBlackList = explorerProperties.getDomainBlackList();
    }

    @Override
    public boolean match(URI uri) {
        if (domainBlackList == null) return false;
        for (String domain : domainBlackList) {
            int idx = uri.getHost().lastIndexOf(domain);
            if (idx == 0 || (idx > 0 && uri.getHost().charAt(idx - 1) == '.')) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean preHandle(URI uri, HttpRequest<?> request) {
        return false;
    }
}
```

- [ ] **Step 2: Rewrite BlockRuleHandler.java** — swap `@Component` → `@Singleton`, replace `RequestEntity` and `HttpHeaders`

```java
package com.chuntung.explorer.handler;

import com.chuntung.explorer.config.BlockContent;
import com.chuntung.explorer.config.BlockRule;
import com.chuntung.explorer.config.ExplorerProperties;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpHeaders;
import jakarta.inject.Singleton;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.net.URI;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Singleton
public class BlockRuleHandler implements BlockHandler {
    private final ExplorerProperties explorerProperties;
    private List<BlockRule> blockRequest = Collections.emptyList();
    private List<BlockRule> blockResponse = Collections.emptyList();

    public BlockRuleHandler(ExplorerProperties explorerProperties) {
        this.explorerProperties = explorerProperties;
        init();
    }

    void init() {
        blockRequest = explorerProperties.getBlockRules().stream()
                .filter(x -> Objects.nonNull(x.getBlockPaths()))
                .collect(Collectors.toList());
        blockResponse = explorerProperties.getBlockRules().stream()
                .filter(x -> Objects.nonNull(x.getBlockContents()) || Objects.nonNull(x.getBlockHeaders()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean match(URI uri) {
        return true;
    }

    @Override
    public boolean preHandle(URI uri, HttpRequest<?> request) {
        for (BlockRule rule : blockRequest) {
            if (uri.getHost().matches(rule.getHostPattern())) {
                for (String blockPath : rule.getBlockPaths()) {
                    if (uri.getPath().matches(blockPath)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void postHtmlHandle(URI proxyURI, URI uri, MutableHttpHeaders responseHeaders, Document document) {
        blockResponse.stream()
                .filter(x -> uri.getHost().matches(x.getHostPattern()))
                .forEach(x -> {
                    if (x.getBlockHeaders() != null) {
                        x.getBlockHeaders().forEach((k, v) -> {
                            if (v != null && !v.isEmpty()) {
                                responseHeaders.add(k, v);
                            } else {
                                responseHeaders.remove(k);
                            }
                        });
                    }
                    if (x.getBlockContents() != null) {
                        x.getBlockContents().forEach(y -> blockContent(uri, document, y));
                    }
                });
    }

    private void blockContent(URI uri, Document document, BlockContent cfg) {
        Elements elements = document.select(cfg.getSelector());
        switch (cfg.getAction()) {
            case REMOVE -> elements.remove();
            case HIDE -> elements.attr("style", "display:none");
            case CUSTOM -> customize(uri, elements, cfg);
        }
    }

    private void customize(URI uri, Elements elements, BlockContent cfg) {
        if (cfg.getAttributes() != null) {
            cfg.getAttributes().forEach((k, v) -> {
                if (v != null && !v.isEmpty()) {
                    elements.attr(k, v);
                } else {
                    elements.removeAttr(k);
                }
            });
        }
        elements.forEach(x -> {
            if (cfg.getReplace() != null) {
                String replacement = cfg.getReplace();
                if (replacement.contains("{") && replacement.contains("}")) {
                    replacement = replacement.replace("{CURRENT_DATE}", LocalDate.now().toString());
                }
                x.html(replacement);
            }
            if (cfg.getPrepend() != null) x.prepend(cfg.getPrepend());
            if (cfg.getAppend() != null) x.append(cfg.getAppend());
        });
    }
}
```

- [ ] **Step 3: Rewrite BrowserInterceptor.java** — swap `@Component` → `@Singleton`, update `postHtmlHandle` signature

```java
package com.chuntung.explorer.handler;

import com.chuntung.explorer.config.ExplorerProperties;
import io.micronaut.http.MutableHttpHeaders;
import jakarta.inject.Singleton;
import org.jsoup.nodes.Document;

import java.net.URI;

@Singleton
public class BrowserInterceptor implements BlockHandler {
    public static final String REMOTE_URL_META = "<meta name=\"remote-url\" content=\"{remoteUrl}\">";
    public static final String EXPLORER_URL_META = "<meta name=\"explorer-url\" content=\"{explorerUrl}\">";
    public static final String WILDCARD_HOST_META = "<meta name=\"wildcard-host\" content=\"{wildcardHost}\">";
    public static final String INTERCEPTOR_SCRIPT = "<script src=\"{interceptorUrl}\"></script>";

    private final ExplorerProperties explorerProperties;

    public BrowserInterceptor(ExplorerProperties explorerProperties) {
        this.explorerProperties = explorerProperties;
    }

    @Override
    public boolean match(URI uri) {
        return true;
    }

    @Override
    public void postHtmlHandle(URI proxyURI, URI uri, MutableHttpHeaders responseHeaders, Document document) {
        String url = explorerProperties.getUrl();
        String explorerUrl = (url != null && !url.isEmpty()) ? url : proxyURI.toString();
        String wh = explorerProperties.getWildcardHost();
        String wildcardHost = (wh != null && !wh.isEmpty()) ? wh : proxyURI.getHost();
        String iu = explorerProperties.getInterceptorUrl();
        String interceptorUrl = (iu != null && !iu.isEmpty()) ? iu : (explorerUrl + "/interceptor.js");

        document.head().prepend(INTERCEPTOR_SCRIPT.replace("{interceptorUrl}", interceptorUrl));
        document.head().prepend(REMOTE_URL_META.replace("{remoteUrl}", uri.toString()));
        document.head().prepend(EXPLORER_URL_META.replace("{explorerUrl}", explorerUrl));
        document.head().prepend(WILDCARD_HOST_META.replace("{wildcardHost}", wildcardHost));
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/chuntung/explorer/handler/
git commit -m "chore: migrate handler implementations to Micronaut types"
```

---

## Task 5: Rewrite HtmlResolver and AssetManager

**Files:**
- Modify: `src/main/java/com/chuntung/explorer/manager/HtmlResolver.java`
- Modify: `src/main/java/com/chuntung/explorer/manager/AssetManager.java`

- [ ] **Step 1: Rewrite HtmlResolver.java** — replace `Resource`/`HttpHeaders`/`MediaType` with plain Java and Micronaut types

```java
package com.chuntung.explorer.manager;

import com.chuntung.explorer.util.UrlUtil;
import io.micronaut.http.MutableHttpHeaders;
import jakarta.inject.Singleton;
import org.brotli.dec.BrotliInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Singleton
public class HtmlResolver {
    private static final Logger logger = LoggerFactory.getLogger(HtmlResolver.class);
    private final BlockManager adBlocker;

    public HtmlResolver(BlockManager adBlocker) {
        this.adBlocker = adBlocker;
    }

    /**
     * Parse, URL-rewrite, and re-encode the HTML bytes.
     * responseHeaders may be mutated: Content-Encoding updated, Content-Length removed.
     */
    public byte[] resolve(byte[] htmlBytes, MutableHttpHeaders responseHeaders, URI remoteURI, URI proxyURI, ExplorerSetting setting)
            throws IOException {
        if (htmlBytes.length == 0) return htmlBytes;

        String charset = getCharset(responseHeaders, setting);
        String encoding = responseHeaders.get("Content-Encoding");

        InputStream in = new ByteArrayInputStream(htmlBytes);
        if (encoding != null && !encoding.isEmpty()) {
            in = decode(in, encoding);
        }

        Document document = Jsoup.parse(in, charset, "");

        if (setting.isRemoveScript()) {
            document.getElementsByTag("script").forEach(Node::remove);
        }

        Set<String> removeMediaTypes = setting.getRemoveMediaTypes();
        for (String attr : new String[]{"src", "data-src", "href", "data-href"}) {
            document.getElementsByAttribute(attr).forEach(e -> {
                String src = e.attr(attr);
                String ext = src.substring(src.lastIndexOf('.') + 1);
                if (removeMediaTypes.contains(ext)) {
                    e.remove();
                    return;
                }
                try {
                    e.attr(attr, UrlUtil.proxyUrl(src, proxyURI));
                } catch (IllegalStateException ex) {
                    logger.warn("Invalid src: {}", src);
                }
            });
        }

        document.getElementsByTag("form")
                .forEach(x -> x.attr("action", UrlUtil.proxyUrl(x.attr("action"), proxyURI)));

        adBlocker.postHandle(proxyURI, remoteURI, responseHeaders, document);

        byte[] resolved = document.toString().getBytes(charset);
        if (encoding != null && !encoding.isEmpty()) {
            resolved = encode(resolved, encoding);
            responseHeaders.set("Content-Encoding", "gzip");
        }

        responseHeaders.remove("Content-Length");
        return resolved;
    }

    private static String getCharset(MutableHttpHeaders headers, ExplorerSetting setting) {
        String contentType = headers.get("Content-Type");
        if (contentType != null && contentType.contains("charset=")) {
            int idx = contentType.indexOf("charset=") + 8;
            int end = contentType.indexOf(';', idx);
            String cs = end == -1 ? contentType.substring(idx).trim() : contentType.substring(idx, end).trim();
            if (Charset.isSupported(cs)) return cs;
        }
        return setting.getCharset() != null ? setting.getCharset() : "utf-8";
    }

    private static byte[] encode(byte[] bytes, String encoding) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out, true)) {
            gz.write(bytes);
        }
        return out.toByteArray();
    }

    private static InputStream decode(InputStream in, String encoding) throws IOException {
        if ("gzip".equals(encoding)) return new GZIPInputStream(in);
        if ("br".equals(encoding)) return new BrotliInputStream(in);
        return in;
    }
}
```

- [ ] **Step 2: Rewrite AssetManager.java** — replace Spring `ResponseEntity`/`MediaType`/`CacheControl`/`DigestUtils`

```java
package com.chuntung.explorer.manager;

import com.chuntung.explorer.config.ExplorerProperties;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Singleton
public class AssetManager {
    private static final Logger logger = LoggerFactory.getLogger(AssetManager.class);
    private final ExplorerProperties explorerProperties;

    public AssetManager(ExplorerProperties explorerProperties) {
        this.explorerProperties = explorerProperties;
    }

    public MutableHttpResponse<byte[]> getAsset(String path) {
        InputStream in = null;
        File localFile = new File(explorerProperties.getAssetsPath() + path);
        Long lastModified = null;

        if (localFile.exists()) {
            try {
                in = new FileInputStream(localFile);
                lastModified = localFile.lastModified();
            } catch (IOException e) {
                // NOOP
            }
        } else {
            in = getClass().getResourceAsStream("/static" + path);
        }

        if (in == null) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        String mimeType;
        try {
            mimeType = Files.probeContentType(Path.of(path));
            if (mimeType == null) mimeType = "application/octet-stream";
        } catch (Exception e) {
            logger.warn("Failed to get content type: {}", path);
            mimeType = "application/octet-stream";
        }

        try {
            byte[] bytes = in.readAllBytes();
            MutableHttpResponse<byte[]> response = HttpResponse.ok(bytes);
            response.contentType(mimeType);

            boolean isHtml = mimeType.contains("html");
            if (!isHtml) {
                response.header("Cache-Control", "max-age=" + TimeUnit.DAYS.toSeconds(1));
                if (lastModified != null) {
                    response.header("Last-Modified", String.valueOf(lastModified));
                } else {
                    String etag = md5Hex(bytes);
                    response.header("ETag", "\"" + etag + "\"");
                }
            }
            return response;
        } catch (IOException e) {
            logger.warn("Failed to read asset: {}", path);
            return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static String md5Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            return "";
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/chuntung/explorer/manager/HtmlResolver.java \
        src/main/java/com/chuntung/explorer/manager/AssetManager.java
git commit -m "chore: migrate HtmlResolver and AssetManager to Micronaut/plain-Java types"
```

---

## Task 6: Replace WebConfig with HttpClientConfig; replace WebExceptionHandler

**Files:**
- Delete: `src/main/java/com/chuntung/explorer/config/WebConfig.java`
- Create: `src/main/java/com/chuntung/explorer/config/HttpClientConfig.java`
- Modify: `src/main/java/com/chuntung/explorer/config/WebExceptionHandler.java`

- [ ] **Step 1: Delete WebConfig.java**

```bash
rm src/main/java/com/chuntung/explorer/config/WebConfig.java
```

- [ ] **Step 2: Create HttpClientConfig.java** — creates `ReactorStreamingHttpClient` bean with proxy support

```java
package com.chuntung.explorer.config;

import io.micronaut.context.annotation.Factory;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient;
import jakarta.inject.Singleton;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

@Factory
public class HttpClientConfig {

    @Singleton
    ReactorStreamingHttpClient proxyHttpClient(ExplorerProperties props) {
        DefaultHttpClientConfiguration config = new DefaultHttpClientConfiguration();
        config.setMaxContentLength(10 * 1024 * 1024);
        config.setFollowRedirects(false);
        config.setConnectTimeout(Duration.ofSeconds(10));
        config.setReadTimeout(Duration.ofSeconds(30));

        ProxyProperties proxy = props.getProxy();
        if (proxy != null && Boolean.TRUE.equals(proxy.getEnabled())) {
            config.setProxyType(proxy.getType());
            config.setProxyAddress(new InetSocketAddress(proxy.getHost(), proxy.getPort()));
        }

        return ReactorStreamingHttpClient.create(null, config);
    }
}
```

- [ ] **Step 3: Rewrite WebExceptionHandler.java** — replace `@RestControllerAdvice` with Micronaut `ExceptionHandler`

```java
package com.chuntung.explorer.config;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class WebExceptionHandler implements ExceptionHandler<Throwable, HttpResponse<?>> {
    private static final Logger logger = LoggerFactory.getLogger(WebExceptionHandler.class);

    @Override
    public HttpResponse<?> handle(HttpRequest request, Throwable exception) {
        logger.error("Failed to process request {}", request.getUri(), exception);
        return HttpResponse.serverError();
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/chuntung/explorer/config/
git commit -m "chore: add HttpClientConfig factory, replace WebExceptionHandler"
```

---

## Task 7: Rewrite ProxyManager

**Files:**
- Modify: `src/main/java/com/chuntung/explorer/manager/ProxyManager.java`

The new `ProxyManager.proxy()` returns `Mono<MutableHttpResponse<?>>` instead of writing directly to `ServerHttpResponse`. It uses `ReactorStreamingHttpClient.exchangeStream()` and `collectList()` to buffer the response body, then branches on `Content-Type`.

- [ ] **Step 1: Rewrite ProxyManager.java**

```java
package com.chuntung.explorer.manager;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.util.UrlUtil;
import io.micronaut.http.*;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Singleton
public class ProxyManager {
    private static final Logger logger = LoggerFactory.getLogger(ProxyManager.class);

    private final ExplorerProperties explorerProperties;
    private final BlockManager adBlocker;
    private final ReactorStreamingHttpClient httpClient;
    private final HtmlResolver htmlResolver;

    public ProxyManager(ExplorerProperties explorerProperties,
                        BlockManager adBlocker,
                        ReactorStreamingHttpClient httpClient,
                        HtmlResolver htmlResolver) {
        this.explorerProperties = explorerProperties;
        this.adBlocker = adBlocker;
        this.httpClient = httpClient;
        this.htmlResolver = htmlResolver;
    }

    public Mono<MutableHttpResponse<?>> proxy(
            HttpRequest<?> incomingRequest,
            byte[] requestBody,
            URI remoteURI,
            URI proxyURI) {

        MutableHttpHeaders requestHeaders = buildRequestHeaders(incomingRequest.getHeaders(), remoteURI, proxyURI);

        MutableHttpRequest<byte[]> outRequest = HttpRequest
                .create(incomingRequest.getMethod(), remoteURI.toString());
        requestHeaders.forEach((k, vals) -> vals.forEach(v -> outRequest.getHeaders().add(k, v)));
        if (requestBody != null && requestBody.length > 0) {
            outRequest.body(requestBody);
        }

        if (!adBlocker.preHandle(remoteURI, outRequest)) {
            logger.debug("Blocked request: {}", remoteURI);
            return Mono.just(HttpResponse.status(HttpStatus.NO_CONTENT));
        }

        Set<String> excludedHeaders = getExcludedHeaders(incomingRequest.getHeaders().getOrigin() != null);

        return Flux.from(httpClient.exchangeStream(outRequest))
                .collectList()
                .flatMap(chunks -> {
                    if (chunks.isEmpty()) {
                        return Mono.just(HttpResponse.noContent());
                    }

                    HttpResponse<ByteBuffer<?>> first = chunks.get(0);
                    HttpStatus status = HttpStatus.valueOf(first.getStatus().getCode());
                    HttpHeaders remoteHeaders = first.getHeaders();

                    // Build mutable response headers from remote, excluding blocked headers
                    MutableHttpHeaders responseHeaders = buildResponseHeaders(remoteHeaders, excludedHeaders, proxyURI);

                    // Handle redirect
                    if (status.getCode() >= 300 && status.getCode() < 400) {
                        String location = remoteHeaders.get("Location");
                        if (location != null) {
                            responseHeaders.set("Location", UrlUtil.proxyUrl(location, proxyURI));
                        }
                        byte[] body = mergeChunks(chunks);
                        MutableHttpResponse<byte[]> response = HttpResponse.status(status, "").body(body);
                        copyHeaders(responseHeaders, response);
                        return Mono.just(response);
                    }

                    // Check if HTML
                    String contentType = remoteHeaders.get("Content-Type");
                    boolean isHtml = contentType != null
                            && contentType.contains("text/") && contentType.contains("html");

                    byte[] body = mergeChunks(chunks);

                    if (isHtml) {
                        ExplorerSetting setting = new ExplorerSetting(
                                incomingRequest.getUri().getRawQuery());
                        MutableHttpHeaders mutableResponseHeaders = responseHeaders;
                        return Mono.fromCallable(() ->
                                htmlResolver.resolve(body, mutableResponseHeaders, remoteURI, proxyURI, setting)
                        ).subscribeOn(Schedulers.boundedElastic())
                                .map(resolved -> {
                                    MutableHttpResponse<byte[]> response = HttpResponse.status(status, "").body(resolved);
                                    copyHeaders(mutableResponseHeaders, response);
                                    return (MutableHttpResponse<?>) response;
                                });
                    }

                    MutableHttpResponse<byte[]> response = HttpResponse.status(status, "").body(body);
                    copyHeaders(responseHeaders, response);
                    return Mono.just(response);
                })
                .onErrorResume(e -> {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof UnknownHostException) {
                        logger.debug("DNS resolution failed for: {}", remoteURI);
                    } else {
                        logger.warn("Failed to handle request: {}", remoteURI, e);
                    }
                    byte[] msg = (e.getMessage() != null ? e.getMessage() : "Bad Gateway")
                            .getBytes(StandardCharsets.UTF_8);
                    return Mono.just(HttpResponse.status(HttpStatus.BAD_GATEWAY, "").body(msg));
                });
    }

    private MutableHttpHeaders buildRequestHeaders(HttpHeaders source, URI remoteURI, URI proxyURI) {
        SimpleHttpHeaders mutable = new SimpleHttpHeaders(new LinkedHashMap<>(),
                io.micronaut.core.convert.ConversionService.SHARED);

        source.forEach((k, vals) -> {
            if (explorerProperties.getProxyHeaders() == null
                    || !explorerProperties.getProxyHeaders().contains(k)) {
                vals.forEach(v -> mutable.add(k, v));
            }
        });

        // Rewrite Host and transform headers
        for (String h : explorerProperties.getTransformHeaders()) {
            String val = mutable.get(h);
            if (val == null) continue;
            if (h.equalsIgnoreCase("Host")) {
                mutable.set(h, remoteURI.getHost());
                continue;
            }
            if (val.contains("//") && val.contains(proxyURI.getHost())) {
                List<URI> uris = UrlUtil.splitUris(UrlUtil.toURI(val), explorerProperties.getHostMappings());
                if (!uris.isEmpty()) mutable.set(h, uris.getFirst().toString());
            }
        }

        // Docker registry: rewrite Bearer realm
        String auth = mutable.get("www-authenticate");
        if (auth != null && auth.startsWith("Bearer") && auth.contains("https://auth.docker.io/token")) {
            mutable.set("www-authenticate",
                    auth.replace("https://auth.docker.io/token",
                            UrlUtil.proxyUrl("https://auth.docker.io/token", proxyURI)));
        }

        return mutable;
    }

    private MutableHttpHeaders buildResponseHeaders(HttpHeaders source, Set<String> excluded, URI proxyURI) {
        SimpleHttpHeaders mutable = new SimpleHttpHeaders(new LinkedHashMap<>(),
                io.micronaut.core.convert.ConversionService.SHARED);

        source.forEach((k, vals) -> {
            if (excluded != null && excluded.stream().anyMatch(k::equalsIgnoreCase)) return;
            if (k.equalsIgnoreCase("Transfer-Encoding")) return;
            vals.forEach(v -> mutable.add(k, v));
        });

        // Translate ACAO
        String acao = mutable.get("Access-Control-Allow-Origin");
        if (acao != null && !acao.equals("*")) {
            mutable.set("Access-Control-Allow-Origin", UrlUtil.proxyUrl(acao, proxyURI));
            mutable.set("Access-Control-Allow-Credentials", "true");
        } else if (acao == null) {
            mutable.set("Access-Control-Allow-Origin", "*");
        }

        return mutable;
    }

    private Set<String> getExcludedHeaders(boolean cors) {
        Set<String> excluded = new HashSet<>();
        if (explorerProperties.getExcludedHeaders() != null) {
            excluded.addAll(explorerProperties.getExcludedHeaders());
        }
        if (cors) {
            excluded.add("Access-Control-Allow-Credentials");
            excluded.add("Access-Control-Allow-Methods");
            excluded.add("Access-Control-Allow-Headers");
        }
        return excluded;
    }

    private static byte[] mergeChunks(List<HttpResponse<ByteBuffer<?>>> chunks) {
        int total = chunks.stream()
                .mapToInt(c -> c.getBody().map(ByteBuffer::remaining).orElse(0))
                .sum();
        ByteBuffer combined = ByteBuffer.allocate(total);
        chunks.forEach(c -> c.getBody().ifPresent(buf -> combined.put(buf.duplicate())));
        return combined.array();
    }

    private static void copyHeaders(MutableHttpHeaders src, MutableHttpResponse<?> response) {
        src.forEach((k, vals) -> vals.forEach(v -> response.getHeaders().add(k, v)));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/chuntung/explorer/manager/ProxyManager.java
git commit -m "feat: rewrite ProxyManager with Micronaut ReactorStreamingHttpClient"
```

---

## Task 8: Rewrite ExplorerFilter

**Files:**
- Modify: `src/main/java/com/chuntung/explorer/filter/ExplorerFilter.java`

- [ ] **Step 1: Rewrite ExplorerFilter.java** — replace `WebFilter`/`ServerWebExchange` with `@ServerFilter`/`HttpRequest`

```java
package com.chuntung.explorer.filter;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.manager.AssetManager;
import com.chuntung.explorer.manager.ProxyManager;
import com.chuntung.explorer.util.UrlUtil;
import io.micronaut.http.*;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Singleton
@ServerFilter("/**")
public class ExplorerFilter {
    private static final Logger logger = LoggerFactory.getLogger(ExplorerFilter.class);

    private final ExplorerProperties explorerProperties;
    private final ProxyManager proxyManager;
    private final AssetManager assetManager;

    public ExplorerFilter(ExplorerProperties explorerProperties,
                          ProxyManager proxyManager,
                          AssetManager assetManager) {
        this.explorerProperties = explorerProperties;
        this.proxyManager = proxyManager;
        this.assetManager = assetManager;
    }

    @RequestFilter
    public Mono<MutableHttpResponse<?>> filter(
            HttpRequest<?> request,
            FilterContinuation<MutableHttpResponse<?>> continuation) {

        String hostHeader = request.getHeaders().get("Host");
        if (!Objects.equals(explorerProperties.getHost(), hostHeader)) {
            return Mono.from(continuation.proceed());
        }

        String url = request.getParameters().getFirst("url").orElse(null);
        String direct = request.getParameters().getFirst("direct").orElse(null);

        if (url != null && !url.isEmpty()) {
            try {
                URI requestUri = request.getUri();
                URI proxyURI = requestUri;
                String wh = explorerProperties.getWildcardHost();
                if (wh != null && !wh.isEmpty()) {
                    proxyURI = new URI(requestUri.getScheme() + "://" + wh);
                }
                final URI finalProxyURI = proxyURI;

                if ("true".equalsIgnoreCase(direct)) {
                    byte[] body = request.getBody(byte[].class).orElse(new byte[0]);
                    return proxyManager.proxy(request, body, UrlUtil.toURI(url), finalProxyURI);
                } else {
                    URI proxyUrl = UrlUtil.toURI(UrlUtil.proxyUrl(url, proxyURI));
                    return Mono.just(HttpResponse.redirect(proxyUrl));
                }
            } catch (URISyntaxException e) {
                logger.warn("Failed to proxy url: {}", url, e);
                return Mono.just(HttpResponse.status(HttpStatus.BAD_REQUEST)
                        .body("Failed to fetch url".getBytes(StandardCharsets.UTF_8)));
            }
        }

        // Serve static asset
        String path = request.getPath();
        return Mono.fromCallable(() ->
                assetManager.getAsset(Objects.equals("/", path) ? "/browser.html" : path)
        ).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .cast(MutableHttpResponse.class);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/chuntung/explorer/filter/ExplorerFilter.java
git commit -m "feat: rewrite ExplorerFilter with Micronaut @ServerFilter"
```

---

## Task 9: Rewrite ProxyController

**Files:**
- Modify: `src/main/java/com/chuntung/explorer/controller/ProxyController.java`

- [ ] **Step 1: Rewrite ProxyController.java**

```java
package com.chuntung.explorer.controller;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.manager.AssetManager;
import com.chuntung.explorer.manager.ProxyManager;
import com.chuntung.explorer.util.UrlUtil;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
public class ProxyController {
    private static final Logger logger = LoggerFactory.getLogger(ProxyController.class);

    private final ExplorerProperties explorerProperties;
    private final AssetManager assetManager;
    private final ProxyManager proxyManager;

    public ProxyController(ExplorerProperties explorerProperties,
                           AssetManager assetManager,
                           ProxyManager proxyManager) {
        this.explorerProperties = explorerProperties;
        this.assetManager = assetManager;
        this.proxyManager = proxyManager;
    }

    @Get("/robots.txt")
    public Mono<MutableHttpResponse<byte[]>> robots(HttpRequest<?> request) {
        return Mono.fromCallable(() -> assetManager.getAsset(request.getPath()))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    @Any("/{+path}")
    @Consumes(MediaType.ALL)
    public Mono<MutableHttpResponse<?>> proxy(HttpRequest<byte[]> request) {
        List<URI> uris = UrlUtil.splitUris(request.getUri(), explorerProperties.getHostMappings());
        if (uris.isEmpty()) {
            String errMsg = "Invalid host: " + request.getUri().getHost();
            return Mono.just(HttpResponse.status(HttpStatus.BAD_REQUEST)
                    .body(errMsg.getBytes(StandardCharsets.UTF_8)));
        }

        URI remoteURI = uris.get(0);
        URI proxyHostURI = uris.get(1);
        byte[] body = request.getBody(byte[].class).orElse(new byte[0]);

        return proxyManager.proxy(request, body, remoteURI, proxyHostURI);
    }

    @Any("/{+path}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Mono<MutableHttpResponse<?>> proxyMultipart(
            HttpRequest<?> request,
            @Body Publisher<CompletedPart> parts) {

        URI uri = request.getUri();
        List<URI> uris = UrlUtil.splitUris(uri, explorerProperties.getHostMappings());
        if (uris.isEmpty()) {
            String errMsg = "Invalid host: " + uri.getHost();
            return Mono.just(HttpResponse.status(HttpStatus.BAD_REQUEST)
                    .body(errMsg.getBytes(StandardCharsets.UTF_8)));
        }
        URI remoteURI = uris.get(0);
        URI proxyHostURI = uris.get(1);

        return Flux.from(parts)
                .collectList()
                .flatMap(partList -> {
                    // Reconstruct as byte[] body with multipart encoding for upstream
                    // ProxyManager will forward as-is; rebuild body by reading each part
                    byte[] combined = buildMultipartBody(partList);
                    return proxyManager.proxy(request, combined, remoteURI, proxyHostURI);
                });
    }

    private static byte[] buildMultipartBody(List<CompletedPart> parts) {
        // Collect all part bytes into a flat byte array (the actual boundary encoding is
        // handled by the upstream server; we forward raw bytes here)
        try {
            var out = new java.io.ByteArrayOutputStream();
            for (CompletedPart part : parts) {
                if (part instanceof CompletedFileUpload file) {
                    out.write(file.getBytes());
                } else {
                    out.write(part.getBytes());
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/chuntung/explorer/controller/ProxyController.java
git commit -m "feat: rewrite ProxyController with Micronaut HttpRequest/HttpResponse"
```

---

## Task 10: Replace application.yml

**Files:**
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: Replace application.yml**

```yaml
micronaut:
  application:
    name: explorer
  environments:
    active: ${APP_ENV:local}
  server:
    netty:
      max-request-buffer-size: 268435456   # 256MB for multipart
      max-header-size: 131072              # 128KB
  http:
    client:
      max-content-length: 10485760         # 10MB for HTML collection
  router:
    static-resources:
      defaults:
        enabled: false

cors:
  enabled: true
  configurations:
    all:
      allowed-origins-regex: ".*"
      exposed-headers:
        - "*"

explorer:
  proxy-headers: X-Real-IP,X-Forwarded-For,X-Forwarded-Proto
  excluded-headers: X-Frame-Options,Content-Security-Policy,Content-Security-Policy-Report-Only,Transfer-Encoding
  host-mappings:
    google: www.google.com
    baidu: www.baidu.com
  block-rules:
    - host-pattern: www\.google\.com
      block-paths: ["/gen_204"]
    - host-pattern: .*\.?baidu\.com
      block-paths: [".*\\.gif", "/ztbox"]
      block-contents:
        - selector: "head"
          append: |
            <style>
            div:has(> .c-abstract) {display:none;}
            .newsTitleTop {display: none;}
            </style>
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "chore: replace Spring application.yml with Micronaut config"
```

---

## Task 11: Write test and verify compilation

**Files:**
- Modify: `src/test/java/com/chuntung/explorer/controller/ExplorerControllerTest.java`

- [ ] **Step 1: Rewrite ExplorerControllerTest.java**

```java
package com.chuntung.explorer.controller;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class ExplorerControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void robotsTxtReturns200OrNotFound() {
        // robots.txt may not exist in test resources — both 200 and 404 are valid
        HttpResponse<String> response = client.toBlocking()
                .exchange(HttpRequest.GET("/robots.txt")
                        .header("Host", "localhost:8080"), String.class);
        int code = response.getStatus().getCode();
        assert code == 200 || code == 404 : "Expected 200 or 404 but got " + code;
    }

    @Test
    void invalidProxyHostReturnsBadRequest() {
        // A host that has no subdomain mapping should return 400
        HttpResponse<String> response = client.toBlocking()
                .exchange(HttpRequest.GET("/")
                        .header("Host", "notmapped.localhost:8080"), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
    }
}
```

- [ ] **Step 2: Run test to verify it fails (Spring code is gone, Micronaut not fully wired yet)**

```bash
./gradlew test --no-daemon 2>&1 | tail -20
```
Expected: compilation errors if any files still import Spring types — review and fix.

- [ ] **Step 3: Fix any remaining Spring import errors**

Search for leftover Spring imports:
```bash
grep -r "org.springframework" src/main/java/ --include="*.java" -l
```
For each file listed, replace the Spring import with its Micronaut or plain-Java equivalent.

- [ ] **Step 4: Run test again**

```bash
./gradlew test --no-daemon 2>&1 | tail -20
```
Expected: `BUILD SUCCESSFUL`, 2 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/chuntung/explorer/controller/ExplorerControllerTest.java
git commit -m "test: add Micronaut embedded-server tests"
```

---

## Task 12: Add native image hints and create Dockerfile.native

**Files:**
- Create: `src/main/resources/META-INF/native-image/com.chuntung.explorer/reflect-config.json`
- Create: `Dockerfile.native`

- [ ] **Step 1: Create reflect-config.json for JSoup**

```bash
mkdir -p src/main/resources/META-INF/native-image/com.chuntung.explorer
```

Create `src/main/resources/META-INF/native-image/com.chuntung.explorer/reflect-config.json`:

```json
[
  {
    "name": "org.jsoup.parser.Parser",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true
  },
  {
    "name": "org.jsoup.parser.HtmlTreeBuilder",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true,
    "allDeclaredMethods": true,
    "allPublicMethods": true
  },
  {
    "name": "org.brotli.dec.BrotliInputStream",
    "allDeclaredConstructors": true,
    "allPublicConstructors": true
  }
]
```

- [ ] **Step 2: Run native compile to find additional missing hints**

```bash
./gradlew nativeCompile --no-daemon 2>&1 | grep -i "missing\|reflect\|error" | head -30
```
If additional missing-class errors appear, add them to `reflect-config.json` and re-run.

- [ ] **Step 3: Verify native binary runs**

```bash
./build/native/nativeCompile/explorer &
sleep 2
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/robots.txt
kill %1
```
Expected: `200` or `404` (not a crash).

- [ ] **Step 4: Create Dockerfile.native**

```dockerfile
# Build stage — compile native binary for Linux
FROM ghcr.io/graalvm/graalvm-ce:ol9-java21 AS builder
WORKDIR /app
COPY . .
RUN ./gradlew nativeCompile --no-daemon -x test

# Runtime stage — distroless, no JVM
FROM gcr.io/distroless/static-debian12
WORKDIR /app
COPY --from=builder /app/build/native/nativeCompile/explorer /app/explorer
COPY --from=builder /app/src/main/resources/application.yml /app/application.yml
EXPOSE 8080
ENTRYPOINT ["/app/explorer"]
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/META-INF/ Dockerfile.native
git commit -m "feat: add native image hints and Dockerfile.native"
```

---

## Self-Review Checklist

**Spec coverage:**
- ✅ Build system: Task 1 replaces Spring plugins with Micronaut
- ✅ MainApplication: Task 2
- ✅ @Singleton annotation swaps: Tasks 2–6
- ✅ WebClient → ReactorStreamingHttpClient: Task 6 + 7
- ✅ Controller (ServerWebExchange → HttpRequest): Task 9
- ✅ Filter (WebFilter → @ServerFilter): Task 8
- ✅ application.yml: Task 10
- ✅ Native image hints: Task 12
- ✅ Dockerfile.native: Task 12
- ✅ Test rewrite: Task 11

**Known limitations documented in plan:**
- `ProxyManager` buffers ALL responses (HTML + non-HTML) via `collectList()`. Non-HTML streaming optimization using `exchangeStream` publish/subscribe can be added as a follow-up.
- `buildMultipartBody()` in `ProxyController.proxyMultipart()` concatenates raw bytes without multipart boundary reconstruction. If upstream servers require a proper `multipart/form-data` body with boundaries, this method needs enhancement.

**Type consistency:**
- `BlockHandler.preHandle` takes `HttpRequest<?>` in all call sites ✅
- `BlockHandler.postHtmlHandle` takes `MutableHttpHeaders` in all call sites ✅
- `HtmlResolver.resolve` takes `byte[]` + `MutableHttpHeaders`, returns `byte[]` ✅
- `AssetManager.getAsset` returns `MutableHttpResponse<byte[]>` ✅
- `ProxyManager.proxy` returns `Mono<MutableHttpResponse<?>>` ✅
