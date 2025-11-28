# Distributed API Rate Limiter using Spring Boot and Redis

This project implements a high-performance, distributed API rate limiter using the Token Bucket algorithm. It is built with Spring Boot and leverages Redis for centralized state management, making it suitable for scalable, multi-instance deployments.

## Features

-   **Distributed & Scalable**: All application instances share rate limit state in a central Redis server, ensuring consistent enforcement across a distributed system.
-   **Token Bucket Algorithm**: Implements the efficient and flexible Token Bucket algorithm for controlling request rates.
-   **Atomic Operations**: Uses a Redis Lua script to ensure that token consumption logic (check and decrement) is fully atomic, preventing race conditions under high concurrency.
-   **Declarative Configuration**: Rate limiting is applied declaratively to controller endpoints using a simple `@RateLimit` annotation.
-   **Configurable Tiers**: Easily define multiple rate-limiting plans (e.g., `FREE`, `BASIC`, `PROFESSIONAL`) in the `application.properties` file without code changes.
-   **Client Identification**: Identifies clients by the `X-API-KEY` HTTP header.
-   **Informative Response Headers**: Provides clients with feedback on their current rate limit status via `X-RateLimit-Limit` and `X-RateLimit-Remaining` headers.
-   **Integration Tested**: Includes a robust integration test suite using Testcontainers to ensure reliability.

## Technology Stack

-   **Framework**: Spring Boot 4.0.0
-   **Language**: Java 23
-   **State Store**: Redis
-   **Build Tool**: Maven
-   **Testing**: JUnit 5, Mockito, Testcontainers

## Prerequisites

-   Java 23 or later
-   Apache Maven 3.9+
-   Docker (for running the Redis instance)

## Configuration

The rate limiter is configured in `src/main/resources/application.properties`. You can define multiple plans, each with its own token `capacity` and `refill-rate-per-second`.

```properties
# application.properties

# Enable or disable the rate limiter globally
rate-limiter.enabled=true

# Define different rate-limiting plans
rate-limiter.plans.FREE.capacity=10
rate-limiter.plans.FREE.refill-rate-per-second=2

rate-limiter.plans.BASIC.capacity=100
rate-limiter.plans.BASIC.refill-rate-per-second=10

rate-limiter.plans.PROFESSIONAL.capacity=1000
rate-limiter.plans.PROFESSIONAL.refill-rate-per-second=50
```

## How to Run

1.  **Start Redis:**
    Open a terminal and start a Redis instance using Docker.
    ```sh
    docker run -d --name redis-rate-limiter -p 6379:6379 redis:alpine
    ```

2.  **Build the Project:**
    Navigate to the project root and build the application using Maven.
    ```sh
    mvn clean install
    ```

3.  **Run the Application:**
    You can run the application using the Spring Boot Maven plugin or by executing the JAR file.
    ```sh
    # Using Maven
    mvn spring-boot:run

    # Or by running the JAR
    java -jar target/rate-limiter-0.0.1-SNAPSHOT.jar
    ```
The application will start and connect to the Redis instance running on `localhost:6379`.

## How to Use

To protect an endpoint, simply add the `@RateLimit` annotation to the controller method and specify the desired plan name.

**Example:**

```java
import com.vishal.distributed_rate_limiter.RateLimit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/free")
    @RateLimit(plan = "FREE")
    public ResponseEntity<String> freeEndpoint() {
        return ResponseEntity.ok("Free plan endpoint accessed.");
    }

    @GetMapping("/basic")
    @RateLimit(plan = "BASIC")
    public ResponseEntity<String> basicEndpoint() {
        return ResponseEntity.ok("Basic plan endpoint accessed.");
    }
}
```

## API Usage and Response Headers

Clients should provide their API key in the `X-API-KEY` header of their requests.

**Example Request:**

```sh
curl -i -H "X-API-KEY: my-secret-key" http://localhost:8080/free
```

**Response Headers:**

-   `X-RateLimit-Limit`: The total number of requests allowed in the window for the given plan.
-   `X-RateLimit-Remaining`: The number of requests remaining in the current window.

If the rate limit is exceeded, the API will respond with an **HTTP 429 Too Many Requests** status code.
