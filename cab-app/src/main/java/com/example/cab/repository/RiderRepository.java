package com.example.cab.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cab.model.Rider;

public interface RiderRepository extends JpaRepository<Rider, Long> {
    boolean existsByPhone(String phone);
}
