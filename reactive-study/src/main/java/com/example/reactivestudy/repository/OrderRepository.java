package com.example.reactivestudy.repository;

import com.example.reactivestudy.domain.model.Order;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface OrderRepository extends ReactiveCrudRepository<Order, Long> {

    Flux<Order> findTop10ByOrderByIdAsc();
}
