package com.chuntung.explorer.manager;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.util.UrlUtil;
import io.micronaut.http.*;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import io.micronaut.core.io.buffer.ByteBuffer;

import java.net.URI;
import java.net.UnknownHostException;
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
            URI remoteURI,
            URI proxyURI) {

        MutableHttpHeaders requestHeaders = buildRequestHeaders(incomingRequest.getHeaders(), remoteURI, proxyURI);

        MutableHttpRequest<Object> outRequest = HttpRequest.create(incomingRequest.getMethod(), remoteURI.toString());
        requestHeaders.forEach((k, vals) -> vals.forEach(v -> outRequest.getHeaders().add(k, v)));
        incomingRequest.getBody().ifPresent(b -> {
            if (b instanceof byte[] bytes) {
                if (bytes.length > 0) outRequest.body(bytes);
            } else {
                outRequest.body(b);
            }
        });

        if (!adBlocker.preHandle(remoteURI, outRequest)) {
            logger.debug("Blocked request: {}", remoteURI);
            return Mono.just(HttpResponse.status(HttpStatus.NO_CONTENT));
        }

        Set<String> excludedHeaders = getExcludedHeaders(incomingRequest.getHeaders().getOrigin() != null);

        // Mono.create + Sinks decouples the response emission from the body stream lifetime.
        // switchOnFirst + next() would cancel the upstream before Micronaut subscribes to the
        // body Publisher, so instead we drive the exchange subscription manually and emit the
        // response via the MonoSink; for non-HTML, the body is piped through a Sinks.Many so
        // it outlives the switchOnFirst chain.
        return Mono.create(emitter -> {
            Disposable sub = Flux.from(httpClient.exchangeStream(outRequest))
                    .<Object>switchOnFirst((signal, remainingFlux) -> {
                        if (!signal.hasValue()) {
                            emitter.success(HttpResponse.noContent());
                            return remainingFlux.thenMany(Flux.empty());
                        }

                        HttpResponse<ByteBuffer<?>> first = signal.get();
                        HttpStatus status = HttpStatus.valueOf(first.getStatus().getCode());
                        HttpHeaders remoteHeaders = first.getHeaders();
                        MutableHttpHeaders responseHeaders = buildResponseHeaders(remoteHeaders, excludedHeaders, proxyURI);

                        Flux<byte[]> chunkBytes = remainingFlux
                                .map(r -> r.getBody().map(bb -> bb.toByteArray()).orElse(new byte[0]));

                        if (status.getCode() >= 300 && status.getCode() < 400) {
                            return chunkBytes.collectList()
                                    .doOnNext(parts -> {
                                        String location = remoteHeaders.get("Location");
                                        if (location != null)
                                            responseHeaders.set("Location", UrlUtil.proxyUrl(location, proxyURI));
                                        MutableHttpResponse<byte[]> response = HttpResponse.status(status, "").body(mergeBytes(parts));
                                        copyHeaders(responseHeaders, response);
                                        emitter.success(response);
                                    })
                                    .then();
                        }

                        String contentType = remoteHeaders.get("Content-Type");
                        boolean isHtml = contentType != null
                                && contentType.contains("text/") && contentType.contains("html");

                        if (isHtml) {
                            ExplorerSetting setting = new ExplorerSetting(incomingRequest.getUri().getRawQuery());
                            return chunkBytes.collectList()
                                    .flatMap(parts -> Mono.fromCallable(() ->
                                            htmlResolver.resolve(mergeBytes(parts), responseHeaders, remoteURI, proxyURI, setting))
                                            .subscribeOn(Schedulers.boundedElastic()))
                                    .doOnNext(resolved -> {
                                        MutableHttpResponse<byte[]> response = HttpResponse.status(status, "").body(resolved);
                                        copyHeaders(responseHeaders, response);
                                        emitter.success(response);
                                    })
                                    .then();
                        }

                        // Non-HTML: emit response immediately with a Sinks.Many as the body.
                        // The sink is fed by the remainder of the exchange stream, which stays
                        // alive because we drive it through our own subscribe() below.
                        Sinks.Many<byte[]> bodySink = Sinks.many().unicast().onBackpressureBuffer();
                        MutableHttpResponse<Publisher<byte[]>> response =
                                HttpResponse.status(status, "").body(bodySink.asFlux());
                        copyHeaders(responseHeaders, response);
                        emitter.success(response);

                        return chunkBytes
                                .filter(b -> b.length > 0)
                                .doOnNext(bytes -> bodySink.tryEmitNext(bytes))
                                .doOnComplete(() -> bodySink.tryEmitComplete())
                                .doOnError(e -> bodySink.tryEmitError(e))
                                .then();
                    })
                    .subscribe(
                            __ -> {},
                            error -> {
                                Throwable cause = error.getCause() != null ? error.getCause() : error;
                                if (isConnectivityError(cause)) {
                                    logger.debug("Connection failed for: {} ({})", remoteURI, cause.getClass().getSimpleName());
                                } else {
                                    logger.warn("Failed to handle request: {}", remoteURI, error);
                                }
                                byte[] msg = (error.getMessage() != null ? error.getMessage() : "Bad Gateway")
                                        .getBytes(StandardCharsets.UTF_8);
                                emitter.success(HttpResponse.status(HttpStatus.BAD_GATEWAY, "").body(msg));
                            }
                    );

            emitter.onCancel(sub::dispose);
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

    private static byte[] mergeBytes(List<byte[]> parts) {
        int total = 0;
        for (byte[] part : parts) total += part.length;
        byte[] combined = new byte[total];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, combined, offset, part.length);
            offset += part.length;
        }
        return combined;
    }

    private static boolean isConnectivityError(Throwable cause) {
        if (cause instanceof UnknownHostException
                || cause instanceof java.net.ConnectException
                || cause instanceof java.nio.channels.ClosedChannelException) {
            return true;
        }
        String name = cause.getClass().getName();
        return name.contains("SslHandshake") || name.contains("ProxyConnectException");
    }

    private static void copyHeaders(MutableHttpHeaders src, MutableHttpResponse<?> response) {
        src.forEach((k, vals) -> vals.forEach(v -> response.getHeaders().add(k, v)));
    }
}
