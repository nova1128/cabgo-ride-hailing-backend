package com.example.cab.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;

import com.example.cab.dto.CreateTripRequest;
import com.example.cab.dto.DriverResponse;
import com.example.cab.dto.FareQuote;
import com.example.cab.dto.RatingRequest;
import com.example.cab.dto.TripResponse;
import com.example.cab.event.TripEvent;
import com.example.cab.event.TripEventPublisher;
import com.example.cab.exception.ConflictException;
import com.example.cab.exception.ForbiddenException;
import com.example.cab.exception.NotFoundException;
import com.example.cab.model.Role;
import com.example.cab.model.Trip;
import com.example.cab.model.TripStatus;
import com.example.cab.repository.TripRepository;
import com.example.cab.security.CurrentUser;

/**
 * The heart of the system: orchestrates a ride from request to completion.
 * It calls the other modules (rider, driver, pricing, payment, rating) only through their Service classes.
 *
 * Every public method is @Transactional: all its database changes succeed together or are rolled back together.
 */
@Service
public class TripService {

    private static final double MATCH_RADIUS_KM = 5.0;   // how far from the pickup we look for drivers
    private static final int MAX_CANDIDATES = 5;          // how many nearby drivers we try, nearest first
    private static final List<TripStatus> ACTIVE_STATUSES =
            List.of(TripStatus.REQUESTED, TripStatus.DRIVER_ASSIGNED, TripStatus.IN_PROGRESS);

