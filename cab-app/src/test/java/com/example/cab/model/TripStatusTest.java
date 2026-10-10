package com.example.cab.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.cab.exception.ConflictException;

/** Unit tests for the trip state machine. No Spring, no database: they run in milliseconds. */
class TripStatusTest {

    @Test
    void happyPathIsAllowed() {
        assertThat(TripStatus.REQUESTED.canTransitionTo(TripStatus.DRIVER_ASSIGNED)).isTrue();
        assertThat(TripStatus.DRIVER_ASSIGNED.canTransitionTo(TripStatus.IN_PROGRESS)).isTrue();
        assertThat(TripStatus.IN_PROGRESS.canTransitionTo(TripStatus.COMPLETED)).isTrue();
    }

    @Test
    void finishedTripsNeverChangeAgain() {
        for (TripStatus finished : new TripStatus[] {
                TripStatus.COMPLETED, TripStatus.CANCELLED, TripStatus.NO_DRIVER_FOUND }) {
            for (TripStatus next : TripStatus.values()) {
                assertThat(finished.canTransitionTo(next)).as(finished + " -> " + next).isFalse();
            }
        }
    }

    @Test
    void statesCannotBeSkipped() {
        assertThat(TripStatus.REQUESTED.canTransitionTo(TripStatus.IN_PROGRESS)).isFalse();
        assertThat(TripStatus.REQUESTED.canTransitionTo(TripStatus.COMPLETED)).isFalse();
        assertThat(TripStatus.DRIVER_ASSIGNED.canTransitionTo(TripStatus.COMPLETED)).isFalse();
        assertThat(TripStatus.IN_PROGRESS.canTransitionTo(TripStatus.CANCELLED)).isFalse();
    }

    @Test
    void tripEntityRejectsIllegalTransition() {
        Trip trip = new Trip(1L, 10.0, 10.0, 10.1, 10.1);
        assertThatThrownBy(() -> trip.transitionTo(TripStatus.COMPLETED))
                .isInstanceOf(ConflictException.class);
    }
}
