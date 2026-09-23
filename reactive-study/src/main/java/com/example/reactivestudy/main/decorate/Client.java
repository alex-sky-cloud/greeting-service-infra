package com.example.reactivestudy.main.decorate;

public class Client {

    public static void main(String[] args) {

        Component base = new ConcreteComponent();

        System.out.println(base.operation());
        // произошла базовая операция

        ConcreteComponent concreteComponentA = new ConcreteComponent();

        ConcreteDecoratorB concreteDecoratorB = new ConcreteDecoratorB(concreteComponentA);
        System.out.println(concreteDecoratorB.operation());
        //Получили декорированные объекты:
        // Базовый объект
        // Декорированный объект A, обернут вокруг базового
        //декорированный объет B, обернут вокруг объекта A

    }
}
