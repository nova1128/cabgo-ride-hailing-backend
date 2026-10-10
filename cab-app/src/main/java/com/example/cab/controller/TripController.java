package com.example.cab.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.example.cab.config.SecurityConfig;
import com.example.cab.dto.CreateTripRequest;
import com.example.cab.dto.FareQuote;
import com.example.cab.dto.RatingRequest;
import com.example.cab.dto.TripResponse;
import com.example.cab.model.Driver;
import com.example.cab.model.Rider;
import com.example.cab.security.CurrentUser;
import com.example.cab.service.TripService;

/**
 * REST endpoints for rides. The controller only translates HTTP <-> Java (reads the token, validates the
 * body, calls the service). All business rules live in TripService.
 * Who may call what is configured in SecurityConfig and double-checked in TripService (ownership).
 */
@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService service;

    public TripController(TripService service) {
        this.service = service;
    }

    /** Rider: see the fare before booking. */
    @PostMapping("/quote")
    public FareQuote quote(@Valid @RequestBody CreateTripRequest request) {
        return service.quote(request);
    }

    /** Rider: request a ride. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTripRequest request) {
        return service.create(CurrentUser.from(jwt).profileId(), request);
    }

    /** Rider or driver: my recent trips. */
    @GetMapping("/mine")
    public List<TripResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.mine(CurrentUser.from(jwt));
    }

    @GetMapping("/{id}")
    public TripResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") Long id) {
        return service.get(id, CurrentUser.from(jwt));
    }

    /** Driver: passenger picked up. */
    @PostMapping("/{id}/start")
    public TripResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") Long id) {
        return service.start(id, CurrentUser.from(jwt));
    }

    /** Driver: passenger dropped off. */
    @PostMapping("/{id}/complete")
    public TripResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") Long id) {
        return service.complete(id, CurrentUser.from(jwt));
    }

    /** Rider or driver: cancel before the ride starts. */
    @PostMapping("/{id}/cancel")
    public TripResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") Long id) {
        return service.cancel(id, CurrentUser.from(jwt));
    }

    /** Rider: rate the driver (1-5 stars). */
    @PostMapping("/{id}/rating")
    public TripResponse rate(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") Long id,
                             @Valid @RequestBody RatingRequest request) {
        return service.rate(id, CurrentUser.from(jwt), request);
    }
}
