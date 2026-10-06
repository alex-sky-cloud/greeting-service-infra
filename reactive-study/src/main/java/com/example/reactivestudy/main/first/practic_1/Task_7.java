package com.example.reactivestudy.main.first.practic_1;

public class Task_7 {

    public static void main(String[] args) throws InterruptedException {

        Runnable runnable = () -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            System.out.println("Отчёт сформирован, исполнитель: " + Thread.currentThread().getName());
        };

        Thread report1 = new Thread(runnable, "report-1");
        Thread report2 = new Thread(runnable, "report-2");

        report1.start();
        report1.join();

        report2.start();
        report2.join();

        System.out.println("Программа завершена без ошибок");
    }
}
