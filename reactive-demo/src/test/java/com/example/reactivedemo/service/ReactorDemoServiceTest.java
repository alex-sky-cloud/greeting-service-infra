package com.example.reactivedemo.service;

import com.example.reactivedemo.domain.User;
import com.example.reactivedemo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactorDemoServiceTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    ReactorDemoService reactorDemoService;

    @Test
    void flatMapResolvesUsersFromRepository() {
        User ann = new User(1L, "ann@example.com", "Ann Smith", Instant.parse("2024-01-01T00:00:00Z"));
        when(userRepository.findById(1L)).thenReturn(Mono.just(ann));

        StepVerifier.create(reactorDemoService.loadUsersWithFlatMapCorrect(java.util.List.of(1L)))
                .expectNextMatches(dto -> dto.email().equals("ann@example.com"))
                .verifyComplete();
    }

    @Test
    void mapWrongEmitsMonoObjectsNotUsers() {
        when(userRepository.findById(1L)).thenReturn(Mono.just(
                new User(1L, "ann@example.com", "Ann Smith", Instant.now())));

        StepVerifier.create(reactorDemoService.loadUsersWithMapWrong(java.util.List.of(1L)))
                .expectNextMatches(Mono.class::isInstance)
                .verifyComplete();
    }
}
