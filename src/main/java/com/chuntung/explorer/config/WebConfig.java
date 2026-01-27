package com.chuntung.explorer.config;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class WebConfig {

    /**
     * As we need to convert target host, not to follow redirect.
     */
    public static class NoRedirectSimpleClientHttpRequestFactory extends SimpleClientHttpRequestFactory {
        @Override
        protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
            super.prepareConnection(connection, httpMethod);
            connection.setInstanceFollowRedirects(false);
        }
    }

    @Bean
    StreamResourceHttpMessageConverter streamResourceHttpMessageConverter() {
        return new StreamResourceHttpMessageConverter();
    }

    @Bean
    // https://github.com/ItamarBenjamin/stream-rest-template
    RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder, ExplorerProperties explorerProperties) {
        return restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(10))
                .requestFactory(() -> {
                    NoRedirectSimpleClientHttpRequestFactory factory = new NoRedirectSimpleClientHttpRequestFactory();
                    ProxyProperties proxy = explorerProperties.getProxy();
                    if (proxy != null && Boolean.TRUE.equals(proxy.getEnabled())) {
                        SocketAddress address = new InetSocketAddress(proxy.getHost(), proxy.getPort());
                        factory.setProxy(new Proxy(proxy.getType(), address));
                    }
                    return factory;
                })
                .configure(new StreamRestTemplate());
    }

    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }

    @Bean
    ExplorerProperties explorerProperties() {
        return new ExplorerProperties();
    }
}
