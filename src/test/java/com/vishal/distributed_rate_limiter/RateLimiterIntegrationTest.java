package com.vishal.distributed_rate_limiter;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RateLimiterIntegrationTest {

    @Container
    static final RedisContainer redis = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag("alpine"));

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void testFreePlan_allowsUpToCapacityAndThenBlocks() {
        String apiKey = "test-free-key";

        // First 10 requests should be allowed
        for (int i = 0; i < 10; i++) {
            final int count = i;
            webTestClient.get().uri("/free")
                    .headers(h -> h.set("X-API-KEY", apiKey))
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().value("X-RateLimit-Remaining", remaining -> assertThat(remaining).isEqualTo(String.valueOf(9 - count)))
                    .expectHeader().value("X-RateLimit-Limit", limit -> assertThat(limit).isEqualTo("10"));
        }

        // 11th request should be blocked
        webTestClient.get().uri("/free")
                .headers(h -> h.set("X-API-KEY", apiKey))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().value("X-RateLimit-Remaining", remaining -> assertThat(remaining).isEqualTo("0"));
    }

    @Test
    void testBasicPlan_allowsUpToCapacityAndThenBlocks() {
        String apiKey = "test-basic-key";


        // First 100 requests should be allowed
        for (int i = 0; i < 100; i++) {
            webTestClient.get().uri("/basic")
                    .headers(h -> h.set("X-API-KEY", apiKey))
                    .exchange()
                    .expectStatus().isOk();
        }

        // 101st request should be blocked
        webTestClient.get().uri("/basic")
                .headers(h -> h.set("X-API-KEY", apiKey))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
