package com.example.cab.dto;

/** A driver id plus a position. Used to rebuild the Redis index at startup. */
public record DriverLocation(Long id, double latitude, double longitude) {
}
