package com.chuntung.explorer.handler;

import com.chuntung.explorer.config.ExplorerProperties;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;

@Component
public class BrowserInterceptor implements AdBlockHandler {
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
    public void postHandle(URI proxyURI, URI uri, HttpHeaders responseHeaders, Document document) {
        String explorerUrl = StringUtils.hasLength(explorerProperties.getExplorerUrl()) ? explorerProperties.getExplorerUrl() : proxyURI.toString();
        // todo wildcard host with port
        String wildcardHost = StringUtils.hasLength(explorerProperties.getWildcardHost()) ? explorerProperties.getWildcardHost() : proxyURI.getHost();
        String interceptorUrl = StringUtils.hasLength(explorerProperties.getInterceptorUrl()) ? explorerProperties.getInterceptorUrl() : (explorerUrl + "/interceptor.js");

        document.head().prepend(INTERCEPTOR_SCRIPT.replace("{interceptorUrl}", interceptorUrl));
        document.head().prepend(REMOTE_URL_META.replace("{remoteUrl}", uri.toString()));
        document.head().prepend(EXPLORER_URL_META.replace("{explorerUrl}", explorerUrl));
        document.head().prepend(WILDCARD_HOST_META.replace("{wildcardHost}", wildcardHost));
    }
}
