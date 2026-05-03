package com.chuntung.explorer.config;

import io.micronaut.context.annotation.Factory;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient;
import jakarta.inject.Singleton;

import java.net.InetSocketAddress;
import java.time.Duration;

@Factory
public class HttpClientConfig {

    @Singleton
    ReactorStreamingHttpClient proxyHttpClient(ExplorerProperties props) {
        DefaultHttpClientConfiguration config = new DefaultHttpClientConfiguration();
        config.setMaxContentLength(10 * 1024 * 1024);
        config.setFollowRedirects(false);
        config.setConnectTimeout(Duration.ofSeconds(10));
        config.setReadTimeout(Duration.ofSeconds(30));

        ProxyProperties proxy = props.getProxy();
        if (proxy != null && Boolean.TRUE.equals(proxy.getEnabled())) {
            config.setProxyType(proxy.getType());
            config.setProxyAddress(new InetSocketAddress(proxy.getHost(), proxy.getPort()));
        }

        return ReactorStreamingHttpClient.create(null, config);
    }
}
