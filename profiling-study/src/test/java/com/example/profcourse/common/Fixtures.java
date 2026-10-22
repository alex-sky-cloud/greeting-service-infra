package com.example.profcourse.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Реальные выводы утилит JDK, снятые с лаборатории: {@code src/test/resources/fixtures/}. */
public final class Fixtures {

    private Fixtures() {
    }

    public static String text(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Нет файла fixtures/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<String> lines(String name) {
        return text(name).lines().toList();
    }
}
