package com.example.cab.dto;

import com.example.cab.model.Driver;
import com.example.cab.model.DriverStatus;

public record DriverResponse(
        Long id,
        String name,
        String phone,
        String vehicleNumber,
        DriverStatus status,
        Double latitude,
        Double longitude,
        double ratingAverage,
        int ratingCount) {

    public static DriverResponse from(Driver d) {
        // average rounded to 1 decimal; 0.0 means "not rated yet"
        double average = d.getRatingCount() == 0
                ? 0.0
                : Math.round(d.getRatingSum() * 10.0 / d.getRatingCount()) / 10.0;
        return new DriverResponse(d.getId(), d.getName(), d.getPhone(), d.getVehicleNumber(),
                d.getStatus(), d.getLatitude(), d.getLongitude(), average, d.getRatingCount());
    }
}
