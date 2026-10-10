package com.example.cab.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cab.model.Rating;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    Optional<Rating> findByTripId(Long tripId);

    boolean existsByTripId(Long tripId);
}
