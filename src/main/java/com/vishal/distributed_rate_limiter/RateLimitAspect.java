package com.vishal.distributed_rate_limiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final RateLimiterService rateLimiterService;
    private final RateLimiterProperties rateLimiterProperties;

    @Around("@annotation(com.vishal.distributed_rate_limiter.RateLimit)")
    public Object rateLimitAround(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!rateLimiterProperties.isEnabled()) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimitAnnotation = method.getAnnotation(RateLimit.class);

        String planName = rateLimitAnnotation.plan();
        Plan plan = rateLimiterProperties.getPlans().get(planName);

        if (plan == null) {
            log.warn("No rate limit plan found for name: {}. Allowing request.", planName);
            return joinPoint.proceed();
        }

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getResponse();

        String clientId = extractClientId(request);

        RateLimitResult result = rateLimiterService.tryConsume(clientId, planName, plan);

        if (!result.isAllowed()) {
            log.warn("Rate limit exceeded for client: {} with plan: {}", clientId, planName);
            sendRateLimitExceededResponse(response, result.getRetryAfterSeconds());
            return null; // Stop further processing
        } else {
            addRateLimitHeaders(response, plan.getCapacity(), result.getRemainingTokens());
            return joinPoint.proceed();
        }
    }

    private String extractClientId(HttpServletRequest request) {
        // 1. X-API-KEY HTTP Header
        String apiKey = request.getHeader("X-API-KEY");
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey;
        }
        // 2. Client IP Address (as a fallback)
        return Optional.ofNullable(request.getHeader("X-Forwarded-For"))
                .map(s -> s.split(",")[0].trim())
                .orElse(request.getRemoteAddr());
    }

    private void sendRateLimitExceededResponse(HttpServletResponse response, Long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-RateLimit-Limit", "0"); // Or actual capacity if known
        response.setHeader("X-RateLimit-Remaining", "0");
        if (retryAfterSeconds != null && retryAfterSeconds >= 0) {
            response.setHeader("X-RateLimit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));
        }

        StringBuilder jsonResponse = new StringBuilder();
        jsonResponse.append("{");
        jsonResponse.append("\"status\":429,");
        jsonResponse.append("\"error\":\"Rate limit exceeded. Please try again later.\",");
        jsonResponse.append("\"retry_after_seconds\":").append(retryAfterSeconds != null && retryAfterSeconds >= 0 ? retryAfterSeconds : -1);
        jsonResponse.append("}");

        response.getWriter().write(jsonResponse.toString());
    }

    private void addRateLimitHeaders(HttpServletResponse response, long limit, long remaining) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
    }
}
