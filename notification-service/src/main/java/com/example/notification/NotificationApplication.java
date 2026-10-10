package com.example.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A SECOND, independently deployed service. It knows nothing about the main app's code or database:
 * it only listens to the "trip-events" Kafka topic and turns events into notifications.
 * Stop it and the main app keeps working (events simply wait in Kafka); start it again and it catches up.
 * That independence is the whole point of using a message broker between services.
 */
@SpringBootApplication
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
