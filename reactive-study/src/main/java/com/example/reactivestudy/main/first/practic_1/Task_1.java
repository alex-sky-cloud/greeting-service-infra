package com.example.reactivestudy.main.first.practic_1;

public class Task_1 {

    static String moscow = "Москва: ";
    static String cazan = "Казань: ";
    static String samara = "Самара: ";
    static String total = "Итого: ";
    static String time = "Время: ";

    static int total_moscow;
    static int total_cazan;
    static int total_samara;

    public static void main(String[] args) throws InterruptedException {


        int timePoolInMs = 1000;

        long ms = 1_000_000;

        long start = System.nanoTime() / ms;

        Thread moscow_warehouse = new Thread(() -> {
            try {
                Thread.sleep(timePoolInMs);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            total_moscow = 120;
        });

        moscow_warehouse.start();

        Thread cazan_warehouse = new Thread(() -> {
            try {
                Thread.sleep(timePoolInMs);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            total_cazan = 80;
        });

        cazan_warehouse.start();

        Thread samara_warehouse = new Thread(() -> {
            try {
                Thread.sleep(timePoolInMs);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            total_samara = 50;
        });

        samara_warehouse.start();

        moscow_warehouse.join();
        cazan_warehouse.join();
        samara_warehouse.join();

        System.out.println(moscow + total_moscow);
        System.out.println(cazan + total_cazan);
        System.out.println(samara + total_samara);
        System.out.println(total + (total_moscow + total_cazan + total_samara));

        long end = System.nanoTime() / ms;

        long time_performing = end - start;
        System.out.println(time + time_performing + " мс");
    }

}
