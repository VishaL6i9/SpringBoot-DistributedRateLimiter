package com.vishal.distributed_rate_limiter;

import lombok.Data;

@Data
public class Plan {
    private int capacity;
    private int refillRatePerSecond;
}
