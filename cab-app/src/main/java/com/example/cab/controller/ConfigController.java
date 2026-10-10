package com.example.cab.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the frontend where the notification service lives. The same built app runs locally (notification
 * service on localhost:8081) and in the cloud (events/Kafka switched off => no notification service), so the
 * address must come from configuration instead of being hard-coded in the JavaScript.
 */
@RestController
public class ConfigController {

    private final boolean eventsEnabled;
    private final String notificationUrl;

    public ConfigController(@Value("${cab.events.enabled:true}") boolean eventsEnabled,
                            @Value("${cab.notification-url:http://localhost:8081}") String notificationUrl) {
        this.eventsEnabled = eventsEnabled;
        this.notificationUrl = notificationUrl;
    }

    @GetMapping("/api/v1/config")
    public Map<String, String> config() {
        // Empty string = "no notifications available"; the frontend then skips polling.
        return Map.of("notificationUrl", eventsEnabled ? notificationUrl : "");
    }
}
