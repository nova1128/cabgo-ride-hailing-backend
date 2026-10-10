package com.example.cab.repository;

import java.util.List;

import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * CONCEPT: geospatial index (Redis GEO).
 * PROBLEM: "find the nearest free drivers" with SQL means computing the distance to EVERY driver on every
 *          request (a full table scan). That gets slow as drivers grow.
 * SOLUTION: Redis stores positions in a sorted set (geohash scores in memory), so a radius query only
 *          touches nearby entries and returns them already sorted by distance.
 *
 * IMPORTANT DESIGN RULE: this index is only a FAST COPY ("derived data"). PostgreSQL is the source of truth.
 * Only AVAILABLE drivers are kept in it. If Redis loses data or goes stale, matching still stays correct
 * because every candidate is re-checked in PostgreSQL by DriverRepository.transitionStatus(...).
 *
 * ALTERNATIVES: PostGIS (geo queries inside Postgres), Uber H3 hexagon grid, geohash prefix search,
 *               a quadtree kept in application memory.
 */
@Component
public class DriverGeoIndex {

    /** Redis key of the sorted set that holds every AVAILABLE driver's position. */
    public static final String KEY = "drivers:available";

    private final StringRedisTemplate redis;

    public DriverGeoIndex(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Adds the driver, or moves them if they are already in the index (GEOADD is an upsert). */
    public void update(Long driverId, double lat, double lng) {
        // NOTE: Redis (and Spring's Point) use x = longitude, y = latitude: longitude comes FIRST.
        redis.opsForGeo().add(KEY, new Point(lng, lat), String.valueOf(driverId));
    }

    public void remove(Long driverId) {
        redis.opsForGeo().remove(KEY, String.valueOf(driverId));
    }

    /** Wipes the whole index. Used once at startup before rebuilding it from the database. */
    public void clear() {
        redis.delete(KEY);
    }

    /** IDs of drivers within radiusKm of the point, nearest first, at most `limit` of them. */
    public List<Long> findNearby(double lat, double lng, double radiusKm, int limit) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().radius(
                KEY,
                new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().sortAscending().limit(limit));
        if (results == null) {
            return List.of();
        }
        return results.getContent().stream()
                .map(result -> Long.valueOf(result.getContent().getName()))
                .toList();
    }

    /** How many available drivers are near this point (the "supply" side of surge pricing). */
    public long countNearby(double lat, double lng, double radiusKm) {
        return findNearby(lat, lng, radiusKm, 100).size();
    }
}
