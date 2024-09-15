package com.chuntung.explorer.manager;

import com.chuntung.explorer.handler.AdBlockHandler;
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
public class AdBlocker implements ApplicationContextAware {
    private Collection<AdBlockHandler> adBlockHandlers;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.adBlockHandlers = applicationContext.getBeansOfType(AdBlockHandler.class).values();
    }

    public boolean preHandle(URI remoteURI, RequestEntity<?> requestCopy) {
        for (AdBlockHandler x : this.adBlockHandlers) {
            if (x.match(remoteURI)) {
                if (!x.preHandle(remoteURI, requestCopy)) {
                    return false;
                }
            }
        }

        return true;
    }

    public void postHandle(URI proxyURI, URI remoteURI, HttpHeaders responseHeaders, Document document){
        this.adBlockHandlers.forEach(x -> {
            if (x.match(remoteURI)) {
                x.postHandle(proxyURI, remoteURI, responseHeaders, document);
            }
        });
    }
}
