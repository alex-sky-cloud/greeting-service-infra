package com.example.reactivestudy.main.first.practic_1;

public class Task_5 {

    public static void main(String[] args) throws InterruptedException {

        long startTime = System.nanoTime() / 1_000_000;

        Thread thread = new Thread(() -> {
           for(int i = 1; true; i++) {
               try {
                   Thread.sleep(300);
               } catch (InterruptedException e) {
                   throw new RuntimeException(e);
               }
               System.out.println("Статистика: " + i);
           }
        });


        thread.setDaemon(true);
        thread.start();
        Thread.sleep(1000);

        System.out.println("Основная работа завершена (" + calc(startTime) + "мс)");
    }

    private static long calc(long startTime){

        return System.nanoTime() / 1_000_000 - startTime;
    }
}
