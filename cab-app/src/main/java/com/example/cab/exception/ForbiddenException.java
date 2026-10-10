package com.example.cab.exception;

/** Thrown by services; GlobalExceptionHandler turns it into the matching HTTP error response. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
