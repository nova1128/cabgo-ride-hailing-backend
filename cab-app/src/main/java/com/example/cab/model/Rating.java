package com.example.cab.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A rider's 1-5 star rating of the driver for ONE trip (trip_id is unique: one rating per trip). */
@Entity
@Table(name = "ratings")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false, unique = true)
    private Long tripId;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(nullable = false)
    private int score;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Rating() {
        // required by JPA
    }

    public Rating(Long tripId, Long riderId, Long driverId, int score, String comment) {
        this.tripId = tripId;
        this.riderId = riderId;
        this.driverId = driverId;
        this.score = score;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public int getScore() { return score; }
}
