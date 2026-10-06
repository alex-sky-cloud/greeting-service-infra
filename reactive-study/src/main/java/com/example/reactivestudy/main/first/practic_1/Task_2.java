package com.example.reactivestudy.main.first.practic_1;

public class Task_2 {

    public static void main(String[] args) throws InterruptedException {

        int numberOrder = 42;

        String executorMailer = "mailer";
        long start = System.nanoTime() / 1_000_000;


        Thread thread = new Thread(() -> {

            try {
                Thread.sleep(2000);
                System.out.println("[" + calcTimeExecution(start) + " мс] Письмо по заказу " +
                        numberOrder + " отправлено, исполнитель: " +
                        Thread.currentThread().getName());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, executorMailer);


        System.out.println("[" + calcTimeExecution(start) +
                " мс] Заказ № " + numberOrder + " оформлен");

        thread.start();
        thread.join();

    }

    private static long calcTimeExecution(long start) {

        return ((System.nanoTime() / 1_000_000) - start );
    }

}
