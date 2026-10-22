package com.example.vtcourse.t03;

import com.example.vtcourse.common.CallStats;
import com.example.vtcourse.common.Slow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Готовый класс, менять не нужно.
 * <p>
 * Единственный принтер чеков на кассе. Печать одного чека блокирует поток на 300 мс.
 * Две печати одновременно портят оба чека: принтер это не проверяет, но считает.
 */
public class ReceiptPrinter {

    private final CallStats stats = new CallStats();
    private final List<String> printed = new CopyOnWriteArrayList<>();

    public void print(String receiptId) throws InterruptedException {
        Slow.call(stats, 300);
        printed.add(receiptId);
    }

    public List<String> printed() {
        return List.copyOf(printed);
    }

    public CallStats stats() {
        return stats;
    }
}
