package com.example.reactivestudy.main.decorate;

public class ConcreteDecoratorB extends Decorator {

    protected ConcreteDecoratorB(Component source) {
        super(source);
    }

    @Override
    public String operation() {
        return super.operation() + " + добавка B";
    }
}
