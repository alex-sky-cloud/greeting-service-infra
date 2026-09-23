package com.example.reactivestudy.main.decorate;

public abstract class Decorator implements Component {

    private final Component wrappedComponent;


    protected Decorator(Component source) {

        this.wrappedComponent = source;
    }

    @Override
    public String operation() {

        return wrappedComponent.operation();
    }
}
