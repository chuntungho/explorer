package com.chuntung.ingress;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@ComponentScan
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@PropertySource("classpath:ingress.properties")
public class IngressApplication {
}