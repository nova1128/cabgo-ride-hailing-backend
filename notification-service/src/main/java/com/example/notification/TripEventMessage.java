package com.example.notification;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * This service's OWN copy of the event shape (the JSON the main app publishes).
 * It is deliberately NOT shared code: services agree on the JSON "contract", not on Java classes,
 * so they can be built and deployed separately.
 */
public record TripEventMessage(
        String type,
        Long tripId,
        Long riderId,
        Long driverId,
        String status,
        BigDecimal amount,
        Instant occurredAt) {
}
