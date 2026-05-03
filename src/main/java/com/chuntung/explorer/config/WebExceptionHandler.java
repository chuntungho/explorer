package com.chuntung.explorer.config;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class WebExceptionHandler implements ExceptionHandler<Throwable, HttpResponse<?>> {
    private static final Logger logger = LoggerFactory.getLogger(WebExceptionHandler.class);

    @Override
    public HttpResponse<?> handle(HttpRequest request, Throwable exception) {
        logger.error("Failed to process request {}", request.getUri(), exception);
        return HttpResponse.serverError();
    }
}
