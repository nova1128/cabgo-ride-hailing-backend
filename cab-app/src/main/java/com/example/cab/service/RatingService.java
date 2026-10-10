package com.example.cab.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cab.exception.ConflictException;
import com.example.cab.model.Rating;
import com.example.cab.model.Trip;
import com.example.cab.repository.RatingRepository;

/**
 * Stores ratings. It is a "leaf" service on purpose: it does not call the Trip module back, which avoids
 * circular dependencies. TripService does the checks (is the trip completed? is the caller the rider?)
 * and then calls this class.
 */
@Service
public class RatingService {

    private final RatingRepository repository;

    public RatingService(RatingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void save(Long tripId, Long riderId, Long driverId, int score, String comment) {
        if (repository.existsByTripId(tripId)) {
            throw new ConflictException("This trip has already been rated");
        }
        repository.save(new Rating(tripId, riderId, driverId, score, comment));
    }

    /** The stored score for a trip, or null if it has not been rated. */
    @Transactional(readOnly = true)
    public Integer scoreOf(Long tripId) {
        return repository.findByTripId(tripId).map(Rating::getScore).orElse(null);
    }
}
