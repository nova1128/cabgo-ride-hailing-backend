package com.example.cab.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.cab.model.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    boolean existsByPhone(String phone);

    Optional<AppUser> findByPhone(String phone);
}
