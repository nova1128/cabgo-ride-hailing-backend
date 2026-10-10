package com.example.cab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.example.cab.model.Role;

/** Registration body. Bean Validation annotations reject bad input before any code runs (HTTP 400). */
public record RegisterRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "\\d{10}", message = "must be exactly 10 digits") String phone,
        @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password,
        @NotNull Role role,
        String vehicleNumber) {
}
