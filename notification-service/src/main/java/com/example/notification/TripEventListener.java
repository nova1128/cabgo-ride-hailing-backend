package com.example.notification;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CONCEPT: Kafka consumer. Spring calls onMessage() for every event on the "trip-events" topic.
 * Each event type is translated into friendly messages for the rider and/or the driver.
 * A broken message is logged and skipped, so one bad event can never block the ones behind it.
 */
@Component
public class TripEventListener {

    private static final Logger log = LoggerFactory.getLogger(TripEventListener.class);

    private final ObjectMapper objectMapper;
    private final NotificationStore store;

    public TripEventListener(ObjectMapper objectMapper, NotificationStore store) {
        this.objectMapper = objectMapper;
        this.store = store;
    }

    @KafkaListener(topics = "trip-events")
    public void onMessage(String payload) {
        try {
            TripEventMessage event = objectMapper.readValue(payload, TripEventMessage.class);
            log.info("Received {} for trip {}", event.type(), event.tripId());
            handle(event);
        } catch (Exception e) {
            log.warn("Skipping unreadable event: {}", e.getMessage());
        }
    }

    private void handle(TripEventMessage e) {
        switch (e.type()) {
            case "TRIP_DRIVER_ASSIGNED" -> {
                toRider(e, "Driver #" + e.driverId() + " accepted your ride and is on the way. Fare " + money(e.amount()));
                toDriver(e, "New trip #" + e.tripId() + " assigned. Head to the pickup point.");
            }
            case "TRIP_NO_DRIVER_FOUND" -> toRider(e, "Sorry, no drivers are available near you right now.");
            case "TRIP_STARTED" -> toRider(e, "Your ride has started. Have a safe trip!");
            case "TRIP_COMPLETED" -> {
                toRider(e, "You have arrived. Please pay " + money(e.amount()) + ".");
                toDriver(e, "Trip #" + e.tripId() + " completed.");
            }
            case "TRIP_CANCELLED" -> {
                toRider(e, "Trip #" + e.tripId() + " was cancelled.");
                toDriver(e, "Trip #" + e.tripId() + " was cancelled.");
            }
            case "PAYMENT_CAPTURED" -> {
                toRider(e, "Payment of " + money(e.amount()) + " received. Thank you!");
                toDriver(e, "The rider paid " + money(e.amount()) + " for trip #" + e.tripId() + ".");
            }
            default -> log.debug("Ignoring event type {}", e.type());
        }
    }

    private void toRider(TripEventMessage e, String message) {
        if (e.riderId() != null) {
            store.add("RIDER", e.riderId(), message);
        }
    }

    private void toDriver(TripEventMessage e, String message) {
        if (e.driverId() != null) {
            store.add("DRIVER", e.driverId(), message);
        }
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "" : "\u20B9" + amount.toPlainString();
    }
}
