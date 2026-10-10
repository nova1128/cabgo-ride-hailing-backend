package com.example.cab.exception;

/** Thrown by services; GlobalExceptionHandler turns it into the matching HTTP error response. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
