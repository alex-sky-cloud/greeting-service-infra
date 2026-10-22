package com.example.profcourse.t07;

import com.example.profcourse.common.Measure;
import jdk.jfr.Recording;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Проверка задач темы 07. Условия: {@code docs/profiling-course/07-allocations-tasks.md}. */
class T07TasksTest {

    private static final PriceList PRICES = new PriceList();

    @Nested
    @DisplayName("Задача 1. Цена со скидкой без лишних объектов")
    class Task1 {

        @Test
        @DisplayName("цены совпадают со старым каталогом")
        void samePrices() {
            LegacyCatalog legacy = new LegacyCatalog(PRICES);
            T07Catalog fixed = new T07Catalog(PRICES);

            for (String sku : List.of("SKU-00001", "SKU-12345", "SKU-50000", " sku-00777 ", "SKU-99999", "")) {
                for (int discount : new int[]{0, 7, 15, 100}) {
                    assertThat(fixed.priceFor(sku, discount)).as(sku + " скидка " + discount)
                            .isEqualTo(legacy.priceFor(sku, discount));
                }
            }
            assertThat(fixed.priceFor("SKU-00001", 0)).isEqualTo(1_113);
            assertThat(fixed.priceFor("SKU-00001", 15)).isEqualTo(946);
            assertThat(fixed.priceFor("SKU-99999", 15)).isEqualTo(-1);
        }

        @Test
        @DisplayName("10 000 расчётов: в среднем не больше 64 байт на расчёт")
        void allocationBudget() throws Exception {
            T07Catalog fixed = new T07Catalog(PRICES);
            String[] skus = new String[1_000];
            for (int i = 0; i < skus.length; i++) {
                skus[i] = String.format("SKU-%05d", i * 37 + 1);
            }
            for (int i = 0; i < 20_000; i++) {
                fixed.priceFor(skus[i % skus.length], 7);
            }

            long[] sink = new long[1];
            long bytes = Measure.allocatedBytes(() -> {
                for (int i = 0; i < 10_000; i++) {
                    sink[0] += fixed.priceFor(skus[i % skus.length], 7);
                }
            });

            System.out.printf("каталог: %d байт на расчёт%n", bytes / 10_000);
            assertThat(sink[0]).isPositive();
            assertThat(bytes / 10_000).as("байт на один расчёт").isLessThanOrEqualTo(64);
        }
    }

    @Nested
    @DisplayName("Задача 2. Кто выделяет больше всех")
    class Task2 {

        @Test
        @DisplayName("каталог и выгрузка картинок: первое место — старый расчёт цены")
        void topSites(@TempDir Path dir) throws Exception {
            Path file = dir.resolve("alloc.jfr");
            LegacyCatalog legacy = new LegacyCatalog(PRICES);
            ThumbnailService thumbnails = new ThumbnailService();
            try (Recording recording = new Recording()) {
                recording.enable("jdk.ObjectAllocationSample").withStackTrace();
                recording.start();
                long sink = 0;
                for (int i = 0; i < 300; i++) {
                    sink += legacy.priceFor("SKU-00042", 5);
                    sink += thumbnails.thumbnail(i).length;
                }
                assertThat(sink).isPositive();
                recording.stop();
                recording.dump(file);
            }

            List<AllocationSite> top = new T07AllocationSites().top(file, 2);

            assertThat(top).extracting(AllocationSite::method).containsExactly(
                    "com.example.profcourse.t07.LegacyCatalog.priceFor",
                    "com.example.profcourse.t07.ThumbnailService.thumbnail");
            assertThat(top.get(0).bytes()).isGreaterThan(top.get(1).bytes() * 3);
        }
    }
}
