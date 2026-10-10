package com.example.cab.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.example.cab.model.Payment;
import com.example.cab.model.PaymentStatus;

public record PaymentResponse(
        Long id,
        Long tripId,
        BigDecimal amount,
        PaymentStatus status,
        String gatewayChargeId,
        Instant paidAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getTripId(), p.getAmount(), p.getStatus(),
                p.getGatewayChargeId(), p.getPaidAt());
    }
}
