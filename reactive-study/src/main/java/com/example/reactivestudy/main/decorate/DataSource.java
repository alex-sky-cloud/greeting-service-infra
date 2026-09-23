package com.example.reactivestudy.main.decorate;

public interface DataSource {

    void write(String data);

    String readData();
}
