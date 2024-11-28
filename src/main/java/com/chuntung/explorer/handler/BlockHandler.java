package com.chuntung.explorer.handler;

import com.chuntung.explorer.manager.HtmlResolver;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;

import java.net.URI;
import java.util.Optional;

/**
 * Customized handler that handle request and parsed html when the uri matches.
 */
public interface BlockHandler {
    boolean match(URI uri);

    /**
     * Pre-handle request before accessing remote resource.
     *
     * @param uri real uri
     * @param requestEntity
     * @return false if not to continue
     */
    default boolean preHandle(URI uri, RequestEntity<?> requestEntity) {
        return true;
    }

    /**
     * Post-handle after html is resolved by {@link HtmlResolver}.
     *
     * @param proxyURI
     * @param uri
     * @param responseHeaders
     * @param document
     */
    default void postHtmlHandle(URI proxyURI, URI uri, HttpHeaders responseHeaders, Document document) {

    }

    default Optional<Element> getElementById(Document document, String id) {
        Element element = document.getElementById(id);
        return Optional.ofNullable(element);
    }
}
