package com.example.cab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRiderRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "\\d{10}", message = "must be exactly 10 digits") String phone) {
}
