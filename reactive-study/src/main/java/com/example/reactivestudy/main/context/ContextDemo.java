package com.example.reactivestudy.main.context;

import reactor.core.publisher.Mono;

public class ContextDemo {

    public static void main(String[] args) {
        String key = "message";

        Mono<String> result = Mono.just("Hello")
                .flatMap(
                        str -> getDeferContextual(str, key)
                )
                .contextWrite(ctx -> ctx.put(key, "World"));

        result.subscribe(System.out::println); // выведет: Hello World
    }

    private static Mono<String> getDeferContextual(String str, String key) {

        return Mono.deferContextual(
                ctx -> Mono.just(
                        str + " " + ctx.get(key)
                )
        );
    }
}
