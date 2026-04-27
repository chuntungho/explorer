package com.chuntung.explorer.controller;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.manager.AssetManager;
import com.chuntung.explorer.manager.ProxyManager;
import com.chuntung.explorer.util.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.*;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;

@Controller
@CrossOrigin(originPatterns = "*", exposedHeaders = "*", allowCredentials = "true")
public class ProxyController {
    private static final Logger logger = LoggerFactory.getLogger(ProxyController.class);
    private final ExplorerProperties explorerProperties;
    private final AssetManager assetManager;
    private final ProxyManager proxyManager;

    public ProxyController(ExplorerProperties explorerProperties, AssetManager assetManager, ProxyManager proxyManager) {
        this.explorerProperties = explorerProperties;
        this.assetManager = assetManager;
        this.proxyManager = proxyManager;
    }

    /**
     * Proxy http request by parsing subdomain.
     *
     * <p>
     * e.g. {@code https://www-google-com.localhost/search?w=xxx}
     * will be parsed to {@code https://www.google.com/search?w=xxx}
     * </p>
     */
    @RequestMapping(value = "/**", consumes = MediaType.ALL_VALUE)
    public Mono<Void> proxy(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        List<URI> uris = UrlUtil.splitUris(request.getURI(), explorerProperties.getHostMappings());
        if (uris.isEmpty()) {
            String errMsg = "Invalid host: " + request.getURI().getHost();
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory()
                            .wrap(errMsg.getBytes(StandardCharsets.UTF_8))));
        }

        URI remoteURI = uris.get(0);
        URI proxyHostURI = uris.get(1);

        return DataBufferUtils.join(request.getBody())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0])
                .flatMap(bytes -> {
                    RequestEntity<ByteArrayResource> requestEntity = new RequestEntity<>(
                            bytes.length > 0 ? new ByteArrayResource(bytes) : null,
                            request.getHeaders(),
                            request.getMethod(),
                            request.getURI());
                    return proxyManager.proxy(exchange.getResponse(), requestEntity, remoteURI, proxyHostURI);
                });
    }

    @RequestMapping(value = "/**", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Void> proxyMultipart(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        URI uri = request.getURI();

        List<URI> uris = UrlUtil.splitUris(uri, explorerProperties.getHostMappings());
        if (uris.isEmpty()) {
            String errMsg = "Invalid host: " + uri.getHost();
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory()
                            .wrap(errMsg.getBytes(StandardCharsets.UTF_8))));
        }
        URI remoteURI = uris.get(0);
        URI proxyHostURI = uris.get(1);

        return exchange.getMultipartData().flatMap(multipartData -> {
            List<Mono<AbstractMap.SimpleEntry<String, Resource>>> entryMonos = new ArrayList<>();

            multipartData.forEach((key, parts) -> {
                for (Part part : parts) {
                    Mono<AbstractMap.SimpleEntry<String, Resource>> entryMono;
                    if (part instanceof FormFieldPart formField) {
                        byte[] bytes = formField.value().getBytes(StandardCharsets.UTF_8);
                        entryMono = Mono.just(new AbstractMap.SimpleEntry<>(key,
                                (Resource) new ByteArrayResource(bytes)));
                    } else if (part instanceof FilePart filePart) {
                        entryMono = DataBufferUtils.join(filePart.content())
                                .map(db -> {
                                    byte[] bytes = new byte[db.readableByteCount()];
                                    db.read(bytes);
                                    DataBufferUtils.release(db);
                                    return bytes;
                                })
                                .defaultIfEmpty(new byte[0])
                                .map(bytes -> new AbstractMap.SimpleEntry<>(key, (Resource) new ByteArrayResource(bytes) {
                                    @Override
                                    public String getFilename() {
                                        return filePart.filename();
                                    }
                                }));
                    } else {
                        entryMono = DataBufferUtils.join(part.content())
                                .map(db -> {
                                    byte[] bytes = new byte[db.readableByteCount()];
                                    db.read(bytes);
                                    DataBufferUtils.release(db);
                                    return bytes;
                                })
                                .defaultIfEmpty(new byte[0])
                                .map(bytes -> new AbstractMap.SimpleEntry<>(key,
                                        (Resource) new ByteArrayResource(bytes)));
                    }
                    entryMonos.add(entryMono);
                }
            });

            return Flux.fromIterable(entryMonos)
                    .flatMap(m -> m)
                    .collectList()
                    .flatMap(entries -> {
                        MultiValueMap<String, Resource> requestBody = new LinkedMultiValueMap<>();
                        entries.forEach(e -> requestBody.add(e.getKey(), e.getValue()));

                        // remove content headers — WebClient sets new Content-Type with boundary
                        HttpHeaders requestHeaders = new HttpHeaders();
                        requestHeaders.addAll(request.getHeaders());
                        requestHeaders.remove(HttpHeaders.CONTENT_LENGTH);
                        requestHeaders.remove(HttpHeaders.CONTENT_TYPE);

                        RequestEntity<MultiValueMap<String, Resource>> requestEntity = new RequestEntity<>(
                                requestBody, requestHeaders, request.getMethod(), uri);
                        return proxyManager.proxy(exchange.getResponse(), requestEntity, remoteURI, proxyHostURI);
                    });
        });
    }

    @GetMapping(value = "/robots.txt")
    public Mono<ResponseEntity<?>> robots(ServerHttpRequest request) {
        return Mono.fromCallable(() -> assetManager.getAsset(request.getURI().getPath()));
    }
}
