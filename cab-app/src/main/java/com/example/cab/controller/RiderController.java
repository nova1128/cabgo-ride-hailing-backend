package com.example.cab.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cab.dto.RiderResponse;
import com.example.cab.security.CurrentUser;
import com.example.cab.service.RiderService;

/** A rider reads their OWN profile via /me; the id comes from the token, never from the URL. */
@RestController
@RequestMapping("/api/v1/riders")
public class RiderController {

    private final RiderService service;

    public RiderController(RiderService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public RiderResponse me(@AuthenticationPrincipal Jwt jwt) {
        return service.get(CurrentUser.from(jwt).profileId());
    }
}
