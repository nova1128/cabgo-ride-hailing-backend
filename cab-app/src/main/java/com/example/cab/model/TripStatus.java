package com.example.cab.model;

/**
 * CONCEPT: a state machine. A trip may only move along these arrows:
 *
 *   REQUESTED --> DRIVER_ASSIGNED --> IN_PROGRESS --> COMPLETED
 *       |               |
 *       |               +-----------> CANCELLED
 *       +--> NO_DRIVER_FOUND, CANCELLED
 *
 * Everything not listed is rejected (e.g. you cannot "complete" a trip that never started, and a
 * finished trip can never change again). This prevents corrupted data from buggy or malicious calls.
 */
public enum TripStatus {
    REQUESTED,
    DRIVER_ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_DRIVER_FOUND;

    public boolean canTransitionTo(TripStatus next) {
        return switch (this) {
            case REQUESTED -> next == DRIVER_ASSIGNED || next == NO_DRIVER_FOUND || next == CANCELLED;
            case DRIVER_ASSIGNED -> next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS -> next == COMPLETED;
            case COMPLETED, CANCELLED, NO_DRIVER_FOUND -> false;
        };
    }
}
