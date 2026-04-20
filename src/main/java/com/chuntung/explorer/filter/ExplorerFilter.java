package com.chuntung.explorer.filter;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.manager.AssetManager;
import com.chuntung.explorer.manager.ProxyManager;
import com.chuntung.explorer.util.UrlUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Objects;

@Component
@Order(1)
public class ExplorerFilter extends OncePerRequestFilter {

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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String hostHeader = request.getHeader("Host");
        if (Objects.equals(explorerProperties.getHost(), hostHeader)) {
            // todo static serving or proxy
            String url = request.getParameter("url");
            String direct = request.getParameter("direct");
            if (StringUtils.hasLength(url)) {
                try {
                    URI requestUri = new URI(request.getScheme(), null, request.getServerName(), request.getServerPort(), request.getRequestURI(), request.getQueryString(), null);
                    URI proxyURI = requestUri;
                    if (StringUtils.hasLength(explorerProperties.getWildcardHost())) {
                        proxyURI = new URI(requestUri.getScheme() + "://" + explorerProperties.getWildcardHost());
                    }
                    if ("true".equalsIgnoreCase(direct)) { // direct proxy
                        RequestEntity<byte[]> requestEntity = convert(request);
                        ResponseEntity<Resource> responseEntity = proxyManager.proxy(requestEntity, UrlUtil.toURI(url), proxyURI);
                        convert(responseEntity, response);
                    } else { // just redirect for html
                        URI proxyUrl = UrlUtil.toURI(UrlUtil.proxyUrl(url, proxyURI));
                        response.sendRedirect(proxyUrl.toString());
                    }
                } catch (Exception e) {
                    logger.warn("Failed to proxy url: {}", url, e);
                    response.sendError(400, "Failed to fetch url");
                }
            } else {
                String path = URI.create(request.getRequestURI()).getPath();
                ResponseEntity<?> responseEntity = assetManager.getAsset(Objects.equals("/", path) ? "/browser.html" : path);
                convert(responseEntity, response);
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private RequestEntity<byte[]> convert(HttpServletRequest request) throws IOException, URISyntaxException {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        URI uri = new URI(request.getScheme(), null, request.getServerName(), request.getServerPort(), request.getRequestURI(), request.getQueryString(), null);

        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, Collections.list(request.getHeaders(headerName)));
        }

        byte[] body = request.getInputStream().readAllBytes();

        return new RequestEntity<>(body, headers, method, uri);
    }

    private static void convert(ResponseEntity<?> responseEntity, HttpServletResponse response) throws IOException {
        response.setStatus(responseEntity.getStatusCode().value());
        responseEntity.getHeaders().forEach((k, v) -> {
            if (!k.equalsIgnoreCase(HttpHeaders.TRANSFER_ENCODING)) { // Transfer-Encoding is handled by servlet container
                response.setHeader(k, String.join(",", v));
            }
        });

        if (responseEntity.getBody() instanceof Resource resource) {
            try (var is = resource.getInputStream()) {
                is.transferTo(response.getOutputStream());
            }
        } else if (responseEntity.getBody() != null) {
            response.getOutputStream().write(responseEntity.getBody().toString().getBytes());
        }
    }
}
