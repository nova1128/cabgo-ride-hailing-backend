package com.example.cab.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.example.cab.dto.DriverResponse;
import com.example.cab.dto.UpdateLocationRequest;
import com.example.cab.dto.UpdateStatusRequest;
import com.example.cab.security.CurrentUser;
import com.example.cab.service.DriverService;

/** A driver can only read/update their OWN profile ("/me"); the id comes from the token, never the URL. */
@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverService service;

    public DriverController(DriverService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public DriverResponse me(@AuthenticationPrincipal Jwt jwt) {
        return service.get(CurrentUser.from(jwt).profileId());
    }

    @PutMapping("/me/location")
    public DriverResponse updateLocation(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody UpdateLocationRequest request) {
        return service.updateLocation(CurrentUser.from(jwt).profileId(), request);
    }

    @PutMapping("/me/status")
    public DriverResponse updateStatus(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody UpdateStatusRequest request) {
        return service.updateStatus(CurrentUser.from(jwt).profileId(), request);
    }
}
