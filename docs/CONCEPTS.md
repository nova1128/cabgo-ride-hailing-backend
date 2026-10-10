# Concepts used, where, why, and alternatives

Every item names the file where it lives so you can open it and read the commented code.

| Concept | Where | Why it is used | Alternatives |
|---|---|---|---|
| **Layered / MVC architecture** (controller -> service -> repository -> model) | `controller/`, `service/`, `repository/`, `model/` | Controllers only translate HTTP; business rules live in services; repositories only talk to the DB. Easy to test and change. | Hexagonal/clean architecture (more structure, more files) |
| **Service boundaries inside a monolith** | `service/` (e.g. `TripService` calls `DriverService`, never `DriverRepository`) | One deployable app, but features talk only through service classes, so they can be split into services later without rewrites. | Microservices from day one (network failures, deployment overhead) |
| **DTOs** (request/response records) | `dto/` | The API shape is independent of DB tables; sensitive fields (password hash) are never exposed. | Returning JPA entities directly (leaks DB structure, lazy-loading bugs) |
| **Bean Validation** | `RegisterRequest`, `CreateTripRequest`, ... | Bad input is rejected with HTTP 400 before any logic runs. | Manual `if` checks (easy to forget) |
| **Centralised error handling** | `exception/GlobalExceptionHandler` | One place decides status code and JSON error shape. | try/catch in every controller |
| **Stateless JWT auth** | `config/SecurityConfig`, `service/JwtService` | No server session; any instance can verify a token. | Server sessions, Keycloak/OAuth2 server |
| **RBAC + ownership checks** | `SecurityConfig` rules, `TripService.requireParticipant` | Roles limit endpoints; ownership checks stop a user acting on someone else's trip. Identity comes from the token (`/drivers/me`), never the URL. | Fine-grained permissions/ACLs |
| **Password hashing** (BCrypt) | `SecurityConfig.passwordEncoder` | Only a one-way hash is stored. | Argon2, scrypt |
| **Rate limiting** (token bucket) | `service/RateLimitService`, `application.properties` | Stops brute-force logins and request spam. | API gateway limits, Redis sliding window (cluster-wide) |
| **State machine** | `model/TripStatus`, `Trip.transitionTo` | Illegal status jumps are impossible; data stays consistent. | Spring Statemachine library |
| **Optimistic locking** (`@Version`) | `Trip`, `Driver`, `Payment` | Two concurrent edits of one row: the second fails with 409 instead of silently overwriting. | Pessimistic locks (`SELECT FOR UPDATE`) |
| **Atomic compare-and-swap in SQL** | `DriverRepository.transitionStatus`, `DriverService.tryClaim` | The single statement that guarantees one driver is never given to two riders. | Redis distributed lock (Redlock), pessimistic lock |
| **Geospatial index** (Redis GEO) | `repository/DriverGeoIndex` | Nearest-driver lookups without scanning every driver. | PostGIS, Uber H3, geohash prefixes, quadtree |
| **Derived data / source of truth** | `DriverService` (afterCommit), `DriverIndexWarmup` | Postgres is authoritative; Redis is a fast copy updated after commit and rebuilt at startup. | Dual writes without safeguards (drift), CDC (Debezium) |
| **Circuit breaker + fallback** | `DriverMatchingService`, `resilience4j.circuitbreaker.*` | If Redis fails, stop calling it and use SQL so riders are still matched. | Retries, timeouts only, bulkheads |
| **Graceful degradation** | `SurgePricingService` (returns 1.00 on Redis failure), `TripEventPublisher` | A non-critical dependency failing must not fail the booking. | Failing the request (worse UX) |
| **Caching with TTL** | `SurgePricingService` (20 s multiplier cache) | Stable prices, fewer computations. | Caffeine in-memory cache, Spring `@Cacheable` |
| **Dynamic pricing** | `service/SurgePricingService` | Demand/supply based multiplier per ~1 km grid cell. | Geohash/H3 cells, streaming job, ML model |
| **Event-driven architecture** | `event/*`, `notification-service` | Notifications react to events; the trip code does not know who listens. Services can fail/deploy independently. | Direct REST calls (tight coupling), RabbitMQ, SNS/SQS |
| **Publish after commit** | `TripEventPublisher` (`@TransactionalEventListener(AFTER_COMMIT)`) | Never publish an event for a change that was rolled back. | Transactional **outbox** (guaranteed delivery; see limitations) |
| **Kafka partition key** | `TripEventPublisher.publish`, `KafkaTopicConfig` | Key = trip id keeps one trip's events ordered while trips run in parallel. | Single partition (no parallelism) |
| **Idempotency** | `service/PaymentService`, `Payment.trip_id UNIQUE` | Retries and double-clicks never charge twice. | Idempotency-key table, Redis SETNX |
| **Interface for external systems** | `model/PaymentGateway` | Swap the fake gateway for Stripe (test mode is free) without touching callers. | Hard-coded provider |
| **Transactions** | `@Transactional` on service methods | Multi-step changes (create user + profile, complete trip + release driver + create payment) succeed or fail together. | Saga pattern across services |
| **Reference by ID, not relation** | `Trip.riderId/driverId` | Modules do not depend on each other's internals. | JPA `@ManyToOne` (simpler inside one module) |
| **Observability** | Actuator, Micrometer metrics (`cab.trips`, `cab.matching.duration`, `cab.events.published`), Prometheus, Grafana | You can *see* latency, error rates, circuit breaker state, fallback usage. | ELK/Loki logs, OpenTelemetry tracing |
| **Testing pyramid** | `src/test` | Fast unit tests for logic; Testcontainers integration test with real Postgres/Redis/Kafka for behaviour and races. | H2 in-memory DB (not the same as Postgres) |
| **Load testing / benchmarking** | `loadtest/matching.js` | Measure instead of guess: SQL scan vs Redis. | JMeter, Gatling |
| **Containerisation** | `docker-compose.yml`, `Dockerfile`s | Reproducible local infrastructure with one command. | Kubernetes (future work), local installs |
