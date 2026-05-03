package com.chuntung.explorer.filter;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.manager.AssetManager;
import com.chuntung.explorer.manager.ProxyManager;
import com.chuntung.explorer.util.UrlUtil;
import io.micronaut.http.*;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.FilterContinuation;
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