    private final TripRepository repository;
    private final RiderService riderService;
    private final DriverService driverService;
    private final PricingService pricingService;
    private final PaymentService paymentService;
    private final RatingService ratingService;
    private final RateLimitService rateLimitService;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    public TripService(TripRepository repository, RiderService riderService, DriverService driverService,
                       PricingService pricingService, PaymentService paymentService,
                       RatingService ratingService, RateLimitService rateLimitService,
                       ApplicationEventPublisher eventPublisher, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.riderService = riderService;
        this.driverService = driverService;
        this.pricingService = pricingService;
        this.paymentService = paymentService;
        this.ratingService = ratingService;
        this.rateLimitService = rateLimitService;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    /** Price preview shown before the rider confirms. Does not create anything and does not count as demand. */
    public FareQuote quote(CreateTripRequest request) {
        return pricingService.preview(request.pickupLat(), request.pickupLng(), request.dropLat(), request.dropLng());
    }

    /**
     * Request a ride:
     *  1. rate-limit and validate  2. price it (with surge)  3. find nearby drivers (Redis, SQL fallback)
     *  4. atomically claim the nearest one that is still free  5. save  6. publish an event for notifications.
     */
    @Transactional
    public TripResponse create(Long riderId, CreateTripRequest request) {
        // Max 30 ride requests per minute per rider (see resilience4j.ratelimiter.configs.trip).
        rateLimitService.check("trip", String.valueOf(riderId));

        riderService.requireExists(riderId);
        if (repository.existsByRiderIdAndStatusIn(riderId, ACTIVE_STATUSES)) {
            throw new ConflictException("You already have an active trip");
        }

        FareQuote quote = pricingService.quote(
                request.pickupLat(), request.pickupLng(), request.dropLat(), request.dropLng());
        Trip trip = new Trip(riderId, request.pickupLat(), request.pickupLng(),
                request.dropLat(), request.dropLng());
        trip.setFare(quote.total());
        trip.setSurgeMultiplier(quote.surgeMultiplier());

        // Nearby candidates come from Redis (a fast HINT). The claim below is the real decision:
        // it succeeds for only ONE request even if many riders pick the same driver at the same moment.
        Long assignedDriverId = null;
        List<Long> candidates = driverService.findNearbyAvailableDriverIds(
                request.pickupLat(), request.pickupLng(), MATCH_RADIUS_KM, MAX_CANDIDATES);
        for (Long candidateId : candidates) {
            if (driverService.tryClaim(candidateId)) {
                assignedDriverId = candidateId;
                break;
            }
        }

        String eventType;
        if (assignedDriverId != null) {
            trip.assignDriver(assignedDriverId);
            eventType = "TRIP_DRIVER_ASSIGNED";
            meterRegistry.counter("cab.trips", "outcome", "assigned").increment();
        } else {
            trip.transitionTo(TripStatus.NO_DRIVER_FOUND);
            eventType = "TRIP_NO_DRIVER_FOUND";
            meterRegistry.counter("cab.trips", "outcome", "no_driver").increment();
        }

        Trip saved = repository.save(trip);
        publish(eventType, saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TripResponse get(Long id, CurrentUser user) {
        Trip trip = findOrThrow(id);
        requireParticipant(trip, user);
        return toResponse(trip);
    }

    /** The caller's 20 most recent trips (rider's own rides, or the rides assigned to the driver). */
    @Transactional(readOnly = true)
    public List<TripResponse> mine(CurrentUser user) {
        List<Trip> trips = user.role() == Role.RIDER
                ? repository.findTop20ByRiderIdOrderByIdDesc(user.profileId())
                : repository.findTop20ByDriverIdOrderByIdDesc(user.profileId());
        return trips.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TripResponse start(Long id, CurrentUser user) {
        Trip trip = findOrThrow(id);
        requireAssignedDriver(trip, user);
        trip.transitionTo(TripStatus.IN_PROGRESS);
        publish("TRIP_STARTED", trip);
        return toResponse(trip);
    }

    @Transactional
    public TripResponse complete(Long id, CurrentUser user) {
        Trip trip = findOrThrow(id);
        requireAssignedDriver(trip, user);
        trip.transitionTo(TripStatus.COMPLETED);
        driverService.release(trip.getDriverId());      // driver becomes AVAILABLE (and re-enters the Redis index)
        paymentService.createPending(trip.getId(), trip.getRiderId(), trip.getDriverId(), trip.getFare());
        meterRegistry.counter("cab.trips", "outcome", "completed").increment();
        publish("TRIP_COMPLETED", trip);
        return toResponse(trip);
    }

    @Transactional
    public TripResponse cancel(Long id, CurrentUser user) {
        Trip trip = findOrThrow(id);
        requireParticipant(trip, user);
        Long driverId = trip.getDriverId();
        trip.transitionTo(TripStatus.CANCELLED);
        if (driverId != null) {
            driverService.release(driverId);
        }
        meterRegistry.counter("cab.trips", "outcome", "cancelled").increment();
        publish("TRIP_CANCELLED", trip);
        return toResponse(trip);
    }

    /** The rider rates the driver after a completed trip. One rating per trip. */
    @Transactional
    public TripResponse rate(Long id, CurrentUser user, RatingRequest request) {
        Trip trip = findOrThrow(id);
        if (user.role() != Role.RIDER || !Objects.equals(trip.getRiderId(), user.profileId())) {
            throw new ForbiddenException("Only the rider of this trip can rate it");
        }
        if (trip.getStatus() != TripStatus.COMPLETED) {
            throw new ConflictException("You can only rate a completed trip");
        }
        ratingService.save(trip.getId(), trip.getRiderId(), trip.getDriverId(),
                request.score(), request.comment());
        driverService.applyRating(trip.getDriverId(), request.score());
        return toResponse(trip);
    }

    // ------------------------------------------------------------------ helpers

    private Trip findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Trip " + id + " not found"));
    }

    /** Only the driver who was assigned to the trip may start or complete it. */
    private void requireAssignedDriver(Trip trip, CurrentUser user) {
        if (user.role() != Role.DRIVER || !Objects.equals(trip.getDriverId(), user.profileId())) {
            throw new ForbiddenException("Only the assigned driver can do this");
        }
    }

    /** Only the rider or the assigned driver may see or cancel a trip (ownership check). */
    private void requireParticipant(Trip trip, CurrentUser user) {
        boolean isRider = user.role() == Role.RIDER && Objects.equals(trip.getRiderId(), user.profileId());
        boolean isDriver = user.role() == Role.DRIVER && Objects.equals(trip.getDriverId(), user.profileId());
        if (!isRider && !isDriver) {
            throw new ForbiddenException("You are not part of this trip");
        }
    }

    /** Queues a Kafka event; it is actually sent only after this transaction commits (see TripEventPublisher). */
    private void publish(String type, Trip trip) {
        eventPublisher.publishEvent(new TripEvent(type, trip.getId(), trip.getRiderId(), trip.getDriverId(),
                trip.getStatus().name(), trip.getFare(), Instant.now()));
    }

    private TripResponse toResponse(Trip trip) {
        String riderName = riderService.get(trip.getRiderId()).name();
        DriverResponse driver = trip.getDriverId() == null ? null : driverService.get(trip.getDriverId());
        return TripResponse.from(trip, riderName, driver,
                paymentService.statusOf(trip.getId()), ratingService.scoreOf(trip.getId()));
    }
}
