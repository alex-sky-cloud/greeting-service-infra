# Spring R2DBC: руководство и вопросы для собеседования

> Краткое руководство по **Spring R2DBC** (реактивный доступ к реляционным БД) и типичным вопросам на Java-собеседованиях.  
> Формат каждого блока: **ответ простым языком → вопрос → источник → цитата (EN/RU)**.

**См. также:** [project-reactor-interview-guide.md](interview/project-reactor-interview-guide.md) — основы Reactor (Mono, Flux, backpressure).

---

## Оглавление

1. [Что такое R2DBC](#1-что-такое-r2dbc)
2. [R2DBC vs JDBC — в чём разница](#2-r2dbc-vs-jdbc--в-чём-разница)
3. [Когда R2DBC уместен, а когда нет](#3-когда-r2dbc-уместен-а-когда-нет)
4. [Spring Data R2DBC — реактивные репозитории](#4-spring-data-r2dbc--реактивные-репозитории)
5. [ConnectionFactory и настройка Spring Boot](#5-connectionfactory-и-настройка-spring-boot)
6. [Mono и Flux в методах репозитория](#6-mono-и-flux-в-методах-репозитория)
7. [DatabaseClient и ручные SQL-запросы](#7-databaseclient-и-ручные-sql-запросы)
8. [Транзакции в R2DBC](#8-транзакции-в-r2dbc)
9. [Почему R2DBC — не JPA/Hibernate](#9-почему-r2dbc--не-jpahibernate)
10. [Пул соединений и производительность](#10-пул-соединений-и-производительность)
11. [Обработка ошибок R2DBC](#11-обработка-ошибок-r2dbc)
12. [R2DBC в полном reactive-стеке (WebFlux + WebClient)](#12-r2dbc-в-полном-reactive-стеке-webflux--webclient)

---

## Введение

**R2DBC** (Reactive Relational Database Connectivity) — спецификация и набор драйверов для **неблокирующего** доступа к SQL-базам (PostgreSQL, MySQL, H2, SQL Server и др.). **Spring Data R2DBC** и **Spring Framework R2DBC** дают репозитории, `DatabaseClient` и интеграцию с Reactor (`Mono`/`Flux`).

Зависимости (Spring Boot):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-r2dbc</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>r2dbc-postgresql</artifactId>
</dependency>
```

---

## 1. Что такое R2DBC

**Ответ:** R2DBC — это API для **реактивного** доступа к реляционным БД. Вместо блокирующего JDBC (поток ждёт ответа от БД) запрос возвращает `Publisher` (в Spring — `Mono` или `Flux`), и поток освобождается для других задач. R2DBC реализует идеи **Reactive Streams**: backpressure, асинхронный I/O на уровне драйвера.

**Вопрос:** *What is R2DBC and why was it created?*

**Источник:** [Spring Framework — R2DBC](https://docs.spring.io/spring-framework/reference/data-access/r2dbc.html)

> **EN:** «R2DBC provides a standard API for reactive database access. It is designed as a reactive alternative to JDBC, allowing non-blocking interaction with relational databases.»

> **RU:** «R2DBC — стандартный API для реактивного доступа к БД. Это реактивная альтернатива JDBC с неблокирующим взаимодействием с реляционными базами.»

---

## 2. R2DBC vs JDBC — в чём разница

**Ответ:**

| | **JDBC** | **R2DBC** |
|---|----------|-----------|
| Модель | Блокирующая: поток ждёт | Неблокирующая: `Mono`/`Flux` |
| API | `ResultSet`, `PreparedStatement` | `Connection`, `Statement`, Reactive Streams |
| ORM | JPA/Hibernate | Нет полноценного ORM (только mapping в Spring Data) |
| Типичный стек | Spring MVC + Tomcat | Spring WebFlux + Netty |
| Пул | HikariCP | r2dbc-pool |

JDBC проще и зрелее; R2DBC даёт выигрыш, когда **весь** путь запроса неблокирующий (Netty → WebFlux → R2DBC).

**Вопрос:** *What is the difference between JDBC and R2DBC?*

**Источник:** [Spring Data R2DBC — Introduction](https://docs.spring.io/spring-data/r2dbc/docs/current/reference/html/#introduction)

> **EN:** «Spring Data R2DBC aims to be a familiar and consistent Spring programming model for relational database access while taking advantage of reactive data access.»

> **RU:** «Spring Data R2DBC — знакомая Spring-модель доступа к реляционным БД с использованием реактивного доступа к данным.»

---

## 3. Когда R2DBC уместен, а когда нет

**Ответ:**

**Уместен:**

- приложение на **WebFlux** + Netty, много одновременных запросов;
- цепочка «HTTP → сервис → БД» полностью на `Mono`/`Flux`;
- стриминг больших выборок (`Flux` построчно, без загрузки всего в память).

**Не уместен / осторожно:**

- классический **Spring MVC + JDBC/JPA** — R2DBC добавит сложность без выигрыша;
- нужен **JPA** (lazy loading, `@OneToMany`, Criteria API);
- команда не готова к reactive-отладке;
- смешивание `block()` внутри reactive-цепочки.

**Вопрос:** *When should you use R2DBC instead of JDBC?*

**Источник:** [Spring Data R2DBC — Requirements](https://docs.spring.io/spring-data/r2dbc/docs/current/reference/html/#requirements)

> **EN:** «Spring Data R2DBC uses Spring Framework's core data abstractions to offer a familiar programming model. It requires a reactive driver and is intended for use in reactive applications.»

> **RU:** «Spring Data R2DBC использует абстракции Spring Data и требует реактивный драйвер; предназначен для реактивных приложений.»

---

## 4. Spring Data R2DBC — реактивные репозитории

**Ответ:** Интерфейс наследует `ReactiveCrudRepository<T, ID>`. Методы возвращают `Mono` или `Flux`, а не `Optional`/`List`. Spring генерирует реализацию по имени метода (`findByLastName`) или `@Query`. Аннотация `@Table` на entity — аналог `@Entity` без JPA-сессии.

```java
public interface UserRepository extends ReactiveCrudRepository<User, Long> {
    Flux<User> findByLastName(String lastName);
    Mono<User> findByEmail(String email);
}
```

**Вопрос:** *How do Spring Data R2DBC repositories differ from JPA repositories?*

**Источник:** [Spring Data R2DBC — Defining Repository Interfaces](https://docs.spring.io/spring-data/r2dbc/docs/current/reference/html/#r2dbc.repositories)

> **EN:** «ReactiveCrudRepository provides basic CRUD operations and returns reactive types such as Mono and Flux instead of blocking types.»

> **RU:** «ReactiveCrudRepository даёт базовые CRUD-операции и возвращает реактивные типы Mono и Flux вместо блокирующих.»

---

## 5. ConnectionFactory и настройка Spring Boot

**Ответ:** Центральный bean — `ConnectionFactory` (аналог `DataSource` в JDBC). В Spring Boot достаточно `application.yml`:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/mydb
    username: user
    password: secret
```

Для пула добавляют `spring.r2dbc.pool.*` (r2dbc-pool). `@EnableR2dbcRepositories` включает сканирование репозиториев.

**Вопрос:** *How do you configure R2DBC in Spring Boot?*

**Источник:** [Spring Boot — R2DBC](https://docs.spring.io/spring-boot/docs/current/reference/html/data.html#data.r2dbc)

> **EN:** «Spring Boot auto-configures a ConnectionFactory if R2DBC is on the classpath. You can configure the connection using spring.r2dbc.* properties.»

> **RU:** «Spring Boot автоматически настраивает ConnectionFactory, если R2DBC в classpath. Подключение задаётся через свойства spring.r2dbc.*.»

---

## 6. Mono и Flux в методах репозитория

**Ответ:**

- **`Mono<T>`** — одна запись или 0: `findById`, `save`, `deleteById`, `count`.
- **`Flux<T>`** — 0…N: `findAll`, `findByStatus`, кастомный `@Query` без LIMIT 1.

`saveAll(Iterable)` возвращает `Flux`. Для «сохранить и вернуть одну» — `save(entity)` → `Mono<User>`.

**Вопрос:** *When should a repository method return Mono vs Flux?*

**Источник:** [Reactor Core — Mono vs Flux](https://projectreactor.io/docs/core/release/reference/coreFeatures.html)

> **EN:** «A Flux object represents a reactive sequence of 0..N items, while a Mono object represents a single-value-or-empty (0..1) result.»

> **RU:** «Flux — 0…N элементов, Mono — один элемент или пусто. Тот же принцип применяют к сигнатурам R2DBC-репозиториев.»

---

## 7. DatabaseClient и ручные SQL-запросы

**Ответ:** Когда репозитория недостаточно — **`DatabaseClient`** (низкоуровневый fluent API): bind параметры, `map` строки в объекты, получить `Mono`/`Flux`. Удобно для сложных JOIN, bulk-операций, вызова функций БД.

```java
return databaseClient.sql("SELECT * FROM users WHERE status = :status")
    .bind("status", status)
    .map((row, meta) -> new User(row.get("id", Long.class), ...))
    .all(); // Flux<User>
```

**Вопрос:** *What is DatabaseClient in Spring R2DBC?*

**Источник:** [Spring Framework — Using DatabaseClient](https://docs.spring.io/spring-framework/reference/data-access/r2dbc.html#r2dbc-Using-DatabaseClient)

> **EN:** «DatabaseClient provides a fluent API for performing common database operations with a minimal amount of ceremony.»

> **RU:** «DatabaseClient — fluent API для типовых операций с БД с минимальной обёрткой.»

---

## 8. Транзакции в R2DBC

**Ответ:** Используют **`TransactionalOperator`** или `@Transactional` на **реактивных** сервисах (методы возвращают `Mono`/`Flux`). Транзакция привязана к Reactor-контексту (`ReactiveTransactionManager`), а не к thread-local JDBC. Важно: все операции в одной цепочке должны использовать тот же connection из контекста.

```java
@Transactional
public Mono<Order> placeOrder(Order order) {
    return orderRepository.save(order)
        .flatMap(o -> inventoryRepository.decrement(o.getProductId()).thenReturn(o));
}
```

**Вопрос:** *How do transactions work in Spring R2DBC?*

**Источник:** [Spring Framework — R2DBC Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/r2dbc.html#r2dbc-transaction)

> **EN:** «Spring Framework supports transactional access to R2DBC databases through ReactiveTransactionManager and @Transactional on reactive methods.»

> **RU:** «Spring поддерживает транзакции R2DBC через ReactiveTransactionManager и @Transactional на реактивных методах.»

---

## 9. Почему R2DBC — не JPA/Hibernate

**Ответ:** R2DBC — **тонкий** слой поверх протокола БД. Нет:

- persistence context и **lazy loading**;
- автоматического dirty checking;
- `@ManyToOne` / каскадов как в JPA;
- JPQL/Criteria.

Mapping — через `@Table`, `@Column`, конвертеры. Связи загружают явно (`flatMap` + второй запрос или JOIN в SQL). Это частый вопрос на собеседовании: «почему нельзя просто подключить Hibernate к WebFlux?» — потому что Hibernate **блокирующий**.

**Вопрос:** *Why doesn't R2DBC support JPA/Hibernate?*

**Источник:** [Spring Data R2DBC — Object Mapping Fundamentals](https://docs.spring.io/spring-data/r2dbc/docs/current/reference/html/#mapping)

> **EN:** «Spring Data R2DBC uses a simplified mapping model. It does not provide a full ORM implementation like JPA.»

> **RU:** «Spring Data R2DBC использует упрощённую модель mapping без полноценного ORM как в JPA.»

---

## 10. Пул соединений и производительность

**Ответ:** Драйвер R2DBC сам по себе асинхронный, но **пул** (`io.r2dbc:r2dbc-pool`) ограничивает число физических соединений к БД — как HikariCP для JDBC. Настройки: `initial-size`, `max-size`, `max-idle-time`. Без пула каждый запрос может открывать новое соединение — дорого.

**Вопрос:** *How does connection pooling work with R2DBC?*

**Источник:** [r2dbc-pool README](https://github.com/r2dbc/r2dbc-pool)

> **EN:** «r2dbc-pool provides a ConnectionPool implementation for R2DBC ConnectionFactory.»

> **RU:** «r2dbc-pool реализует пул соединений поверх ConnectionFactory R2DBC.»

---

## 11. Обработка ошибок R2DBC

**Ответ:** Ошибки БД приходят как **`R2dbcException`** (и подтипы) в сигнале `onError` вашего `Mono`/`Flux`. В сервисе — `onErrorMap`, `onErrorResume`: например, `DuplicateKeyException` → HTTP 409. Не глотайте ошибки молча; логируйте через `doOnError`.

```java
return userRepository.findById(id)
    .switchIfEmpty(Mono.error(new NotFoundException(id)))
    .onErrorMap(R2dbcException.class, e -> new DataAccessException("DB error", e));
```

**Вопрос:** *How do you handle database errors in a reactive repository chain?*

**Источник:** [Spring Framework — R2DBC Exception Translation](https://docs.spring.io/spring-framework/reference/data-access/r2dbc.html)

> **EN:** «Spring provides exception translation from R2DBC exceptions to Spring's DataAccessException hierarchy.»

> **RU:** «Spring переводит исключения R2DBC в иерархию DataAccessException.»

---

## 12. R2DBC в полном reactive-стеке (WebFlux + WebClient)

**Ответ:** Идеальный сценарий: клиент → **WebFlux-контроллер** (`Mono<T>`) → сервис → **R2DBC** (`Mono`/`Flux`) → при необходимости **WebClient** к другому сервису — всё в одной цепочке без `block()`. Netty обслуживает тысячи соединений малым числом потоков.

```java
@GetMapping("/users/{id}")
public Mono<UserDto> getUser(@PathVariable Long id) {
    return userRepository.findById(id)
        .flatMap(user -> profileClient.fetchProfile(user.getProfileId())
            .map(profile -> new UserDto(user, profile)));
}
```

**Вопрос:** *How does R2DBC fit into a Spring WebFlux application?*

**Источник:** [Spring WebFlux — WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html)

> **EN:** «Spring WebFlux is fully non-blocking and supports back pressure. It runs on servers such as Netty that are asynchronous and event-driven.»

> **RU:** «WebFlux полностью неблокирующий, с backpressure, на Netty — естественная пара для R2DBC.»

---

## Мини-пример entity + repository + сервис

```java
@Table("users")
public record User(@Id Long id, String email, String name) {}

public interface UserRepository extends ReactiveCrudRepository<User, Long> {
    Mono<User> findByEmail(String email);
}

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repo;

    public Mono<User> create(User user) {
        return repo.findByEmail(user.email())
            .flatMap(existing -> Mono.<User>error(new DuplicateEmailException()))
            .switchIfEmpty(repo.save(user));
    }
}
```

---

## Полезные ссылки

| Ресурс | URL |
|--------|-----|
| Spring Framework — R2DBC | https://docs.spring.io/spring-framework/reference/data-access/r2dbc.html |
| Spring Data R2DBC Reference | https://docs.spring.io/spring-data/r2dbc/docs/current/reference/html/ |
| Spring Boot — R2DBC | https://docs.spring.io/spring-boot/docs/current/reference/html/data.html#data.r2dbc |
| R2DBC spec (GitHub) | https://github.com/r2dbc/r2dbc-spi |
| Project Reactor (основы) | [project-reactor-interview-guide.md](interview/project-reactor-interview-guide.md) |

---

*Документ для подготовки к собеседованиям. Источники — официальная документация Spring и R2DBC (2024–2026).*
