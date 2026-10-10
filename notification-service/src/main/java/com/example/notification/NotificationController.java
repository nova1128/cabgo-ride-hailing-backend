package com.example.notification;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The frontend (served from port 8080) polls this service (port 8081) for new notifications.
 * Different port = different "origin", so the browser needs CORS permission; @CrossOrigin grants it.
 * "*" is fine for a local demo; a real deployment would list the exact frontend origin and require auth.
 */
@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationStore store;

    public NotificationController(NotificationStore store) {
        this.store = store;
    }

    @GetMapping
    public List<Notification> list(@RequestParam("role") String role,
                                   @RequestParam("id") long id,
                                   @RequestParam(value = "afterId", defaultValue = "0") long afterId) {
        return store.find(role, id, afterId);
    }
}
