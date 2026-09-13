package com.lanka.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApiGatewayApplicationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldExposeGatewayInformationWithoutDownstreamServices() {
        webTestClient.get().uri("/").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("Lanka MicroJob API Gateway")
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.routes['/brokers/**']").isEqualTo(
                        "broker-service (broker applications, offline workers, commission)")
                .jsonPath("$.health").isEqualTo("/actuator/health");
    }

    @Test
    void shouldLoadAllConfiguredServiceAndDocumentationRoutes() {
        List<String> routeIds = routeLocator.getRoutes()
                .map(Route::getId)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(routeIds).containsExactlyInAnyOrder(
                "user-service",
                "job-service",
                "job-service-applications",
                "matching-service",
                "broker-service",
                "notification-service",
                "user-service-docs",
                "job-service-docs",
                "matching-service-docs",
                "broker-service-docs",
                "notification-service-docs");
    }
}
