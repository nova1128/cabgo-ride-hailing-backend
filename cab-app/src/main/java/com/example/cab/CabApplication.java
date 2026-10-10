package com.example.cab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the main backend.
 *
 * The code follows the classic Spring MVC / layered structure (one package per layer):
 *   controller - REST endpoints (the "C"): read the request, call a service, return JSON
 *   service    - business rules (state machine, matching, pricing, payments, ...)
 *   repository - data access: Spring Data JPA interfaces + the Redis geo-index
 *   model      - JPA entities and enums (the "M"): Driver, Trip, Payment, ...
 *   dto        - request/response objects exchanged with the client (what the "View" receives)
 *   exception  - custom exceptions + the global error handler
 *   security   - helper for the logged-in caller (CurrentUser)
 *   config     - Spring configuration (security, Kafka topic)
 *   event      - trip events published to Kafka
 *   gateway    - payment provider abstraction (fake implementation)
 *   util       - small helpers (distance calculation)
 * The "V" (view) is the web UI in src/main/resources/static (plain HTML/JS) that calls this JSON API.
 *
 * RULE: a request flows controller -> service -> repository. Services call OTHER modules' services
 * (e.g. TripService -> DriverService), never their repositories, so features stay loosely coupled.
 */
@SpringBootApplication
public class CabApplication {

    public static void main(String[] args) {
        SpringApplication.run(CabApplication.class, args);
    }
}
