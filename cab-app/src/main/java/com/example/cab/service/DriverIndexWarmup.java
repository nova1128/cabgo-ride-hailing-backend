package com.example.cab.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.cab.dto.DriverLocation;
import com.example.cab.repository.DriverGeoIndex;

/**
 * CONCEPT: cache warm-up / rebuilding derived data.
 * Redis can be restarted or flushed at any time, but the database still knows every AVAILABLE driver.
 * On startup we wipe the Redis index and rebuild it from PostgreSQL so both agree.
 * (With several app instances you would do this in a single leader/job instead; fine for one instance.)
 */
@Component
public class DriverIndexWarmup {

    private static final Logger log = LoggerFactory.getLogger(DriverIndexWarmup.class);

    private final DriverService driverService;
    private final DriverGeoIndex geoIndex;

    public DriverIndexWarmup(DriverService driverService, DriverGeoIndex geoIndex) {
        this.driverService = driverService;
        this.geoIndex = geoIndex;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildIndex() {
        try {
            geoIndex.clear();
            List<DriverLocation> available = driverService.findAvailableLocations();
            for (DriverLocation location : available) {
                geoIndex.update(location.id(), location.latitude(), location.longitude());
            }
            log.info("Rebuilt Redis geo-index with {} available drivers", available.size());
        } catch (RuntimeException e) {
            log.warn("Could not rebuild the Redis geo-index (is Redis running?): {}", e.getMessage());
        }
    }
}
