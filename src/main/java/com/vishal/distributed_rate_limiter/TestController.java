package com.vishal.distributed_rate_limiter;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/free")
    @RateLimit(plan = "FREE")
    public ResponseEntity<String> getFreeData() {
        return ResponseEntity.ok("Free data");
    }

    @GetMapping("/basic")
    @RateLimit(plan = "BASIC")
    public ResponseEntity<String> getBasicData() {
        return ResponseEntity.ok("Basic data");
    }

    @GetMapping("/professional")
    @RateLimit(plan = "PROFESSIONAL")
    public ResponseEntity<String> getProfessionalData() {
        return ResponseEntity.ok("Professional data");
    }
}
