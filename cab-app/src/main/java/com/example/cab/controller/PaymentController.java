package com.example.cab.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cab.dto.PaymentResponse;
import com.example.cab.security.CurrentUser;
import com.example.cab.service.PaymentService;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/payment")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /** Pay for a completed trip. The Idempotency-Key header is REQUIRED (see PaymentService). */
    @PostMapping
    public PaymentResponse pay(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable("tripId") Long tripId,
                               @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.pay(tripId, CurrentUser.from(jwt).profileId(), idempotencyKey);
    }
}
