package com.example.cab.security;

import org.springframework.security.oauth2.jwt.Jwt;

import com.example.cab.model.Role;

/** The logged-in caller, read from the verified JWT. */
public record CurrentUser(Long userId, Role role, Long profileId) {

    public static CurrentUser from(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        Number profileId = jwt.getClaim("profileId");
        return new CurrentUser(Long.valueOf(jwt.getSubject()), Role.valueOf(role), profileId.longValue());
    }
}
