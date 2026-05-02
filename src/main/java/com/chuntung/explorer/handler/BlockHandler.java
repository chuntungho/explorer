package com.chuntung.explorer.handler;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpHeaders;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.util.Optional;

public interface BlockHandler {
    boolean match(URI uri);

    default boolean preHandle(URI uri, HttpRequest<?> request) {
        return true;
    }

    default void postHtmlHandle(URI proxyURI, URI uri, MutableHttpHeaders responseHeaders, Document document) {
    }

    default Optional<Element> getElementById(Document document, String id) {
        return Optional.ofNullable(document.getElementById(id));
    }
}
