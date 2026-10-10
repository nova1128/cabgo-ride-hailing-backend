package com.example.cab.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.cab.model.Trip;
import com.example.cab.model.TripStatus;

/**
 * What the API returns for a trip. It is a flat "view" combining data from several modules
 * (trip + rider name + driver details + payment status + rating) so the frontend needs only one call.
 */
public record TripResponse(
        Long id,
        TripStatus status,
        Long riderId,
        String riderName,
        Long driverId,
        String driverName,
        String driverVehicle,
        double driverRating,
        Double driverLat,
        Double driverLng,
        Double pickupLat,
        Double pickupLng,
        Double dropLat,
        Double dropLng,
        BigDecimal fare,
        BigDecimal surgeMultiplier,
        String paymentStatus,
        Integer ratingScore,
        Instant createdAt) {

    public static TripResponse from(Trip t, String riderName, DriverResponse driver,
                                    String paymentStatus, Integer ratingScore) {
        return new TripResponse(
                t.getId(), t.getStatus(), t.getRiderId(), riderName,
                t.getDriverId(),
                driver == null ? null : driver.name(),
                driver == null ? null : driver.vehicleNumber(),
                driver == null ? 0.0 : driver.ratingAverage(),
                driver == null ? null : driver.latitude(),
                driver == null ? null : driver.longitude(),
                t.getPickupLat(), t.getPickupLng(), t.getDropLat(), t.getDropLng(),
                t.getFare(), t.getSurgeMultiplier(), paymentStatus, ratingScore, t.getCreatedAt());
    }
}
