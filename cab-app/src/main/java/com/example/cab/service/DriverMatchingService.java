package com.example.cab.service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import com.example.cab.model.Driver;
import com.example.cab.repository.DriverGeoIndex;
import com.example.cab.repository.DriverRepository;

/**
 * Finds candidate drivers near a pickup point.
 *
 * CONCEPT 1 - strategy switch: cab.matching.strategy = redis (default) or sql. The load test runs both so
 *             the README can show real numbers for "SQL scan vs Redis geo-index".
 * CONCEPT 2 - CIRCUIT BREAKER (Resilience4j): if Redis keeps failing, the breaker "opens" and calls skip
 *             Redis entirely for a while and go straight to the SQL fallback. Riders still get matched
 *             (slower) instead of every request waiting on a dead Redis. After 10s it lets a few trial
 *             calls through ("half-open"); if they succeed it closes again.
 *             Settings: resilience4j.circuitbreaker.instances.redisGeo.* in application.properties.
 * CONCEPT 3 - observability: every lookup is timed and tagged, visible in Prometheus/Grafana as
 *             cab_matching_duration_seconds{strategy="redis|sql|sql_fallback"}.
 *
 * NOTE: the @CircuitBreaker annotation only works when this method is called from ANOTHER bean
 *       (DriverService), because Spring wraps the bean in a proxy. Do not call it from inside this class.
 */
@Service
public class DriverMatchingService {

    private static final Logger log = LoggerFactory.getLogger(DriverMatchingService.class);

    private final DriverGeoIndex geoIndex;
    private final DriverRepository repository;
    private final MeterRegistry meterRegistry;
    private final String strategy;

    public DriverMatchingService(DriverGeoIndex geoIndex, DriverRepository repository,
                                 MeterRegistry meterRegistry,
                                 @Value("${cab.matching.strategy:redis}") String strategy) {
        this.geoIndex = geoIndex;
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.strategy = strategy;
    }

    @CircuitBreaker(name = "redisGeo", fallbackMethod = "redisFallback")
    public List<Long> findNearbyDriverIds(double lat, double lng, double radiusKm, int limit) {
        if ("sql".equalsIgnoreCase(strategy)) {
            return timed("sql", () -> findInDatabase(lat, lng, radiusKm, limit));
        }
        return timed("redis", () -> geoIndex.findNearby(lat, lng, radiusKm, limit));
    }

    /** Called by Resilience4j when the Redis lookup throws, or when the circuit is open. */
    public List<Long> redisFallback(double lat, double lng, double radiusKm, int limit, Throwable cause) {
        log.warn("Redis geo lookup unavailable ({}); falling back to the SQL search", cause.toString());
        meterRegistry.counter("cab.matching.fallback").increment();
        return timed("sql_fallback", () -> findInDatabase(lat, lng, radiusKm, limit));
    }

    private List<Long> findInDatabase(double lat, double lng, double radiusKm, int limit) {
        return repository.findNearbyAvailable(lat, lng, radiusKm, limit).stream()
                .map(Driver::getId)
                .toList();
    }

    private <T> T timed(String strategyName, Supplier<T> work) {
        long start = System.nanoTime();
        try {
            return work.get();
        } finally {
            meterRegistry.timer("cab.matching.duration", "strategy", strategyName)
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }
}
