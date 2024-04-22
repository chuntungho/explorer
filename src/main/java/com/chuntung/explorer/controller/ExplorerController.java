package com.chuntung.explorer.controller;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.util.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Controller;
import org.springframework.util.DigestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller
@CrossOrigin(originPatterns = "*", exposedHeaders = "*", allowCredentials = "true")
public class ExplorerController {
    private static final Logger logger = LoggerFactory.getLogger(ExplorerController.class);

    private AdBlocker adBlocker;
    private HtmlResolver htmlResolver;
    private ExplorerProperties explorerProperties;
    private RestTemplate restTemplate;

    public ExplorerController(ExplorerProperties explorerProperties, AdBlocker adBlocker, HtmlResolver htmlResolver, RestTemplate restTemplate) {
        this.explorerProperties = explorerProperties;
        this.restTemplate = restTemplate;
        this.adBlocker = adBlocker;
        this.htmlResolver = htmlResolver;
    }

    /**
     * Proxy given url in the main domain request.
     *
     * <p>The request should be forwarded by nginx (identified by header {@code x-internal-forward}).
     * Just redirect to converted url, as the content may contains relative paths which should be retained</p>
     *
     * @param url
     * @param request
     * @return
     */
    @RequestMapping(headers = "x-internal-forward")
    public ResponseEntity<?> entrypoint(
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "direct", defaultValue = "false") boolean direct,
            RequestEntity<Resource> request) {
        if (StringUtils.hasLength(url)) {
            try {
                URI proxyURI = request.getUrl();
                if (StringUtils.hasLength(explorerProperties.getWildcardHost())) {
                    proxyURI = new URI(proxyURI.getScheme() +"://" + explorerProperties.getWildcardHost());
                }
                if (direct) { // direct proxy
                    return doProxy(request, UrlUtil.toURI(url), proxyURI);
                } else { // just redirect for html
                    URI proxyUrl = UrlUtil.toURI(UrlUtil.proxyUrl(url, proxyURI));
                    return ResponseEntity.status(HttpStatus.FOUND).location(proxyUrl).build();
                }
            } catch (Exception e) {
                logger.warn("Failed to proxy url: {}", url, e);
                return ResponseEntity.badRequest().build();
            }
        } else {
            return getAsset("/browser.html");
        }
    }

    @GetMapping(value = "/**", headers = "x-internal-forward")
    public ResponseEntity<?> assets(RequestEntity<?> request) {
        return getAsset(request.getUrl().getPath());
    }

    /**
     * Debug proxying given url in local request.
     *
     * @param url
     * @param request
     * @return
     */
    @RequestMapping(headers = "host=localhost:2024")
    public ResponseEntity<?> localEntrypoint(
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "direct", defaultValue = "false") boolean direct,
            RequestEntity<Resource> request) {
        return entrypoint(url, direct, request);
    }

    @GetMapping(value = "/**", headers = "host=localhost:2024")
    public ResponseEntity<?> localAssets(RequestEntity<?> request) {
        return getAsset(request.getUrl().getPath());
    }

    @GetMapping(value = "/robots.txt")
    public ResponseEntity<?> robots(RequestEntity<?> request) {
        return getAsset(request.getUrl().getPath());
    }

    private ResponseEntity<?> getAsset(String path) {
        InputStream in = null;
        // find local file first
        File localFile = new File(explorerProperties.getAssetsPath() + path);
        Long lastModified = null;
        if (localFile.exists()) {
            try {
                in = new FileInputStream(localFile);
                lastModified = localFile.lastModified();
            } catch (IOException e) {
                // NOOP
            }
        } else {
            in = getClass().getResourceAsStream("/static" + path);
        }

        if (in == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        MediaType contentType;
        try {
            String mimeType = Files.probeContentType(Path.of(path));
            contentType = MediaType.valueOf(mimeType);
        } catch (Exception e) {
            logger.warn("Failed to get content type: {}", path);
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String etag = null;
        CacheControl cacheControl = null; //CacheControl.empty();
        if (!contentType.equals(MediaType.TEXT_HTML)) {
            cacheControl = CacheControl.maxAge(1, TimeUnit.DAYS);
            if (lastModified == null) {
                try {
                    byte[] bytes = in.readAllBytes();
                    etag = DigestUtils.md5DigestAsHex(bytes);
                    in = new ByteArrayInputStream(bytes);
                } catch (IOException e) {
                    logger.warn("Failed to generate MD5 for {}", path);
                    // NOOP
                }
            }
        }

        ResponseEntity.BodyBuilder bodyBuilder = ResponseEntity.ok().contentType(contentType);
        if (cacheControl != null) {
            bodyBuilder.cacheControl(cacheControl);
        }
        if (lastModified != null) {
            bodyBuilder.lastModified(lastModified);
        }
        if (etag != null) {
            bodyBuilder.eTag(etag);
        }
        return bodyBuilder.body(new InputStreamResource(in));
    }

    /**
     * Proxy http request with specified header forwarded by nginx, it requires your domain supporting wildcard dns.
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
    public ResponseEntity<Resource> proxy(RequestEntity<?> request) {
        List<URI> uris = UrlUtil.splitUris(request.getUrl(), explorerProperties.getHostMappings());
        if (uris.isEmpty()) {
            String errMsg = "Invalid host: " + request.getUrl().getHost();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ByteArrayResource(errMsg.getBytes(StandardCharsets.UTF_8)));
        }

        URI remoteURI = uris.get(0);
        URI proxyHostURI = uris.get(1);
        return doProxy(request, remoteURI, proxyHostURI);
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
        return doProxy(requestEntity, remoteURI, proxyHostURI);
    }


    private ResponseEntity<Resource> doProxy(RequestEntity<?> request, URI remoteURI, URI proxyURI) {
        HttpHeaders requestHeaders = trimHeaders(request.getHeaders(), explorerProperties.getProxyHeaders());

        // replace host with remote host
        for (String x : explorerProperties.getTransformHeaders()) {
            if (requestHeaders.containsKey(x)) {
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