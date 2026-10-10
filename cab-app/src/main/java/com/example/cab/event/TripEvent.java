package com.example.cab.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * CONCEPT: a domain event, "something that happened". Published to Kafka as JSON so OTHER services
 * (like notification-service) can react without the trip code knowing they exist.
 *
 * type examples: TRIP_DRIVER_ASSIGNED, TRIP_NO_DRIVER_FOUND, TRIP_STARTED, TRIP_COMPLETED,
 *                TRIP_CANCELLED, PAYMENT_CAPTURED
 */
public record TripEvent(
        String type,
        Long tripId,
        Long riderId,
        Long driverId,
        String status,
        BigDecimal amount,
        Instant occurredAt) {
}
