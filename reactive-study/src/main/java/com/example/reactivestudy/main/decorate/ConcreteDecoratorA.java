package com.example.reactivestudy.main.decorate;


public class ConcreteDecoratorA extends Decorator {

    protected ConcreteDecoratorA(Component source) {
        super(source);
    }

    @Override
    public String operation() {

        return super.operation() + " + добавка А";
    }
}
