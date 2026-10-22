package com.example.vtcourse.t03;

import com.example.vtcourse.common.Todo;

/**
 * Тема 03. Практика: задача 2.
 * Условия: {@code virtual-threads-study/docs/virtual-threads-course/03-locks-pinning-tasks.md}.
 */
public class T03PrinterTasks {

    private final ReceiptPrinter printer;

    public T03PrinterTasks(ReceiptPrinter printer) {
        this.printer = printer;
    }

    /** Задача 2. Печать чека, если принтер освободился быстро. */
    public String printReceipt(String receiptId) throws InterruptedException {
        throw Todo.task("03.2");
    }
}
