package com.chuntung.explorer.config;

import com.chuntung.explorer.handler.ProxyHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.all;

@Configuration
public class RouterConfig {

    @Bean
    public RouterFunction<ServerResponse> route(ProxyHandler proxyHandler) {
        return RouterFunctions
                .route(GET("/robots.txt"), proxyHandler::robots)
                .andRoute(GET("/p_"), proxyHandler::fetch)
                .andRoute(all(), proxyHandler::proxy);
    }
}
