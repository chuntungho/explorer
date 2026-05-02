package com.chuntung.explorer.manager;

import com.chuntung.explorer.handler.BlockHandler;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpHeaders;
import jakarta.inject.Singleton;
import org.jsoup.nodes.Document;

import java.net.URI;
import java.util.Collection;

@Singleton
public class BlockManager {
    private final Collection<BlockHandler> blockHandlers;

    public BlockManager(Collection<BlockHandler> blockHandlers) {
        this.blockHandlers = blockHandlers;
    }

    public boolean preHandle(URI remoteURI, HttpRequest<?> request) {
        for (BlockHandler x : blockHandlers) {
            if (x.match(remoteURI)) {
                if (!x.preHandle(remoteURI, request)) {
                    return false;
                }
            }
        }
        return true;
    }

    public void postHandle(URI proxyURI, URI remoteURI, MutableHttpHeaders responseHeaders, Document document) {
        blockHandlers.forEach(x -> {
            if (x.match(remoteURI)) {
                x.postHtmlHandle(proxyURI, remoteURI, responseHeaders, document);
            }
        });
    }
}
