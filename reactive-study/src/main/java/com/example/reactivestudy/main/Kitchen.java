package com.example.reactivestudy.main;

public class Kitchen {
    static String fridge = "пусто"; // Одно поле класса, общее для main и cook.

    public static void main(String[] args) throws InterruptedException {
        Runnable task = new Runnable() { // Объект с инструкциями для будущего потока.
            @Override
            public void run() { // Тело run() — набор инструкций, которые выполнит поток.
                System.out.println("run() выполняет поток " + Thread.currentThread().getName());
                fridge = "яйцо"; // Поток кладёт яйцо в общий холодильник.
            }
        };

        Thread cook = new Thread(task, "cook"); // Создаём поток cook и передаём ему task. Поток ещё не работает.
        cook.start(); // main запускает cook. Дальше cook сам вызывает task.run().
        cook.join();  // main ждёт, пока cook выполнит run() до конца.

        System.out.println(fridge);
    }
}
