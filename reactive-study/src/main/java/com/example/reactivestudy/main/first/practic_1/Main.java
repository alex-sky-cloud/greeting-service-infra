package com.example.reactivestudy.main.first.practic_1;

public class Main {

    static int answer = 42;
    public static void main(String[] args) throws InterruptedException {

        Runnable worker = (() -> {
            int counter = 0;

           for(int i = 0; true; i++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                counter++;
                System.out.println("Фон: " + counter);
            }

        });

        Thread thread = new Thread(worker, "worker");
        thread.setDaemon(true);
        thread.start();

        Thread.sleep(300);
        System.out.println("Конец");
    }
}
