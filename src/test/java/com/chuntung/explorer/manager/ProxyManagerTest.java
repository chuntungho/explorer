package com.chuntung.explorer.manager;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
class ProxyManagerTest {
    @Inject
    ProxyManager proxyManager;

    @Test
    void proxy() {
        // todo test html and non-html url
//        proxyManager.proxyManager
    }
}