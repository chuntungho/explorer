package com.chuntung.explorer.controller;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
class ExplorerControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void robotsTxtReturns200OrNotFound() {
        try {
            var response = client.toBlocking()
                    .exchange(HttpRequest.GET("/robots.txt")
                            .header("Host", "localhost:8080"), String.class);
            int code = response.getStatus().getCode();
            assertTrue(code == 200 || code == 404, "Expected 200 or 404 but got " + code);
        } catch (HttpClientResponseException e) {
            // 404 is acceptable
            assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        }
    }

    @Test
    void noDotHostReturnsBadRequest() {
        // A host with no dot (no subdomain) cannot be routed → 400
        try {
            client.toBlocking()
                    .exchange(HttpRequest.GET("/")
                            .header("Host", "plainhost"), String.class);
            fail("Expected HttpClientResponseException for unroutable host");
        } catch (HttpClientResponseException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        }
    }
}
