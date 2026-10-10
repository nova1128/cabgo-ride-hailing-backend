package com.example.cab.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeoUtilsTest {

    @Test
    void samePointIsZeroDistance() {
        assertThat(GeoUtils.distanceKm(27.49, 77.67, 27.49, 77.67)).isZero();
    }

    @Test
    void delhiToMumbaiIsAbout1150Km() {
        double km = GeoUtils.distanceKm(28.6139, 77.2090, 19.0760, 72.8777);
        assertThat(km).isBetween(1140.0, 1170.0);
    }

    @Test
    void distanceIsSymmetric() {
        double ab = GeoUtils.distanceKm(10.0, 20.0, 11.0, 21.0);
        double ba = GeoUtils.distanceKm(11.0, 21.0, 10.0, 20.0);
        assertThat(ab).isEqualTo(ba);
    }
}
