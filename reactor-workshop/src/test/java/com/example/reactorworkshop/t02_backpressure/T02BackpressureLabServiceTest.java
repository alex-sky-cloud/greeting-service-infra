package com.example.reactorworkshop.t02_backpressure;

import com.example.reactorworkshop.t02_backpressure.repository.T02ReadingRepository;
import com.example.reactorworkshop.t02_backpressure.service.T02BackpressureLabService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.test.StepVerifier;

/** {@code limitRate} не отбрасывает элементы. */
class T02BackpressureLabServiceTest {

    @Test
    void limitRateDoesNotDropElements() {
        T02ReadingRepository readings = Mockito.mock(T02ReadingRepository.class);
        T02BackpressureLabService service = new T02BackpressureLabService(readings);
        StepVerifier.create(service.pacedIds(10, 3).map(p -> p.value()))
                .expectNext(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .verifyComplete();
    }
}
