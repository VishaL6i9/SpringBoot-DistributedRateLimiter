package com.vishal.distributed_rate_limiter;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RateLimitResult {
    private boolean allowed;
    private long remainingTokens;
    private Long retryAfterSeconds;
}
