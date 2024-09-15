package com.chuntung.ingress;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.manager.AssetManager;
import com.chuntung.explorer.manager.ProxyManager;
import com.chuntung.explorer.util.UrlUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;

@Controller
@CrossOrigin(originPatterns = "*", exposedHeaders = "*", allowCredentials = "true")
public class IngressController {
    private static final Logger logger = LoggerFactory.getLogger(IngressController.class);

    private final ExplorerProperties explorerProperties;
    private final AssetManager assetManager;
    private final ProxyManager proxyManager;

    public IngressController(ExplorerProperties explorerProperties, AssetManager assetManager, ProxyManager proxyManager) {
        this.explorerProperties = explorerProperties;
        this.assetManager = assetManager;
        this.proxyManager = proxyManager;
    }

    /**
     * Proxy given url in the main domain request.
     *
     * <p>Just redirect to converted url, as the content may contains relative paths which should be retained</p>
     *
     * @param url
     * @param request
     * @return
     */
    @RequestMapping()
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
                    return proxyManager.proxy(request, UrlUtil.toURI(url), proxyURI);
                } else { // just redirect for html
                    URI proxyUrl = UrlUtil.toURI(UrlUtil.proxyUrl(url, proxyURI));
                    return ResponseEntity.status(HttpStatus.FOUND).location(proxyUrl).build();
                }
            } catch (Exception e) {
                logger.warn("Failed to proxy url: {}", url, e);
                return ResponseEntity.badRequest().build();
            }
        } else {
            return assetManager.getAsset("/browser.html");
        }
    }

    @GetMapping(value = "/**")
    public ResponseEntity<?> assets(RequestEntity<?> request) {
        return assetManager.getAsset(request.getUrl().getPath());
    }

    @GetMapping(value = "/robots.txt")
    public ResponseEntity<?> robots(RequestEntity<?> request) {
        return assetManager.getAsset(request.getUrl().getPath());
    }

}