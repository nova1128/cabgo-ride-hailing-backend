package com.example.cab.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.example.cab.repository.DriverGeoIndex;

/**
 * CONCEPT: dynamic (surge) pricing from supply and demand, using Redis.
 *
 *   demand = how many rides were requested in this area in the last 5 minutes   (Redis counter)
 *   supply = how many AVAILABLE drivers are within 3 km                         (Redis geo-index)
 *   multiplier = 1.00 while demand <= supply, then +0.25 for every extra "demand per driver", max 2.50
 *
 * The map is cut into ~1 km grid cells (lat/lng rounded to 2 decimals) so each cell has its own counter.
 *
 * CONCEPT: caching with a short TTL. The multiplier is cached for 20 seconds per cell. That keeps prices
 * stable (riders do not see the price flicker on every request) and avoids recomputing on every call.
 *
 * GRACEFUL DEGRADATION: if Redis is down we return 1.00 (no surge) instead of failing the booking.
 * ALTERNATIVES: geohash/H3 cells instead of a lat/lng grid, a streaming job (Kafka Streams/Flink) computing
 *               demand, an ML-based price model.
 */
@Service
public class SurgePricingService {

    private static final Logger log = LoggerFactory.getLogger(SurgePricingService.class);

    private static final String DEMAND_PREFIX = "surge:demand:";
    private static final String MULTIPLIER_PREFIX = "surge:multiplier:";
    private static final Duration DEMAND_WINDOW = Duration.ofMinutes(5);
    private static final Duration CACHE_TTL = Duration.ofSeconds(20);
    private static final double SUPPLY_RADIUS_KM = 3.0;

    static final BigDecimal NO_SURGE = new BigDecimal("1.00");
    static final BigDecimal MAX_SURGE = new BigDecimal("2.50");

    private final StringRedisTemplate redis;
    private final DriverGeoIndex geoIndex;

    public SurgePricingService(StringRedisTemplate redis, DriverGeoIndex geoIndex) {
        this.redis = redis;
        this.geoIndex = geoIndex;
    }

    /**
     * @param recordDemand true when a rider actually requests a ride (counts as demand);
     *                     false for a price preview, which must not inflate demand.
     */
    public BigDecimal multiplierFor(double lat, double lng, boolean recordDemand) {
        try {
            String cell = cellOf(lat, lng);
            String demandKey = DEMAND_PREFIX + cell;

            if (recordDemand) {
                Long count = redis.opsForValue().increment(demandKey);
                if (count != null && count == 1L) {
                    redis.expire(demandKey, DEMAND_WINDOW); // the 5-minute window starts at the first request
                }
            }

            String cached = redis.opsForValue().get(MULTIPLIER_PREFIX + cell);
            if (cached != null) {
                return new BigDecimal(cached);
            }

            long demand = parseLong(redis.opsForValue().get(demandKey));
            long supply = geoIndex.countNearby(lat, lng, SUPPLY_RADIUS_KM);
            BigDecimal multiplier = computeMultiplier(demand, supply);
            redis.opsForValue().set(MULTIPLIER_PREFIX + cell, multiplier.toPlainString(), CACHE_TTL);
            return multiplier;
        } catch (RuntimeException e) {
            log.warn("Surge pricing unavailable, charging normal fare: {}", e.getMessage());
            return NO_SURGE;
        }
    }

    /** The pure formula, kept static and side-effect free so it is trivial to unit test. */
    public static BigDecimal computeMultiplier(long demand, long supply) {
        double ratio = (double) demand / Math.max(1L, supply);
        if (ratio <= 1.0) {
            return NO_SURGE;
        }
        BigDecimal multiplier = BigDecimal.valueOf(1.0 + 0.25 * (ratio - 1.0)).setScale(2, RoundingMode.HALF_UP);
        return multiplier.min(MAX_SURGE);
    }

    private static String cellOf(double lat, double lng) {
        return String.format(Locale.ROOT, "%.2f:%.2f", lat, lng);
    }

    private static long parseLong(String value) {
        return value == null ? 0L : Long.parseLong(value);
    }
}
