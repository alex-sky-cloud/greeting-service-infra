package com.example.reactivestudy.main;

public class Report {

    static int revenue;

    public static void main(String[] args) throws InterruptedException {

        Thread worker = new Thread(() -> revenue = 1000);

        worker.start();
        System.out.println(" До join: " + revenue);

       // worker.join();
        System.out.println(" После join: " + revenue);

    }
}
