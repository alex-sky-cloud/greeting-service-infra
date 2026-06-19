package com.example.reactivedemo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class UserControllerIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int localPort;

    @Override
    protected int port() {
        return localPort;
    }

    @Test
    void getUserById() {
        webTestClient.get()
                .uri("/api/users/1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("ann@example.com")
                .jsonPath("$.fullName").isEqualTo("Ann Smith");
    }

    @Test
    void getUserSummaryUsesFlatMapChain() {
        webTestClient.get()
                .uri("/api/users/1/summary")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.orderCount").isEqualTo(2)
                .jsonPath("$.totalAmount").isEqualTo(1029.98)
                .jsonPath("$.orders[0].productName").exists();
    }

    @Test
    void compareMapVsFlatMap() {
        webTestClient.get()
                .uri("/api/demo/reactor/compare?ids=1,2")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.mapWrong.streamElementType").isEqualTo("Mono<User>")
                .jsonPath("$.mapWrong.elementsInStream[0]").value(org.hamcrest.Matchers.containsString("Mono"))
                .jsonPath("$.flatMapCorrect.users[0].email").isEqualTo("ann@example.com")
                .jsonPath("$.flatMapCorrect.users[1].email").isEqualTo("bob@example.com");
    }

    @Test
    void loadUsersWithFlatMapEndpoint() {
        webTestClient.get()
                .uri("/api/demo/reactor/users?ids=1,3")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Object.class)
                .hasSize(2);
    }
}
