package com.vishal.distributed_rate_limiter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties("rate-limiter")
public class RateLimiterProperties {
    private boolean enabled;
    private Map<String, Plan> plans;
}
