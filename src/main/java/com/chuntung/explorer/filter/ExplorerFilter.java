package com.chuntung.explorer.filter;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.manager.AssetManager;
import com.chuntung.explorer.manager.ProxyManager;
import com.chuntung.explorer.util.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Component
@Order(1)
public class ExplorerFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(ExplorerFilter.class);

    private final ExplorerProperties explorerProperties;
    private final ProxyManager proxyManager;
    private final AssetManager assetManager;

    public ExplorerFilter(ExplorerProperties explorerProperties, ProxyManager proxyManager, AssetManager assetManager) {
        this.explorerProperties = explorerProperties;
        this.proxyManager = proxyManager;
        this.assetManager = assetManager;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String hostHeader = request.getHeaders().getFirst("Host");

        if (!Objects.equals(explorerProperties.getHost(), hostHeader)) {
            return chain.filter(exchange);
        }

        String url = request.getQueryParams().getFirst("url");
        String direct = request.getQueryParams().getFirst("direct");
        ServerHttpResponse response = exchange.getResponse();

        if (StringUtils.hasLength(url)) {
            try {
                URI requestUri = request.getURI();
                URI proxyURI = requestUri;
                if (StringUtils.hasLength(explorerProperties.getWildcardHost())) {
                    proxyURI = new URI(requestUri.getScheme() + "://" + explorerProperties.getWildcardHost());
                }
                final URI finalProxyURI = proxyURI;

                if ("true".equalsIgnoreCase(direct)) {
                    // direct proxy — stream response via ProxyManager
                    return DataBufferUtils.join(request.getBody())
                            .map(dataBuffer -> {
                                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(bytes);
                                DataBufferUtils.release(dataBuffer);
                                return bytes;
                            })
                            .defaultIfEmpty(new byte[0])
                            .flatMap(body -> {
                                RequestEntity<byte[]> requestEntity = new RequestEntity<>(
                                        body.length > 0 ? body : null,
                                        request.getHeaders(),
                                        request.getMethod(),
                                        requestUri);
                                return proxyManager.proxy(response, requestEntity, UrlUtil.toURI(url), finalProxyURI);
                            });
                } else {
                    // redirect for html
                    URI proxyUrl = UrlUtil.toURI(UrlUtil.proxyUrl(url, proxyURI));
                    response.setStatusCode(HttpStatus.FOUND);
                    response.getHeaders().setLocation(proxyUrl);
                    return response.setComplete();
                }
            } catch (URISyntaxException e) {
                logger.warn("Failed to proxy url: {}", url, e);
                response.setStatusCode(HttpStatus.BAD_REQUEST);
                return response.writeWith(Mono.just(
                        response.bufferFactory().wrap("Failed to fetch url".getBytes(StandardCharsets.UTF_8))));
            }
        } else {
            // serve static asset
            String path = request.getPath().value();
            return Mono.fromCallable(() -> assetManager.getAsset(Objects.equals("/", path) ? "/browser.html" : path))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(responseEntity -> writeAsset(response, responseEntity));
        }
    }

    private Mono<Void> writeAsset(ServerHttpResponse response, ResponseEntity<?> responseEntity) {
        response.setStatusCode(responseEntity.getStatusCode());
        responseEntity.getHeaders().forEach((k, v) -> {
            if (!k.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)) {
                response.getHeaders().put(k, v);
            }
        });

        if (responseEntity.getBody() instanceof Resource resource) {
            return Mono.fromCallable(() -> resource.getInputStream().readAllBytes())
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(bytes -> response.writeWith(
                            Mono.just(response.bufferFactory().wrap(bytes))));
        } else if (responseEntity.getBody() != null) {
            byte[] bytes = responseEntity.getBody().toString().getBytes(StandardCharsets.UTF_8);
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        }
        return response.setComplete();
    }
}
