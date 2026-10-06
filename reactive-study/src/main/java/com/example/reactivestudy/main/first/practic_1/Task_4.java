package com.example.reactivestudy.main.first.practic_1;

public class Task_4 {

    public static void main(String[] args) throws InterruptedException {

        long startTime = System.nanoTime() / 1_000_000;

        Thread thread = new Thread(() -> {
            long counter = 0;
            for (int i = 1; true; i++) {
              if(Thread.currentThread().isInterrupted()) {
                  break;
              }
                int i1 = i / 7;
              counter++;
            }

            System.out.println("Поиск остановлен, проверено чисел: " + counter);
        });

        thread.start();
        Thread.sleep(500);
        thread.interrupt();
        thread.join();
        System.out.println("Время: " + ( (System.nanoTime() / 1_000_000) - startTime  ) + " мс");
    }
}
