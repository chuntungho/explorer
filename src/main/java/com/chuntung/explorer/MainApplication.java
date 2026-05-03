package com.chuntung.explorer;

import io.micronaut.runtime.Micronaut;

public class MainApplication {
    public static void main(String[] args) {
        String env = System.getenv().getOrDefault("APP_ENV", "local");
        Micronaut.build(args).environments(env).start();
    }
}
