package com.example.cab.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * CONCEPT: event-driven architecture with Kafka, sent only AFTER the database commit.
 *
 * HOW IT WORKS: services call applicationEventPublisher.publishEvent(new TripEvent(...)) inside their
 * database transaction. Spring holds the event until the transaction COMMITS (AFTER_COMMIT), and only then
 * this class sends it to Kafka. So Kafka never receives an event for a change that was rolled back.
 *
 * KNOWN LIMITATION (good interview point): if the app crashes between the commit and the Kafka send, the
 * event is lost. The production-grade fix is the TRANSACTIONAL OUTBOX pattern: write the event to an
 * "outbox" table in the same transaction and let a background job publish it, guaranteeing at-least-once.
 *
 * The event key is the trip id, so all events of one trip go to the same Kafka partition and stay ordered.
 * ALTERNATIVES: RabbitMQ, AWS SNS/SQS, Spring's in-process events only (no separate services).
 */
@Component
// Set cab.events.enabled=false (env CAB_EVENTS_ENABLED=false) to run WITHOUT Kafka, e.g. on a free cloud tier.
// The bean then does not exist, so published events are simply ignored and nothing waits on a broker.
@ConditionalOnProperty(name = "cab.events.enabled", havingValue = "true", matchIfMissing = true)
public class TripEventPublisher {

    public static final String TOPIC = "trip-events";

    private static final Logger log = LoggerFactory.getLogger(TripEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public TripEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(TripEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, String.valueOf(event.tripId()), json)
                    .whenComplete((result, error) -> {
                        if (error != null) {
                            log.warn("Kafka send failed for {}: {}", event.type(), error.getMessage());
                        }
                    });
            meterRegistry.counter("cab.events.published", "type", event.type()).increment();
        } catch (Exception e) {
            // Never fail the user's request because messaging is down; the trip itself is already saved.
            log.warn("Could not publish event {}: {}", event.type(), e.getMessage());
        }
    }
}
