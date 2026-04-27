package com.chuntung.explorer.manager;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.util.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
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

    /**
     * Proxy the request and write the response directly to {@code serverResponse}.
     * <p>
     * HTML responses are collected, modified by {@link HtmlResolver}, then written.
     * All other responses are piped as a {@code Flux<DataBuffer>} — no intermediate buffering.
     */
    public Mono<Void> proxy(ServerHttpResponse serverResponse, RequestEntity<?> request, URI remoteURI, URI proxyURI) {
        HttpHeaders requestHeaders = copyAndTrimHeaders(request.getHeaders(), explorerProperties.getProxyHeaders(), proxyURI);

        // replace host with remote host
        for (String x : explorerProperties.getTransformHeaders()) {
            if (requestHeaders.containsHeader(x)) {
                if (x.equalsIgnoreCase("Host")) {
                    requestHeaders.set(x, remoteURI.getHost());
                    continue;
                }
                String url = requestHeaders.getFirst(x);
                if (url != null && url.contains("//") && url.contains(proxyURI.getHost())) {
                    List<URI> uris = UrlUtil.splitUris(UrlUtil.toURI(url), explorerProperties.getHostMappings());
                    if (!uris.isEmpty()) {
                        requestHeaders.set(x, uris.get(0).toString());
                    }
                }
            }
        }

        RequestEntity<?> requestCopy = new RequestEntity<>(
                request.getBody(), requestHeaders, request.getMethod(), remoteURI, request.getType());

        // preHandle block ads
        if (!adBlocker.preHandle(remoteURI, requestCopy)) {
            logger.debug("Blocked request: {}", remoteURI);
            serverResponse.setStatusCode(HttpStatus.NO_CONTENT);
            return serverResponse.setComplete();
        }

        Set<String> excludedHeaders = getExcludedHeaders(requestHeaders.getOrigin() != null);

        WebClient.RequestBodySpec requestBodySpec = webClient.method(request.getMethod())
                .uri(remoteURI)
                .headers(h -> h.addAll(requestHeaders));

        URI requestUrl = request.getUrl();

        return buildBody(requestBodySpec, request.getBody())
                .exchangeToMono(clientResponse -> {
                    HttpStatusCode statusCode = clientResponse.statusCode();
                    HttpHeaders responseHeaders = copyAndTrimHeaders(
                            clientResponse.headers().asHttpHeaders(), excludedHeaders, proxyURI);

                    // handle redirect — let the browser redirect to the converted URL
                    if (statusCode.is3xxRedirection() && responseHeaders.getLocation() != null) {
                        String destUrl = UrlUtil.proxyUrl(responseHeaders.getLocation().toString(), proxyURI);
                        responseHeaders.set("Location", destUrl);
                        applyToResponse(serverResponse, statusCode, responseHeaders);
                        return serverResponse.writeWith(clientResponse.bodyToFlux(DataBuffer.class));
                    }

                    // resolve HTML
                    MediaType contentType = responseHeaders.getContentType();
                    if (contentType != null && contentType.isCompatibleWith(MediaType.TEXT_HTML)) {
                        ExplorerSetting setting = new ExplorerSetting(
                                requestUrl != null ? requestUrl.getRawQuery() : null);
                        return clientResponse.bodyToMono(byte[].class)
                                .defaultIfEmpty(new byte[0])
                                .flatMap(bytes -> Mono.fromCallable(() ->
                                        htmlResolver.resolve(new ByteArrayResource(bytes),
                                                responseHeaders, remoteURI, proxyURI, setting)
                                ).subscribeOn(Schedulers.boundedElastic()))
                                .flatMap(resolved -> {
                                    // htmlResolver may have modified responseHeaders (Content-Encoding, Content-Length)
                                    applyToResponse(serverResponse, statusCode, responseHeaders);
                                    return Mono.fromCallable(() -> resolved.getInputStream().readAllBytes())
                                            .subscribeOn(Schedulers.boundedElastic())
                                            .flatMap(b -> serverResponse.writeWith(
                                                    Mono.just(serverResponse.bufferFactory().wrap(b))));
                                });
                    }

                    // stream non-HTML body directly — no buffering
                    applyToResponse(serverResponse, statusCode, responseHeaders);
                    Flux<DataBuffer> bodyFlux = clientResponse.bodyToFlux(DataBuffer.class);
                    return serverResponse.writeWith(bodyFlux);
                })
                .onErrorResume(e -> {
                    logger.warn("Failed to handle request: {}", remoteURI, e);
                    serverResponse.setStatusCode(HttpStatus.BAD_GATEWAY);
                    String msg = e.getMessage() != null ? e.getMessage() : "Bad Gateway";
                    return serverResponse.writeWith(
                            Mono.just(serverResponse.bufferFactory().wrap(msg.getBytes(StandardCharsets.UTF_8))));
                });
    }

    private void applyToResponse(ServerHttpResponse response, HttpStatusCode statusCode, HttpHeaders headers) {
        response.setStatusCode(statusCode);
        headers.forEach((k, v) -> {
            if (!k.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)) {
                response.getHeaders().put(k, v);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private WebClient.RequestHeadersSpec<?> buildBody(WebClient.RequestBodySpec spec, Object body) {
        if (body == null) {
            return spec;
        }
        if (body instanceof byte[] bytes) {
            return bytes.length > 0 ? spec.bodyValue(bytes) : spec;
        }
        if (body instanceof ByteArrayResource resource) {
            byte[] bytes = resource.getByteArray();
            return bytes.length > 0 ? spec.bodyValue(bytes) : spec;
        }
        if (body instanceof MultiValueMap<?, ?> multiValueMap) {
            return spec.body(BodyInserters.fromMultipartData((MultiValueMap<String, ?>) multiValueMap));
        }
        return spec.bodyValue(body);
    }

    private Set<String> getExcludedHeaders(boolean cors) {
        Set<String> excludedHeaders = new HashSet<>();
        if (explorerProperties.getExcludedHeaders() != null) {
            excludedHeaders.addAll(explorerProperties.getExcludedHeaders());
        }
        // Exclude CORS headers from remote server — handled by Spring WebFlux
        if (cors) {
            excludedHeaders.add("Access-Control-Allow-Origin");
            excludedHeaders.add("Access-Control-Allow-Credentials");
            excludedHeaders.add("Access-Control-Allow-Methods");
            excludedHeaders.add("Access-Control-Allow-Headers");
        }
        return excludedHeaders;
    }

    private HttpHeaders copyAndTrimHeaders(HttpHeaders source, Set<String> ignoredHeaders, URI proxyUri) {
        HttpHeaders mutable = new HttpHeaders();
        mutable.addAll(source);
        if (ignoredHeaders != null) {
            ignoredHeaders.forEach(mutable::remove);
        }
        List<String> auth = mutable.get("www-authenticate");
        // todo rewrite response url
        // Bearer realm="https://auth.docker.io/token",service="registry.docker.io"
        if (auth != null) {
            String bearer = auth.get(0);
            if (bearer.startsWith("Bearer") && bearer.contains("https://auth.docker.io/token")) {
                String proxiedUrl = UrlUtil.proxyUrl("https://auth.docker.io/token", proxyUri);
                mutable.set("www-authenticate", bearer.replace("https://auth.docker.io/token", proxiedUrl));
            }
        }
        return mutable;
    }
}
