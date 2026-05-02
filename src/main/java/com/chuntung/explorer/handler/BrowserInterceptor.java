package com.chuntung.explorer.handler;

import com.chuntung.explorer.config.ExplorerProperties;
import io.micronaut.http.MutableHttpHeaders;
import jakarta.inject.Singleton;
import org.jsoup.nodes.Document;

import java.net.URI;

@Singleton
public class BrowserInterceptor implements BlockHandler {
    public static final String REMOTE_URL_META = "<meta name=\"remote-url\" content=\"{remoteUrl}\">";
    public static final String EXPLORER_URL_META = "<meta name=\"explorer-url\" content=\"{explorerUrl}\">";
    public static final String WILDCARD_HOST_META = "<meta name=\"wildcard-host\" content=\"{wildcardHost}\">";
    public static final String INTERCEPTOR_SCRIPT = "<script src=\"{interceptorUrl}\"></script>";

    private final ExplorerProperties explorerProperties;

    public BrowserInterceptor(ExplorerProperties explorerProperties) {
        this.explorerProperties = explorerProperties;
    }

    @Override
    public boolean match(URI uri) {
        return true;
    }

    @Override
    public void postHtmlHandle(URI proxyURI, URI uri, MutableHttpHeaders responseHeaders, Document document) {
        String url = explorerProperties.getUrl();
        String explorerUrl = (url != null && !url.isEmpty()) ? url : proxyURI.toString();
        String wh = explorerProperties.getWildcardHost();
        String wildcardHost = (wh != null && !wh.isEmpty()) ? wh : proxyURI.getHost();
        String iu = explorerProperties.getInterceptorUrl();
        String interceptorUrl = (iu != null && !iu.isEmpty()) ? iu : (explorerUrl + "/interceptor.js");

        document.head().prepend(INTERCEPTOR_SCRIPT.replace("{interceptorUrl}", interceptorUrl));
        document.head().prepend(REMOTE_URL_META.replace("{remoteUrl}", uri.toString()));
        document.head().prepend(EXPLORER_URL_META.replace("{explorerUrl}", explorerUrl));
        document.head().prepend(WILDCARD_HOST_META.replace("{wildcardHost}", wildcardHost));
    }
}
