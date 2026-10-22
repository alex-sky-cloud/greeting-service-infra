package com.example.profcourse.common;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

/**
 * То же, что {@code jcmd <pid> GC.class_histogram}, но для своего процесса: через MBean диагностических команд.
 * Гистограмма считает только живые объекты — перед подсчётом JVM делает полную сборку.
 */
public final class HeapHistogram {

    private HeapHistogram() {
    }

    public static String print() {
        try {
            Object text = ManagementFactory.getPlatformMBeanServer().invoke(
                    new ObjectName("com.sun.management:type=DiagnosticCommand"),
                    "gcClassHistogram",
                    new Object[]{new String[0]},
                    new String[]{String[].class.getName()});
            return (String) text;
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось снять гистограмму кучи", e);
        }
    }

    /** Сколько живых объектов класса в куче. */
    public static long liveInstances(Class<?> type) {
        for (String line : print().lines().toList()) {
            String[] cells = line.strip().split("\\s+");
            if (cells.length >= 4 && cells[0].endsWith(":") && cells[3].equals(type.getName())) {
                return Long.parseLong(cells[1]);
            }
        }
        return 0;
    }
}
