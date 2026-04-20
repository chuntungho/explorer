package com.chuntung.explorer.manager;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.util.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class ProxyManager {
    private static final Logger logger = LoggerFactory.getLogger(ProxyManager.class);
    private final ExplorerProperties explorerProperties;
    private final BlockManager adBlocker;
    private final WebClient webClient;
    private final HtmlResolver htmlResolver;

    public ProxyManager(ExplorerProperties explorerProperties, BlockManager adBlocker, WebClient webClient, HtmlResolver htmlResolver) {
        this.explorerProperties = explorerProperties;
        this.adBlocker = adBlocker;
        this.webClient = webClient;
        this.htmlResolver = htmlResolver;
    }

    public Mono<ServerResponse> proxy(ServerRequest request, URI remoteURI, URI proxyURI) {
        HttpHeaders requestHeaders = trimHeaders(request.headers().asHttpHeaders(), explorerProperties.getProxyHeaders(), proxyURI);

        // replace host with remote host
        for (String x : explorerProperties.getTransformHeaders()) {
            if (requestHeaders.containsHeader(x)) {
                if (x.equalsIgnoreCase("Host")) {
                    requestHeaders.set(x, remoteURI.getHost());
                    continue;
                }
                String url = requestHeaders.getFirst(x);
                if (url != null && url.contains("//") & url.contains(proxyURI.getHost())) {
                    List<URI> uris = UrlUtil.splitUris(UrlUtil.toURI(url), explorerProperties.getHostMappings());
                    if (!uris.isEmpty()) {
                        requestHeaders.set(x, uris.getFirst().toString());
                    }
                }
            }
        }

        // preHandle block ads
        if (!adBlocker.preHandle(remoteURI, request.headers().asHttpHeaders())) {
            logger.debug("Blocked request: {}", remoteURI);
            return ServerResponse.noContent().build();
        }

        Set<String> excludedHeaders = getExcludedHeaders(requestHeaders.getOrigin() != null);
        return webClient.method(request.method())
                .uri(remoteURI)
                .headers(httpHeaders -> httpHeaders.addAll(requestHeaders))
                .body(BodyInserters.fromPublisher(request.body(BodyExtractors.toDataBuffers()),
                        org.springframework.core.io.buffer.DataBuffer.class))
                .exchangeToMono(clientResponse -> {
                    // handle redirect, just let browser redirect to converted url as the html may contain relative paths
                    if (clientResponse.statusCode().is3xxRedirection() && clientResponse.headers().asHttpHeaders().getLocation() != null) {
                        String destUrl = UrlUtil.proxyUrl(Objects.requireNonNull(clientResponse.headers().asHttpHeaders().getLocation()).toString(), proxyURI);
                        return ServerResponse.status(clientResponse.statusCode())
                                .headers(httpHeaders -> httpHeaders.addAll(trimHeaders(clientResponse.headers().asHttpHeaders(), excludedHeaders, proxyURI)))
                                .header("Location", destUrl)
                                .build();
                    }

                    // resolve html
                    MediaType contentType = clientResponse.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM);
                    if (contentType.isCompatibleWith(MediaType.TEXT_HTML)) {
                        ExplorerSetting setting = new ExplorerSetting(request.uri().getRawQuery());
                        return clientResponse.bodyToMono(Resource.class)
                                .flatMap(resource -> htmlResolver.resolve(resource, clientResponse.headers().asHttpHeaders(), remoteURI, proxyURI, setting))
                                .flatMap(resolvedResource -> ServerResponse.status(clientResponse.statusCode())
                                        .headers(httpHeaders -> httpHeaders.addAll(trimHeaders(clientResponse.headers().asHttpHeaders(), excludedHeaders, proxyURI)))
                                        .bodyValue(resolvedResource));
                    } else {
                        return ServerResponse.status(clientResponse.statusCode())
                                .headers(httpHeaders -> httpHeaders.addAll(trimHeaders(clientResponse.headers().asHttpHeaders(), excludedHeaders, proxyURI)))
                                .body(BodyInserters.fromPublisher(clientResponse.body(BodyExtractors.toDataBuffers()),
                                        org.springframework.core.io.buffer.DataBuffer.class));
                    }
                })
                .onErrorResume(e -> {
                    logger.warn("Failed to handle request: {}", remoteURI, e);
                    return ServerResponse.status(HttpStatus.BAD_GATEWAY).bodyValue(e.getMessage());
                });
    }

    private Set<String> getExcludedHeaders(Boolean cors) {
        Set<String> excludedHeaders = new HashSet<>();
        if (explorerProperties.getExcludedHeaders() != null) {
            excludedHeaders.addAll(explorerProperties.getExcludedHeaders());
        }
        // Exclude CORS (Cross-origin resource sharing) headers from remote server as it is handled by spring mvc
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Access-Control-Allow-Credentials
        if (cors) {
            excludedHeaders.add("Access-Control-Allow-Origin");
            excludedHeaders.add("Access-Control-Allow-Credentials");
            excludedHeaders.add("Access-Control-Allow-Methods");
            excludedHeaders.add("Access-Control-Allow-Headers");
        }
        return excludedHeaders;
    }

    private HttpHeaders trimHeaders(HttpHeaders immutableHeaders, Set<String> ignoredHeaders, URI proxyUri) {
        HttpHeaders mutable = new HttpHeaders();
        immutableHeaders.forEach((key, value) -> {
            if (!ignoredHeaders.contains(key)) {
                mutable.put(key, value);
            }
        });

        List<String> auth = mutable.get("www-authenticate");
        // todo rewrite response url
        // Bearer realm="https://auth.docker.io/token",service="registry.docker.io"
        if (auth != null) {
            String bearer = auth.getFirst();
            if (bearer.startsWith("Bearer") && bearer.contains("https://auth.docker.io/token")) {
                String proxiedUrl = UrlUtil.proxyUrl("https://auth.docker.io/token", proxyUri);
                String replaced = bearer.replace("https://auth.docker.io/token", proxiedUrl);
                mutable.set("www-authenticate", replaced);
            }
        }
        return mutable;
    }
}
