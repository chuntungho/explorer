package com.chuntung.proxy;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.manager.AssetManager;
import com.chuntung.explorer.manager.ProxyManager;
import com.chuntung.explorer.util.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@CrossOrigin(originPatterns = "*", exposedHeaders = "*", allowCredentials = "true")
public class ProxyController {
    private static final Logger logger = LoggerFactory.getLogger(ProxyController.class);
    private ExplorerProperties explorerProperties;
    private AssetManager assetManager;
    private ProxyManager proxyManager;

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
     *
     * </p>
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/**", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Resource> proxy(RequestEntity<ByteArrayResource> request) {
        List<URI> uris = UrlUtil.splitUris(request.getUrl(), explorerProperties.getHostMappings());
        if (uris.isEmpty()) {
            String errMsg = "Invalid host: " + request.getUrl().getHost();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ByteArrayResource(errMsg.getBytes(StandardCharsets.UTF_8)));
        }

        URI remoteURI = uris.get(0);
        URI proxyHostURI = uris.get(1);
        return proxyManager.proxy(request, remoteURI, proxyHostURI);
    }

    @RequestMapping(value = "/**", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Resource> proxyMultipart(MultipartHttpServletRequest request) {
        ServletServerHttpRequest serverHttpRequest = new ServletServerHttpRequest(request);
        URI uri = serverHttpRequest.getURI();

        List<URI> uris = UrlUtil.splitUris(uri, explorerProperties.getHostMappings());
        if (uris.isEmpty()) {
            String errMsg = "Invalid host: " + uri.getHost();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ByteArrayResource(errMsg.getBytes(StandardCharsets.UTF_8)));
        }
        URI remoteURI = uris.get(0);
        URI proxyHostURI = uris.get(1);

        // convert multipart to resource to be handled by ResourceHttpMessageConverter (valueType extends Resource)
        // in FormHttpMessageConvertor (bodyType = MultiValueMap)
        MultiValueMap<String, Resource> requestBody = new LinkedMultiValueMap<>();
        request.getParameterMap().forEach((k, v) -> {
            requestBody.put(k, Arrays.stream(v).map(x -> new ByteArrayResource(x.getBytes(StandardCharsets.UTF_8))).collect(Collectors.toList()));
        });
        request.getMultiFileMap().forEach((k, v) -> {
            requestBody.put(k, v.stream().map(MultipartFile::getResource).collect(Collectors.toList()));
        });
        // remove content length as content will be written in new format
        HttpHeaders requestHeaders = request.getRequestHeaders();
        requestHeaders.remove("Content-Length");

        RequestEntity<?> requestEntity = new RequestEntity<>(requestBody, requestHeaders, request.getRequestMethod(), uri, MultiValueMap.class);
        return proxyManager.proxy(requestEntity, remoteURI, proxyHostURI);
    }

    @GetMapping(value = "/robots.txt")
    public ResponseEntity<?> robots(RequestEntity<?> request) {
        return assetManager.getAsset(request.getUrl().getPath());
    }

}