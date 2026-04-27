package com.chuntung.explorer.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

@Configuration(proxyBeanMethods = false)
public class WebConfig {

    @Bean
    WebClient webClient(ExplorerProperties explorerProperties) {
        HttpClient httpClient = HttpClient.create()
                .followRedirect(false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000);

        ProxyProperties proxy = explorerProperties.getProxy();
        if (proxy != null && Boolean.TRUE.equals(proxy.getEnabled())) {
            httpClient = httpClient.proxy(spec -> spec
                    .type(toProxyType(proxy.getType()))
                    .host(proxy.getHost())
                    .port(proxy.getPort()));
        }

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                // Raise in-memory limit for HTML collection; non-HTML is streamed via Flux<DataBuffer>
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    private ProxyProvider.Proxy toProxyType(java.net.Proxy.Type type) {
        return switch (type) {
            case SOCKS -> ProxyProvider.Proxy.SOCKS5;
            case HTTP -> ProxyProvider.Proxy.HTTP;
            default -> throw new IllegalArgumentException("Unsupported proxy type: " + type);
        };
    }

    @Bean
    ExplorerProperties explorerProperties() {
        return new ExplorerProperties();
    }
}
