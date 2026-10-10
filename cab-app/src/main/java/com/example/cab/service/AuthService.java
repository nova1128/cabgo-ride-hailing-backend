package com.example.cab.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.cab.dto.AuthResponse;
import com.example.cab.dto.LoginRequest;
import com.example.cab.dto.RegisterDriverRequest;
import com.example.cab.dto.RegisterRequest;
import com.example.cab.dto.RegisterRiderRequest;
import com.example.cab.exception.BadRequestException;
import com.example.cab.exception.ConflictException;
import com.example.cab.exception.UnauthorizedException;
import com.example.cab.model.AppUser;
import com.example.cab.model.Driver;
import com.example.cab.model.Rider;
import com.example.cab.model.Role;
import com.example.cab.repository.AppUserRepository;

/**
 * Registration and login.
 * Registering creates TWO things in one transaction: a login identity (AppUser) and a profile
 * (Rider or Driver). If either fails, both are rolled back, so we never get half-created accounts.
 */
@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final RiderService riderService;
    private final DriverService driverService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;

    public AuthService(AppUserRepository userRepository, RiderService riderService,
                       DriverService driverService, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RateLimitService rateLimitService) {
        this.userRepository = userRepository;
        this.riderService = riderService;
        this.driverService = driverService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimitService = rateLimitService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByPhone(request.phone())) {
            throw new ConflictException("This phone is already registered");
        }

        Long profileId;
        if (request.role() == Role.RIDER) {
            profileId = riderService.register(new RegisterRiderRequest(request.name(), request.phone())).id();
        } else {
            if (request.vehicleNumber() == null || request.vehicleNumber().isBlank()) {
                throw new BadRequestException("vehicleNumber is required for drivers");
            }
            profileId = driverService.register(
                    new RegisterDriverRequest(request.name(), request.phone(), request.vehicleNumber())).id();
        }

        // Only the BCrypt hash is stored, never the password itself.
        AppUser user = userRepository.save(new AppUser(
                request.phone(), passwordEncoder.encode(request.password()), request.role(), profileId));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Brute-force protection: max 5 login attempts per minute per phone number.
        rateLimitService.check("login", request.phone());

        AppUser user = userRepository.findByPhone(request.phone())
                .orElseThrow(() -> new UnauthorizedException("Invalid phone or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // Same message for "unknown phone" and "wrong password" so attackers can't tell which phones exist.
            throw new UnauthorizedException("Invalid phone or password");
        }
        return toResponse(user);
    }

    private AuthResponse toResponse(AppUser user) {
        return new AuthResponse(jwtService.issue(user), "Bearer", jwtService.ttlSeconds(),
                user.getRole(), user.getProfileId());
    }
}
