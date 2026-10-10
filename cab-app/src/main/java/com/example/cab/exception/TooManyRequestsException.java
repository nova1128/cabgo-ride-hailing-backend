package com.example.cab.exception;

/** Thrown by services; GlobalExceptionHandler turns it into the matching HTTP error response. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
