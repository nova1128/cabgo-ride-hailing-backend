package com.example.cab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.cab.model.Driver;
import com.example.cab.model.Rider;

/**
 * END-TO-END integration test against REAL PostgreSQL, Redis and Kafka started by Testcontainers
 * (Docker must be running). It starts the whole Spring app on a random port and calls the real HTTP API.
 *
 * Each test uses its own map area so drivers created by one test cannot be matched in another.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CabFlowIntegrationTest {

    // @ServiceConnection tells Spring Boot to point its datasource / Redis / Kafka settings at these containers.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() { };

    @Autowired
    TestRestTemplate rest;

    // ------------------------------------------------------------------ tests

    @Test
    void onlyOneOfTwoSimultaneousRidersGetsTheSingleDriver() throws Exception {
        double lat = 10.0;
        double lng = 10.0;
        String driverToken = registerDriver(lat, lng);
        String rider1 = registerRider();
        String rider2 = registerRider();
        Map<String, Object> tripBody = tripBody(lat + 0.001, lng + 0.001);

        // Both riders fire at the same instant. The atomic claim in SQL must give the driver to exactly one.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ResponseEntity<Map<String, Object>>>> futures = new ArrayList<>();
        for (String token : List.of(rider1, rider2)) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return call(HttpMethod.POST, "/api/v1/trips", token, tripBody);
            }));
        }
        ready.await();
        go.countDown();

        List<Object> statuses = new ArrayList<>();
        for (Future<ResponseEntity<Map<String, Object>>> future : futures) {
            ResponseEntity<Map<String, Object>> response = future.get(30, TimeUnit.SECONDS);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            statuses.add(response.getBody().get("status"));
        }
        pool.shutdown();

        assertThat(statuses).containsExactlyInAnyOrder("DRIVER_ASSIGNED", "NO_DRIVER_FOUND");
        assertThat(driverToken).isNotBlank();
    }

    @Test
    void fullRideLifecycleWithPaymentAndRating() {
        double lat = 20.0;
        double lng = 20.0;
        String driver = registerDriver(lat, lng);
        String rider = registerRider();
        String otherRider = registerRider();

        // request -> driver assigned, priced
        ResponseEntity<Map<String, Object>> created =
                call(HttpMethod.POST, "/api/v1/trips", rider, tripBody(lat + 0.001, lng + 0.001));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("status")).isEqualTo("DRIVER_ASSIGNED");
        assertThat(created.getBody().get("fare")).isNotNull();
        long tripId = ((Number) created.getBody().get("id")).longValue();

        // someone who is not part of the trip cannot see it
        assertThat(call(HttpMethod.GET, "/api/v1/trips/" + tripId, otherRider, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // the driver is now busy
        assertThat(call(HttpMethod.GET, "/api/v1/drivers/me", driver, null).getBody().get("status"))
                .isEqualTo("ON_TRIP");

        // start -> complete (state machine)
        assertThat(call(HttpMethod.POST, "/api/v1/trips/" + tripId + "/start", driver, null).getBody().get("status"))
                .isEqualTo("IN_PROGRESS");
        assertThat(call(HttpMethod.POST, "/api/v1/trips/" + tripId + "/complete", driver, null).getBody().get("status"))
                .isEqualTo("COMPLETED");
        assertThat(call(HttpMethod.POST, "/api/v1/trips/" + tripId + "/complete", driver, null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // the driver is free again
        assertThat(call(HttpMethod.GET, "/api/v1/drivers/me", driver, null).getBody().get("status"))
                .isEqualTo("AVAILABLE");

        // idempotent payment: same key = safe replay, different key = already paid
        String payPath = "/api/v1/trips/" + tripId + "/payment";
        ResponseEntity<Map<String, Object>> paid =
                call(HttpMethod.POST, payPath, rider, null, "Idempotency-Key", "key-1");
        assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(paid.getBody().get("status")).isEqualTo("CAPTURED");
        assertThat(call(HttpMethod.POST, payPath, rider, null, "Idempotency-Key", "key-1").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.POST, payPath, rider, null, "Idempotency-Key", "key-2").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // rating: allowed once, updates the driver's average
        String ratePath = "/api/v1/trips/" + tripId + "/rating";
        Map<String, Object> rating = new HashMap<>();
        rating.put("score", 5);
        assertThat(call(HttpMethod.POST, ratePath, rider, rating).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.POST, ratePath, rider, rating).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Map<String, Object> driverProfile = call(HttpMethod.GET, "/api/v1/drivers/me", driver, null).getBody();
        assertThat(((Number) driverProfile.get("ratingAverage")).doubleValue()).isEqualTo(5.0);
        assertThat(((Number) driverProfile.get("ratingCount")).intValue()).isEqualTo(1);
    }

    @Test
    void securityRulesAreEnforced() {
        String rider = registerRider();

        // no token -> 401
        assertThat(call(HttpMethod.GET, "/api/v1/drivers/me", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // a rider token on a driver-only endpoint -> 403
        assertThat(call(HttpMethod.GET, "/api/v1/drivers/me", rider, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // invalid coordinates -> 400 (validation)
        assertThat(call(HttpMethod.POST, "/api/v1/trips", rider, tripBody(200.0, 10.0)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------ helpers

    private ResponseEntity<Map<String, Object>> call(HttpMethod method, String path, String token,
                                                     Object body, String... headerPairs) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        for (int i = 0; i + 1 < headerPairs.length; i += 2) {
            headers.add(headerPairs[i], headerPairs[i + 1]);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JSON_MAP);
    }

    private String randomPhone() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }

    private String registerRider() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Test Rider");
        body.put("phone", randomPhone());
        body.put("password", "password123");
        body.put("role", "RIDER");
        ResponseEntity<Map<String, Object>> response = call(HttpMethod.POST, "/api/v1/auth/register", null, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("token");
    }

    /** Registers a driver, sets a location and puts them online. Returns the driver's token. */
    private String registerDriver(double lat, double lng) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Test Driver");
        body.put("phone", randomPhone());
        body.put("password", "password123");
        body.put("role", "DRIVER");
        body.put("vehicleNumber", "TEST1234");
        ResponseEntity<Map<String, Object>> response = call(HttpMethod.POST, "/api/v1/auth/register", null, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String token = (String) response.getBody().get("token");

        Map<String, Object> location = new HashMap<>();
        location.put("latitude", lat);
        location.put("longitude", lng);
        assertThat(call(HttpMethod.PUT, "/api/v1/drivers/me/location", token, location).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> status = new HashMap<>();
        status.put("status", "AVAILABLE");
        assertThat(call(HttpMethod.PUT, "/api/v1/drivers/me/status", token, status).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        return token;
    }

    private Map<String, Object> tripBody(double pickupLat, double pickupLng) {
        Map<String, Object> body = new HashMap<>();
        body.put("pickupLat", pickupLat);
        body.put("pickupLng", pickupLng);
        body.put("dropLat", pickupLat + 0.01);
        body.put("dropLng", pickupLng + 0.01);
        return body;
    }
}
