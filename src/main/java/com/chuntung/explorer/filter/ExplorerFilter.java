package com.chuntung.explorer.filter;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.manager.AssetManager;
import com.chuntung.explorer.manager.ProxyManager;
import com.chuntung.explorer.util.UrlUtil;
import io.micronaut.http.*;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    public Mono<MutableHttpResponse<?>> filter(HttpRequest<?> request) {

        String hostHeader = request.getHeaders().get("Host");

        if (Objects.equals(explorerProperties.getHost(), hostHeader)) {
            return handleExplorerHost(request);
        }

        // Serve robots.txt for all proxy hosts to prevent crawling
        if ("/robots.txt".equals(request.getPath())) {
            return Mono.<MutableHttpResponse<?>>fromCallable(() -> assetManager.getAsset("/robots.txt"))
                    .subscribeOn(Schedulers.boundedElastic());
        }

        // Subdomain-based proxy routing — build URI from Host header so host is always set
        URI routingUri;
        try {
            String rawPath = request.getUri().getRawPath();
            String rawQuery = request.getUri().getRawQuery();
            routingUri = URI.create("http://" + hostHeader
                    + (rawPath != null ? rawPath : "/")
                    + (rawQuery != null ? "?" + rawQuery : ""));
        } catch (IllegalArgumentException e) {
            return Mono.just(HttpResponse.status(HttpStatus.BAD_REQUEST)
                    .body(("Invalid host: " + hostHeader).getBytes(StandardCharsets.UTF_8)));
        }

        List<URI> uris = UrlUtil.splitUris(routingUri, explorerProperties.getHostMappings());
        if (uris.isEmpty()) {
            String errMsg = "Invalid host: " + hostHeader;
            return Mono.just(HttpResponse.status(HttpStatus.BAD_REQUEST)
                    .body(errMsg.getBytes(StandardCharsets.UTF_8)));
        }

        byte[] body = request.getBody(byte[].class).orElse(new byte[0]);
        return proxyManager.proxy(request, body, uris.get(0), uris.get(1));
    }

    private Mono<MutableHttpResponse<?>> handleExplorerHost(HttpRequest<?> request) {
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
        return Mono.<MutableHttpResponse<?>>fromCallable(() -> assetManager.getAsset(
                        Objects.equals("/", path) ? "/browser.html" : path))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
