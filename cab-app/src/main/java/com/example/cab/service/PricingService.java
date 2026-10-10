package com.example.cab.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

import com.example.cab.dto.FareQuote;
import com.example.cab.util.GeoUtils;

/**
 * Fare = (base + per-km rate x straight-line distance) x surge multiplier.
 * Straight-line (Haversine) distance is a simplification; a real app would ask a routing engine
 * (OSRM / Google Directions) for road distance and time.
 */
@Service
public class PricingService {

    private static final BigDecimal BASE_FARE = new BigDecimal("50.00");
    private static final BigDecimal PER_KM = new BigDecimal("12.00");

    private final SurgePricingService surgePricingService;

    public PricingService(SurgePricingService surgePricingService) {
        this.surgePricingService = surgePricingService;
    }

    /** Price shown BEFORE booking. Does not count as demand. */
    public FareQuote preview(double pickupLat, double pickupLng, double dropLat, double dropLng) {
        return build(pickupLat, pickupLng, dropLat, dropLng, false);
    }

    /** Price for a real ride request. Counts as demand for surge pricing. */
    public FareQuote quote(double pickupLat, double pickupLng, double dropLat, double dropLng) {
        return build(pickupLat, pickupLng, dropLat, dropLng, true);
    }

    private FareQuote build(double pickupLat, double pickupLng, double dropLat, double dropLng,
                            boolean recordDemand) {
        double km = GeoUtils.distanceKm(pickupLat, pickupLng, dropLat, dropLng);
        BigDecimal base = BASE_FARE.add(PER_KM.multiply(BigDecimal.valueOf(km))).setScale(2, RoundingMode.HALF_UP);
        BigDecimal surge = surgePricingService.multiplierFor(pickupLat, pickupLng, recordDemand);
        BigDecimal total = base.multiply(surge).setScale(2, RoundingMode.HALF_UP);
        return new FareQuote(base, surge, total, Math.round(km * 100.0) / 100.0);
    }
}
