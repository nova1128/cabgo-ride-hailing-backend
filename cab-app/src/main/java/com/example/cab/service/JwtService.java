package com.example.cab.service;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.example.cab.model.AppUser;

/**
 * CONCEPT: creates the signed JWT returned at login. The token carries: subject = user id, role, profileId,
 * issued/expiry time (1 hour). The signature stops anyone from editing these claims.
 */
@Service
public class JwtService {

    private static final long TTL_SECONDS = 3600; // tokens last 1 hour

    private final JwtEncoder encoder;

    public JwtService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public long ttlSeconds() {
        return TTL_SECONDS;
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(TTL_SECONDS))
                .claim("role", user.getRole().name())
                .claim("profileId", user.getProfileId())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
