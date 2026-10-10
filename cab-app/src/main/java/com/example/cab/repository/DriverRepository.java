package com.example.cab.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.cab.model.Driver;
import com.example.cab.model.DriverStatus;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    boolean existsByPhone(String phone);

    /** Used at startup to rebuild the Redis geo-index from the database. */
    List<Driver> findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(DriverStatus status);

    /**
     * CONCEPT: the "naive" nearest-driver search, written in SQL (Haversine formula).
     * It scans every AVAILABLE driver. We keep it as (1) the fallback when Redis is down and
     * (2) the baseline we benchmark Redis against in the load test (cab.matching.strategy=sql).
     */
    @Query(value = """
            SELECT * FROM drivers
            WHERE status = 'AVAILABLE' AND latitude IS NOT NULL AND longitude IS NOT NULL
              AND 6371 * acos(LEAST(1.0, GREATEST(-1.0,
                    cos(radians(:lat)) * cos(radians(latitude)) * cos(radians(longitude) - radians(:lng))
                  + sin(radians(:lat)) * sin(radians(latitude))))) <= :radiusKm
            ORDER BY 6371 * acos(LEAST(1.0, GREATEST(-1.0,
                    cos(radians(:lat)) * cos(radians(latitude)) * cos(radians(longitude) - radians(:lng))
                  + sin(radians(:lat)) * sin(radians(latitude))))) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Driver> findNearbyAvailable(@Param("lat") double lat,
                                     @Param("lng") double lng,
                                     @Param("radiusKm") double radiusKm,
                                     @Param("limit") int limit);

    /**
     * CONCEPT: atomic compare-and-swap in SQL.
     * "Change status from X to Y ONLY IF it is still X." Returns 1 if we won, 0 if someone else got
     * there first. PostgreSQL locks the row during the UPDATE, so even two requests in the same
     * millisecond cannot both win. This is what prevents one driver being given to two riders.
     * ALTERNATIVES: Redis distributed lock (SETNX), SELECT ... FOR UPDATE (pessimistic lock).
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE drivers SET status = :newStatus, version = version + 1 WHERE id = :id AND status = :expectedStatus",
            nativeQuery = true)
    int transitionStatus(@Param("id") Long id, @Param("expectedStatus") String expectedStatus, @Param("newStatus") String newStatus);

    /** Atomic rating update (no read-modify-write race). */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE drivers SET rating_count = rating_count + 1, rating_sum = rating_sum + :score "
            + "WHERE id = :id", nativeQuery = true)
    int addRating(@Param("id") Long id, @Param("score") int score);
}
