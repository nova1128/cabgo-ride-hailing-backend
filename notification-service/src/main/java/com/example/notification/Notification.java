package com.example.notification;

import java.time.Instant;

/** One message for one person. recipientRole is RIDER or DRIVER; recipientId is their profile id. */
public record Notification(long id, String recipientRole, long recipientId, String message, Instant createdAt) {
}
