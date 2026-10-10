package com.example.cab.exception;

import java.time.Instant;

/** The JSON body returned for every error, so the frontend can always show error.message. */
public record ApiError(Instant timestamp, int status, String error, String message) {
}
