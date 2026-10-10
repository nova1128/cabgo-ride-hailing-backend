package com.example.cab.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cab.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTripId(Long tripId);
}
