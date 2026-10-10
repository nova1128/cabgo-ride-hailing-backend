package com.example.cab.exception;

/** Thrown by services; GlobalExceptionHandler turns it into the matching HTTP error response. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
