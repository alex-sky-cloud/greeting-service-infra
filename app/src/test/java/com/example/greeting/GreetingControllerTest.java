package com.example.greeting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class GreetingControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void greetingWithDefaultName() throws Exception {
        var result = mockMvc.get()
                .uri("/api/greeting")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(json.path("message").asText()).contains("Hello, World!");
        assertThat(json.path("environment").asText()).isNotBlank();
        assertThat(json.path("version").asText()).isNotBlank();
        assertThat(json.path("timestamp").asText()).isNotBlank();
    }

    @Test
    void greetingWithCustomName() throws Exception {
        var result = mockMvc.get()
                .uri("/api/greeting?name=Vasya")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(json.path("message").asText()).contains("Hello, Vasya!");
        assertThat(json.path("environment").asText()).isNotBlank();
        assertThat(json.path("version").asText()).isNotBlank();
        assertThat(json.path("timestamp").asText()).isNotBlank();
    }

    @Test
    void healthEndpointIsAccessible() {
        var result = mockMvc.get()
                .uri("/actuator/health")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
    }
}