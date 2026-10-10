package com.example.notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * Keeps the latest notifications in memory (max 500). A real service would use a database and send
 * push/SMS/email through a provider such as Firebase or Twilio; we only store and expose them.
 */
@Component
public class NotificationStore {

    private static final int MAX_KEPT = 500;

    private final AtomicLong sequence = new AtomicLong();
    private final List<Notification> items = new ArrayList<>();

    public synchronized void add(String role, long recipientId, String message) {
        items.add(new Notification(sequence.incrementAndGet(), role, recipientId, message, Instant.now()));
        if (items.size() > MAX_KEPT) {
            items.remove(0);
        }
    }

    /** Notifications for one person that are newer than afterId (the frontend remembers the last id it saw). */
    public synchronized List<Notification> find(String role, long recipientId, long afterId) {
        return items.stream()
                .filter(n -> n.id() > afterId && n.recipientId() == recipientId
                        && n.recipientRole().equalsIgnoreCase(role))
                .toList();
    }
}
