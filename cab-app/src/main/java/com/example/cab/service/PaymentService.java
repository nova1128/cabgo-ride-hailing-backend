package com.example.cab.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cab.dto.PaymentResponse;
import com.example.cab.event.TripEvent;
import com.example.cab.exception.ConflictException;
import com.example.cab.exception.ForbiddenException;
import com.example.cab.exception.NotFoundException;
import com.example.cab.gateway.PaymentGateway;
import com.example.cab.model.Payment;
import com.example.cab.model.PaymentStatus;
import com.example.cab.repository.PaymentRepository;

/**
 * CONCEPT: idempotent payments.
 * Networks retry and users double-click. The client sends an "Idempotency-Key" header (one random value
 * per payment attempt). Rules:
 *   - same key again after success  -> returns the SAME payment, charges nothing more (safe retry)
 *   - different key after success   -> 409 "already paid"
 *   - concurrent double-submit      -> the @Version check on Payment rejects the second one
 * ALTERNATIVES: a dedicated idempotency-keys table, Redis SETNX with TTL.
 */
@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final PaymentGateway gateway;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(PaymentRepository repository, PaymentGateway gateway,
                          ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.gateway = gateway;
        this.eventPublisher = eventPublisher;
    }

    /** Called by TripService when a trip completes. Safe to call twice (it will not create a duplicate). */
    @Transactional
    public void createPending(Long tripId, Long riderId, Long driverId, BigDecimal amount) {
        if (repository.findByTripId(tripId).isPresent()) {
            return;
        }
        repository.save(new Payment(tripId, riderId, driverId, amount));
    }

    @Transactional
    public PaymentResponse pay(Long tripId, Long riderId, String idempotencyKey) {
        Payment payment = repository.findByTripId(tripId)
                .orElseThrow(() -> new NotFoundException("There is no payment due for this trip yet"));
        if (!Objects.equals(payment.getRiderId(), riderId)) {
            throw new ForbiddenException("This is not your trip");
        }

        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            if (Objects.equals(payment.getIdempotencyKey(), idempotencyKey)) {
                return PaymentResponse.from(payment); // safe replay of the same request
            }
            throw new ConflictException("This trip has already been paid");
        }

        String chargeId = gateway.charge(idempotencyKey, payment.getAmount());
        payment.markCaptured(idempotencyKey, chargeId);

        // Tell other services (e.g. notifications). Sent to Kafka only after this transaction commits.
        eventPublisher.publishEvent(new TripEvent("PAYMENT_CAPTURED", tripId, riderId, payment.getDriverId(),
                PaymentStatus.CAPTURED.name(), payment.getAmount(), Instant.now()));
        return PaymentResponse.from(payment);
    }

    /** "PENDING", "CAPTURED" or null when no payment exists yet. */
    @Transactional(readOnly = true)
    public String statusOf(Long tripId) {
        return repository.findByTripId(tripId).map(p -> p.getStatus().name()).orElse(null);
    }
}
