package com.example.cab.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.cab.dto.DriverLocation;
import com.example.cab.dto.DriverResponse;
import com.example.cab.dto.RegisterDriverRequest;
import com.example.cab.dto.UpdateLocationRequest;
import com.example.cab.dto.UpdateStatusRequest;
import com.example.cab.exception.BadRequestException;
import com.example.cab.exception.ConflictException;
import com.example.cab.exception.NotFoundException;
import com.example.cab.model.Driver;
import com.example.cab.model.DriverStatus;
import com.example.cab.model.Trip;
import com.example.cab.repository.DriverGeoIndex;
import com.example.cab.repository.DriverRepository;

/**
 * The public face of the driver module. Other modules (trip, auth) call these methods and never
 * touch DriverRepository directly.
 *
 * KEEPING REDIS IN SYNC: PostgreSQL is the source of truth; Redis is a fast copy of the positions of
 * AVAILABLE drivers. Redis is updated only AFTER the database transaction commits (afterCommit below),
 * so Redis can never show a change that the database later rolled back.
 */
@Service
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    private final DriverRepository repository;
    private final DriverGeoIndex geoIndex;
    private final DriverMatchingService matchingService;

    public DriverService(DriverRepository repository, DriverGeoIndex geoIndex,
                         DriverMatchingService matchingService) {
        this.repository = repository;
        this.geoIndex = geoIndex;
        this.matchingService = matchingService;
    }

    // ---------------------------------------------------------------- profile

    @Transactional
    public DriverResponse register(RegisterDriverRequest request) {
        if (repository.existsByPhone(request.phone())) {
            throw new ConflictException("A driver with this phone already exists");
        }
        Driver saved = repository.save(new Driver(request.name(), request.phone(), request.vehicleNumber()));
        return DriverResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DriverResponse get(Long id) {
        return DriverResponse.from(findOrThrow(id));
    }

    // ---------------------------------------------------------------- live location & status

    /** Called by the driver app every few seconds. */
    @Transactional
    public DriverResponse updateLocation(Long id, UpdateLocationRequest request) {
        Driver driver = findOrThrow(id);
        driver.setLatitude(request.latitude());
        driver.setLongitude(request.longitude());
        // Only AVAILABLE drivers live in the Redis index (busy/offline drivers must not be matched).
        if (driver.getStatus() == DriverStatus.AVAILABLE) {
            afterCommit(() -> geoIndex.update(id, request.latitude(), request.longitude()));
        }
        return DriverResponse.from(driver);
    }

    @Transactional
    public DriverResponse updateStatus(Long id, UpdateStatusRequest request) {
        Driver driver = findOrThrow(id);
        DriverStatus requested = request.status();

        // Business rules (kept in the service layer so they cannot be bypassed by a different controller).
        if (requested == DriverStatus.ON_TRIP) {
            throw new BadRequestException("ON_TRIP is set by the system when a trip is assigned");
        }
        if (driver.getStatus() == DriverStatus.ON_TRIP) {
            throw new BadRequestException("Driver is on a trip; finish the trip first");
        }
        if (requested == DriverStatus.AVAILABLE
                && (driver.getLatitude() == null || driver.getLongitude() == null)) {
            throw new BadRequestException("Send a location before going AVAILABLE");
        }

        driver.setStatus(requested);
        if (requested == DriverStatus.AVAILABLE) {
            double lat = driver.getLatitude();
            double lng = driver.getLongitude();
            afterCommit(() -> geoIndex.update(id, lat, lng));
        } else {
            afterCommit(() -> geoIndex.remove(id));
        }
        return DriverResponse.from(driver);
    }

    // ---------------------------------------------------------------- used by the Trip module (matching)

    /** Nearest AVAILABLE drivers (ids), via Redis with automatic SQL fallback. */
    public List<Long> findNearbyAvailableDriverIds(double lat, double lng, double radiusKm, int limit) {
        return matchingService.findNearbyDriverIds(lat, lng, radiusKm, limit);
    }

    /**
     * Try to reserve a driver for a trip. Returns true ONLY for the single caller that wins the race
     * (see DriverRepository.transitionStatus). Redis is a hint, this is the real decision.
     */
    @Transactional
    public boolean tryClaim(Long driverId) {
        boolean claimed = repository.transitionStatus(
                driverId, DriverStatus.AVAILABLE.name(), DriverStatus.ON_TRIP.name()) == 1;
        if (claimed) {
            afterCommit(() -> geoIndex.remove(driverId)); // busy drivers must not appear in searches
        }
        return claimed;
    }

    /** Make a driver available again after a trip ends or is cancelled. Safe to call twice. */
    @Transactional
    public void release(Long driverId) {
        boolean released = repository.transitionStatus(
                driverId, DriverStatus.ON_TRIP.name(), DriverStatus.AVAILABLE.name()) == 1;
        if (released) {
            Driver driver = findOrThrow(driverId);
            if (driver.getLatitude() != null && driver.getLongitude() != null) {
                double lat = driver.getLatitude();
                double lng = driver.getLongitude();
                afterCommit(() -> geoIndex.update(driverId, lat, lng));
            }
        }
    }

    /** Atomic: add one rating to the driver's running total. */
    @Transactional
    public void applyRating(Long driverId, int score) {
        if (repository.addRating(driverId, score) == 0) {
            throw new NotFoundException("Driver " + driverId + " not found");
        }
    }

    /** Positions of all AVAILABLE drivers; used to rebuild Redis at startup. */
    @Transactional(readOnly = true)
    public List<DriverLocation> findAvailableLocations() {
        return repository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(DriverStatus.AVAILABLE).stream()
                .map(d -> new DriverLocation(d.getId(), d.getLatitude(), d.getLongitude()))
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private Driver findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Driver " + id + " not found"));
    }

    /**
     * Runs the action only after the surrounding database transaction has COMMITTED.
     * If we updated Redis immediately and the transaction later rolled back, Redis would be wrong.
     * Redis problems are logged but never fail the request: the database is still correct.
     */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(action);
                }
            });
        } else {
            runSafely(action);
        }
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Redis index update failed (database is still correct): {}", e.getMessage());
        }
    }
}
