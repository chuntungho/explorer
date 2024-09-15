package com.chuntung.explorer;

import com.chuntung.ingress.IngressApplication;
import com.chuntung.proxy.ProxyApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan
@EnableAutoConfiguration
@SpringBootConfiguration(proxyBeanMethods = false)
public class MainApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder()
                .sources(MainApplication.class).web(WebApplicationType.NONE)
                .child(IngressApplication.class).web(WebApplicationType.SERVLET)
                .sibling(ProxyApplication.class).web(WebApplicationType.SERVLET)
                .run(args);
    }
}