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

import com.example.cab.service.PaymentService;

/**
 * One payment per trip (trip_id is UNIQUE, so a trip can never be charged twice even if a bug or a
 * double-click tries). The payment starts PENDING when the trip completes and becomes CAPTURED when paid.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false, unique = true)
    private Long tripId;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    // The client-supplied Idempotency-Key that captured this payment (see PaymentService.pay).
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "gateway_charge_id")
    private String gatewayChargeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    @Version
    private Long version;

    protected Payment() {
        // required by JPA
    }

    public Payment(Long tripId, Long riderId, Long driverId, BigDecimal amount) {
        this.tripId = tripId;
        this.riderId = riderId;
        this.driverId = driverId;
        this.amount = amount;
    }

    public void markCaptured(String idempotencyKey, String gatewayChargeId) {
        this.status = PaymentStatus.CAPTURED;
        this.idempotencyKey = idempotencyKey;
        this.gatewayChargeId = gatewayChargeId;
        this.paidAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getTripId() { return tripId; }
    public Long getRiderId() { return riderId; }
    public Long getDriverId() { return driverId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getGatewayChargeId() { return gatewayChargeId; }
    public Instant getPaidAt() { return paidAt; }
}
