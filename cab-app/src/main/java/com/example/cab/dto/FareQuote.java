package com.example.cab.dto;

import java.math.BigDecimal;

/** The price breakdown shown to the rider: distance, base fare, surge multiplier, final total. */
public record FareQuote(BigDecimal baseFare, BigDecimal surgeMultiplier, BigDecimal total, double distanceKm) {
}
