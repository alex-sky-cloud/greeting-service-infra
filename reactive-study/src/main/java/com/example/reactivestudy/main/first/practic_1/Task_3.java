package com.example.reactivestudy.main.first.practic_1;

public class Task_3 {

    public static void main(String[] args) throws InterruptedException {


        Thread thread = new Thread(() -> {
            int counter = 1;
            int exported = 0;

            try {
                for (; counter < 10; counter++) {
                    Thread.sleep(400);
                    System.out.println("Страница " + counter + " выгружена");
                    exported++;
                }
            } catch (InterruptedException e) {
                System.out.println("Выгрузка отменена, выгружено страниц: " + exported);
            }

        });

        thread.start();
        Thread.sleep(1000);
        thread.interrupt();
        thread.join();
        System.out.println("Отмена подтверждена");

    }

}
