package com.example.coldhotpublisher.infra.webclient;

/**
 * <p>Тип внешней системы, с которой общается приложение магазина.</p>
 * <p>Ключ отражает предметную область (каталог, anti-fraud, доставка), а не имя Spring-бина.</p>
 */
public enum ApiClientKind {

    /** Каталог товаров — карточки и цены. */
    CATALOG,

    /** Anti-fraud — можно ли принять заказ. */
    FRAUD,

    /** Тарифы доставки по зонам. */
    TARIFF,

    /** Жизненный цикл заказа в реальном времени. */
    ORDER_STATUS,

    /** Котировки валютных пар для витрины. */
    MARKET
}
