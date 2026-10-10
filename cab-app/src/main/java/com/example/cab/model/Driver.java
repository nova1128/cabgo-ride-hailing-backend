package com.example.cab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A driver profile. This row is the SOURCE OF TRUTH for status; Redis only holds a fast copy of positions. */
@Entity
@Table(name = "drivers")
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(name = "vehicle_number", nullable = false)
    private String vehicleNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriverStatus status = DriverStatus.OFFLINE;

    private Double latitude;
    private Double longitude;

    // Ratings are stored as count + sum (average = sum / count) so each new rating is ONE atomic SQL
    // "UPDATE ... SET count = count + 1, sum = sum + x" with no read-modify-write race.
    @Column(name = "rating_count", nullable = false)
    private int ratingCount = 0;

    @Column(name = "rating_sum", nullable = false)
    private long ratingSum = 0;

    // Optimistic locking: Hibernate bumps this on every update and rejects stale writes (HTTP 409).
    @Version
    private Long version;

    protected Driver() {
        // required by JPA
    }

    public Driver(String name, String phone, String vehicleNumber) {
        this.name = name;
        this.phone = phone;
        this.vehicleNumber = vehicleNumber;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getVehicleNumber() { return vehicleNumber; }
    public DriverStatus getStatus() { return status; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public int getRatingCount() { return ratingCount; }
    public long getRatingSum() { return ratingSum; }

    public void setStatus(DriverStatus status) { this.status = status; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
}
