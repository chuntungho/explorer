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
                    byte[] combined = buildMultipartBody(partList);
                    return proxyManager.proxy(request, combined, remoteURI, proxyHostURI);
                });
    }

    private static byte[] buildMultipartBody(List<CompletedPart> parts) {
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
