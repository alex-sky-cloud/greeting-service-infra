package com.example.reactivestudy.main.decorate;

/**
 * DataSourceDecorator хранит ссылку на wrappee того же интерфейса DataSource и делегирует вызовы, добавляя своё поведение поверх
 */
public abstract class DataSourceDecorator implements DataSource {

    /*Обертка*/
    private final DataSource wrapped;

    public DataSourceDecorator(DataSource dataSource) {
        this.wrapped = dataSource;
    }

    @Override
    public void write(String data) {


        this.wrapped.write(data);
    }

    @Override
    public String readData() {

        return wrapped.readData();
    }
}
