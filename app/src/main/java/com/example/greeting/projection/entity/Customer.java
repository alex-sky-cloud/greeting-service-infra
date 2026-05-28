package com.example.greeting.projection.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Покупатель интернет-магазина.
 *
 * <p>Используется во всех примерах проекций и агрегаций по заказам.</p>
 *
 * @implNote Связь {@code orders} — ленивая. При использовании простых проекций
 *           (интерфейсные / DTO) эта коллекция <b>не</b> инициализируется и не
 *           создаёт N+1. При загрузке полной сущности — требует явного JOIN FETCH
 *           или отдельного запроса.
 */
@Entity
@Table(schema = "shop_demo", name = "customer")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
    private List<Order> orders;

    // getters / setters опущены для краткости
}