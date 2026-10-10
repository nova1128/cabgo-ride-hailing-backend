package com.example.cab.service;

import org.springframework.stereotype.Service;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

import com.example.cab.exception.TooManyRequestsException;

/**
 * CONCEPT: per-user rate limiting (token bucket) using Resilience4j.
 * WHY: stops brute-force logins and a single rider spamming trip requests.
 * HOW: each (configName, key) pair gets its own limiter, e.g. "login:9876543210".
 *      The limits (how many per minute) are configured in application.properties under
 *      resilience4j.ratelimiter.configs.login / .trip
 * ALTERNATIVES: API gateway / NGINX limits, Redis-based sliding window (works across many app
 *      instances; this in-memory version only limits per instance).
 * NOTE: limiters are kept in memory forever per key. Fine for a demo; production would expire them.
 */
@Service
public class RateLimitService {

    private final RateLimiterRegistry registry;

    public RateLimitService(RateLimiterRegistry registry) {
        this.registry = registry;
    }

    public void check(String configName, String key) {
        RateLimiter limiter = registry.rateLimiter(configName + ":" + key, configName);
        if (!limiter.acquirePermission()) {
            throw new TooManyRequestsException("Too many requests. Please wait a moment and try again.");
        }
    }
}
