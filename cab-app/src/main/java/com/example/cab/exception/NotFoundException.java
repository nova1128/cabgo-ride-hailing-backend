package com.example.cab.exception;

/** Thrown by services; GlobalExceptionHandler turns it into the matching HTTP error response. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
