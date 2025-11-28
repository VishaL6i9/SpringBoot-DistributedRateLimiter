package com.vishal.distributed_rate_limiter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List<Long>> redisRequestRateLimiterScript;

    public RateLimitResult tryConsume(String clientId, String planName, Plan plan) {
        if (plan == null) {
            log.warn("Rate limit plan is null for clientId: {}. Allowing request.", clientId);
            return new RateLimitResult(true, Long.MAX_VALUE, null);
        }

        String key = "rate_limit:" + planName + ":" + clientId;
        long capacity = plan.getCapacity();
        long refillRatePerSecond = plan.getRefillRatePerSecond();
        long currentTimeMillis = Instant.now().toEpochMilli();

        try {
            List<Long> result = redisTemplate.execute(
                    redisRequestRateLimiterScript,
                    Collections.singletonList(key),
                    String.valueOf(capacity),
                    String.valueOf(refillRatePerSecond),
                    String.valueOf(currentTimeMillis)
            );

            if (result != null && result.size() == 2) {
                long allowed = result.get(0);
                long remainingTokens = result.get(1);
                boolean isAllowed = allowed == 1;

                Long retryAfterSeconds = null;
                if (!isAllowed) {
                    // Calculate retry after if not allowed. Assuming 1 token requested.
                    // Tokens needed = 1 - remainingTokens
                    // Time to get 1 token = 1 / refillRatePerSecond
                    // Here, we return time until remainingTokens becomes 1 (to consume next)
                    if (refillRatePerSecond > 0) {
                        retryAfterSeconds = (1 - remainingTokens) / refillRatePerSecond;
                        if (retryAfterSeconds <= 0) {
                            retryAfterSeconds = 1L; // At least 1 second if still below 1
                        }
                    } else {
                        retryAfterSeconds = -1L; // Indefinite if refill rate is 0
                    }
                }
                return new RateLimitResult(isAllowed, remainingTokens, retryAfterSeconds);
            }
        } catch (Exception e) {
            log.error("Error executing Redis Lua script for clientId: {}. Falling back to fail-open.", clientId, e);
            return new RateLimitResult(true, Long.MAX_VALUE, null); // Fail-open
        }
        log.error("Unexpected null or invalid result from Redis Lua script for clientId: {}. Falling back to fail-open.", clientId);
        return new RateLimitResult(true, Long.MAX_VALUE, null); // Fail-open
    }
}
