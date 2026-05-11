package com.chuntung.explorer.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Component
@Order(-1)
public class RequestInfoFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestInfoFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // CF-Connecting-IP is the most reliable source when behind Cloudflare
        String ip = request.getHeaders().getFirst("CF-Connecting-IP");
        if (ip == null) {
            ip = request.getHeaders().getFirst("X-Forwarded-For");
            if (ip != null) {
                int commaIdx = ip.indexOf(',');
                if (commaIdx > 0) {
                    ip = ip.substring(0, commaIdx).trim();
                }
            }
        }
        if (ip == null) {
            ip = request.getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null) {
            InetSocketAddress remoteAddress = request.getRemoteAddress();
            ip = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
        }

        // Reconstruct original URI as seen by the client (Cloudflare/Traefik forward the original scheme and host)
        java.net.URI internalUri = request.getURI();
        String scheme = request.getHeaders().getFirst("X-Forwarded-Proto");
        if (scheme == null) scheme = internalUri.getScheme();
        String host = request.getHeaders().getFirst("X-Forwarded-Host");
        if (host == null) host = request.getHeaders().getFirst("Host");
        if (host == null) host = internalUri.getHost();
        String pathAndQuery = internalUri.getRawPath();
        if (internalUri.getRawQuery() != null) {
            pathAndQuery = pathAndQuery + "?" + internalUri.getRawQuery();
        }
        String uri = scheme + "://" + host + pathAndQuery;

        String ua = request.getHeaders().getFirst("User-Agent");
        String locale = request.getHeaders().getFirst("Accept-Language");

        logger.info("ip={}, uri={}, ua={}, locale={}", ip, uri, ua, locale);

        return chain.filter(exchange);
    }
}
