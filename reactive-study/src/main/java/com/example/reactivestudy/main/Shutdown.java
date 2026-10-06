package com.example.reactivestudy.main;

public class Shutdown {
    public static void main(String[] args) {
        Thread worker = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                try {
                    Thread.sleep(3000); // Имитируем работу, которая занимает время.
                } catch (InterruptedException e) {
                    return;
                }

                System.out.println();
                System.out.println("worker работает, шаг " + i);
            }
            System.out.println("worker закончил");
        }, "worker");

        worker.start(); // Запускаем не-daemon поток. join() здесь намеренно не вызываем.

        System.out.println("main закончил, но JVM ещё работает и ждет завершения не фоновых потоков!");
    }
}
