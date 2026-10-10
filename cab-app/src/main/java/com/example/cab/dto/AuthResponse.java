package com.example.cab.dto;

import com.example.cab.model.Role;

public record AuthResponse(String token, String tokenType, long expiresInSeconds, Role role, Long profileId) {
}
