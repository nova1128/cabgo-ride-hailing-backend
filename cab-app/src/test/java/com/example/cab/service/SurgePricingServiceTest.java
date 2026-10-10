package com.example.cab.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Tests the pure surge formula (no Redis needed). */
class SurgePricingServiceTest {

    @Test
    void noSurgeWhenSupplyCoversDemand() {
        assertThat(SurgePricingService.computeMultiplier(1, 1)).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(SurgePricingService.computeMultiplier(1, 3)).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(SurgePricingService.computeMultiplier(0, 0)).isEqualByComparingTo(new BigDecimal("1.00"));
    }

    @Test
    void surgeGrowsWithDemandPerDriver() {
        assertThat(SurgePricingService.computeMultiplier(4, 2)).isEqualByComparingTo(new BigDecimal("1.25"));
        assertThat(SurgePricingService.computeMultiplier(3, 1)).isEqualByComparingTo(new BigDecimal("1.50"));
    }

    @Test
    void noDriversAtAllStillUsesDemandOnly() {
        assertThat(SurgePricingService.computeMultiplier(5, 0)).isEqualByComparingTo(new BigDecimal("2.00"));
    }

    @Test
    void surgeIsCapped() {
        assertThat(SurgePricingService.computeMultiplier(100, 1)).isEqualByComparingTo(new BigDecimal("2.50"));
    }
}
