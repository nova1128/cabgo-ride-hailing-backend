package com.example.cab.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cab.model.Trip;
import com.example.cab.model.TripStatus;

public interface TripRepository extends JpaRepository<Trip, Long> {

    /** "Does this rider already have a ride in progress?" */
    boolean existsByRiderIdAndStatusIn(Long riderId, Collection<TripStatus> statuses);

    // Spring Data builds these queries from the method names: "top 20 trips of this rider, newest first".
    List<Trip> findTop20ByRiderIdOrderByIdDesc(Long riderId);

    List<Trip> findTop20ByDriverIdOrderByIdDesc(Long driverId);
}
