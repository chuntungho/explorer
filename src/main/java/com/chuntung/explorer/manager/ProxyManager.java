package com.chuntung.explorer.manager;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.util.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ProxyManager {
    private static final Logger logger = LoggerFactory.getLogger(ProxyManager.class);
    private ExplorerProperties explorerProperties;
    private BlockManager adBlocker;
    private RestTemplate restTemplate;
    private HtmlResolver htmlResolver;

    public ProxyManager(ExplorerProperties explorerProperties, BlockManager adBlocker, RestTemplate restTemplate, HtmlResolver htmlResolver) {
        this.explorerProperties = explorerProperties;
        this.adBlocker = adBlocker;
        this.restTemplate = restTemplate;
        this.htmlResolver = htmlResolver;
    }

    public ResponseEntity<Resource> proxy(RequestEntity<?> request, URI remoteURI, URI proxyURI) {
        HttpHeaders requestHeaders = trimHeaders(request.getHeaders(), explorerProperties.getProxyHeaders());

        // replace host with remote host
        for (String x : explorerProperties.getTransformHeaders()) {
            if (requestHeaders.containsKey(x)) {
                if (x.equalsIgnoreCase("Host")) {
                    requestHeaders.set(x, remoteURI.getHost());
                    continue;
                }
                String url = requestHeaders.getFirst(x);
                if (url != null && url.contains("//") & url.contains(proxyURI.getHost())) {
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
            return ResponseEntity.noContent().build();
        }

        Set<String> excludedHeaders = getExcludedHeaders(requestHeaders.getOrigin() != null);
        try {
            ResponseEntity<? extends Resource> responseEntity =
                    restTemplate.exchange(requestCopy, InputStreamResource.class);
            HttpStatusCode statusCode = responseEntity.getStatusCode();
            HttpHeaders responseHeaders = trimHeaders(responseEntity.getHeaders(), excludedHeaders);
            Resource body = responseEntity.getBody();

            // handle redirect, just let browser redirect to converted url as the html may contain relative paths
            if (statusCode.is3xxRedirection() && responseHeaders.getLocation() != null) {
                String destUrl = UrlUtil.proxyUrl(responseHeaders.getLocation().toString(), proxyURI);
                responseHeaders.set("Location", destUrl);
                return new ResponseEntity<>(body, responseHeaders, statusCode);
            }

            // resolve html
            MediaType contentType = responseHeaders.getContentType();
            if (contentType != null && contentType.isCompatibleWith(MediaType.TEXT_HTML) && body != null) {
                ExplorerSetting setting = new ExplorerSetting(request.getUrl().getRawQuery());
                Resource resolved = htmlResolver.resolve(body, responseHeaders, remoteURI, proxyURI, setting);
                return new ResponseEntity<>(resolved, responseHeaders, statusCode);
            } else {
                return new ResponseEntity<>(body, responseHeaders, statusCode);
            }
        } catch (RestClientResponseException e) {
            logger.info("{}, invalid request: {}", e.getStatusCode(), remoteURI);
            return new ResponseEntity<>(e.getResponseBodyAs(ByteArrayResource.class)
                    , trimHeaders(e.getResponseHeaders(), excludedHeaders), e.getStatusCode());
        } catch (Exception e) {
            logger.warn("Failed to handle request: {}", remoteURI, e);
            return new ResponseEntity<>(new ByteArrayResource(e.getMessage().getBytes(StandardCharsets.UTF_8)), HttpStatus.BAD_GATEWAY);
        }
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

    private HttpHeaders trimHeaders(HttpHeaders immutableHeaders, Set<String> ignoredHeaders) {
        HttpHeaders mutable = HttpHeaders.writableHttpHeaders(immutableHeaders);
        ignoredHeaders.forEach(mutable::remove);
        return mutable;
    }
}
