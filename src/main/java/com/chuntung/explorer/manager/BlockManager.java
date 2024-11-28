package com.chuntung.explorer.manager;

import com.chuntung.explorer.handler.BlockHandler;
import org.jsoup.nodes.Document;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;

@Component
public class BlockManager implements ApplicationContextAware {
    private Collection<BlockHandler> blockHandlers;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // todo sort by order
        this.blockHandlers = applicationContext.getBeansOfType(BlockHandler.class).values();
    }

    public boolean preHandle(URI remoteURI, RequestEntity<?> requestCopy) {
        for (BlockHandler x : this.blockHandlers) {
            if (x.match(remoteURI)) {
                if (!x.preHandle(remoteURI, requestCopy)) {
                    return false;
                }
            }
        }

        return true;
    }

    public void postHandle(URI proxyURI, URI remoteURI, HttpHeaders responseHeaders, Document document){
        this.blockHandlers.forEach(x -> {
            if (x.match(remoteURI)) {
                x.postHtmlHandle(proxyURI, remoteURI, responseHeaders, document);
            }
        });
    }
}
