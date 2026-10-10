package com.example.cab.dto;

import jakarta.validation.constraints.NotNull;

import com.example.cab.model.DriverStatus;

public record UpdateStatusRequest(@NotNull DriverStatus status) {
}
