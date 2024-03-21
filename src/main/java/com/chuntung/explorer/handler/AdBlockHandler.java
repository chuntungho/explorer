package com.chuntung.explorer.handler;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpHeaders;
import org.springframework.http.RequestEntity;

import java.net.URI;
import java.util.Optional;

public interface AdBlockHandler {
    boolean match(URI uri);

    /**
     * @param uri
     * @param requestEntity
     * @return false if not to continue
     */
    default boolean preHandle(URI uri, RequestEntity<?> requestEntity) {
        return true;
    }

    void postHandle(URI uri, HttpHeaders responseHeaders, Document document);

    default Optional<Element> getElementById(Document document, String id) {
        Element element = document.getElementById(id);
        return Optional.ofNullable(element);
    }
}
