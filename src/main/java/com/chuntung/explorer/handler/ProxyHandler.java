package com.chuntung.explorer.handler;

import com.chuntung.explorer.config.ExplorerProperties;
import com.chuntung.explorer.manager.AssetManager;
import com.chuntung.explorer.manager.ProxyManager;
import com.chuntung.explorer.util.UrlUtil;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class ProxyHandler {
    private ExplorerProperties explorerProperties;
    private AssetManager assetManager;
    private ProxyManager proxyManager;

    public ProxyHandler(ExplorerProperties explorerProperties, AssetManager assetManager, ProxyManager proxyManager) {
        this.explorerProperties = explorerProperties;
        this.assetManager = assetManager;
        this.proxyManager = proxyManager;
    }

    public Mono<ServerResponse> proxy(ServerRequest request) {
        List<URI> uris = UrlUtil.splitUris(request.uri(), explorerProperties.getHostMappings());
        if (uris.isEmpty()) {
            String errMsg = "Invalid host: " + request.uri().getHost();
            return ServerResponse.status(HttpStatus.BAD_REQUEST).bodyValue(new ByteArrayResource(errMsg.getBytes(StandardCharsets.UTF_8)));
        }

        URI remoteURI = uris.get(0);
        URI proxyHostURI = uris.get(1);
        return proxyManager.proxy(request, remoteURI, proxyHostURI);
    }

    public Mono<ServerResponse> robots(ServerRequest request) {
        return assetManager.getAsset(request.path());
    }

    public Mono<ServerResponse> fetch(ServerRequest request) {
        // todo fetch or redirect
        return assetManager.getAsset(request.path());
    }
}
