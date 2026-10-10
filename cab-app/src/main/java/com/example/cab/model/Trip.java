package com.example.cab.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.example.cab.exception.ConflictException;

/** One ride, from request to completion. */
@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Stored as plain IDs (not JPA relations) so Trip does not depend on the internals of the
    // Rider/Driver modules. This is how services that own their own data reference each other.
    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Column(name = "driver_id")
    private Long driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status = TripStatus.REQUESTED;

    @Column(name = "pickup_lat", nullable = false)
    private Double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private Double pickupLng;

    @Column(name = "drop_lat", nullable = false)
    private Double dropLat;

    @Column(name = "drop_lng", nullable = false)
    private Double dropLng;

    @Column(precision = 10, scale = 2)
    private BigDecimal fare;

    @Column(name = "surge_multiplier", precision = 4, scale = 2)
    private BigDecimal surgeMultiplier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // Optimistic locking: if two requests change the same trip at once (e.g. cancel + complete),
    // the second one fails with HTTP 409 instead of silently overwriting the first.
    @Version
    private Long version;

    protected Trip() {
        // required by JPA
    }

    public Trip(Long riderId, Double pickupLat, Double pickupLng, Double dropLat, Double dropLng) {
        this.riderId = riderId;
        this.pickupLat = pickupLat;
        this.pickupLng = pickupLng;
        this.dropLat = dropLat;
        this.dropLng = dropLng;
    }

    /** The ONLY way the status changes, so illegal jumps (e.g. COMPLETED -> IN_PROGRESS) are impossible. */
    public void transitionTo(TripStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new ConflictException("Cannot move trip from " + status + " to " + next);
        }
        this.status = next;
    }

    public void assignDriver(Long driverId) {
        this.driverId = driverId;
        transitionTo(TripStatus.DRIVER_ASSIGNED);
    }

    public Long getId() { return id; }
    public Long getRiderId() { return riderId; }
    public Long getDriverId() { return driverId; }
    public TripStatus getStatus() { return status; }
    public Double getPickupLat() { return pickupLat; }
    public Double getPickupLng() { return pickupLng; }
    public Double getDropLat() { return dropLat; }
    public Double getDropLng() { return dropLng; }
    public BigDecimal getFare() { return fare; }
    public BigDecimal getSurgeMultiplier() { return surgeMultiplier; }
    public Instant getCreatedAt() { return createdAt; }

    public void setFare(BigDecimal fare) { this.fare = fare; }
    public void setSurgeMultiplier(BigDecimal surgeMultiplier) { this.surgeMultiplier = surgeMultiplier; }
}
