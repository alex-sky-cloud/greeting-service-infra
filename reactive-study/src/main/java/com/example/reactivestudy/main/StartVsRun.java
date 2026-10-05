package com.example.reactivestudy.main;

public class StartVsRun {
    public static void main(String[] args) {
        Runnable task = new Runnable() {
            @Override
            public void run() { // Инструкции для потока: напечатать имя исполнителя.
                System.out.println("run() выполняет поток " + Thread.currentThread().getName());
            }
        };

        // Первый способ: запускаем поток worker.
        Thread worker = new Thread(task, "worker");
        worker.start(); // Запуск потока worker. Он сам вызовет task.run().

        // Второй способ: создаём поток idle-worker, но start() не вызываем.
        Thread idleWorker = new Thread(task, "idle-worker");
        idleWorker.start(); // Обычный вызов метода у объекта idleWorker.

    }
}