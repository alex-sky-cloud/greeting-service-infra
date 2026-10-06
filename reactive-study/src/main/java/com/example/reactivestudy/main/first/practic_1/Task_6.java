package com.example.reactivestudy.main.first.practic_1;

public class Task_6 {

    public static void main(String[] args) throws InterruptedException {


        Thread thread = new Thread(() -> {

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        String name = thread.getState().name();
        System.out.println("Создан: " + name);

        thread.start();
        Thread.sleep(300);
        name = thread.getState().name();
        System.out.println("Ждёт ответа банка: " + name);

        thread.join();
        name = thread.getState().name();
        System.out.println("Завершил: " + name);
    }
}
