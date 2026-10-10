package com.example.cab.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

import com.example.cab.event.TripEventPublisher;

/**
 * Creates the Kafka topic at startup if it does not exist.
 * 3 partitions: events with the same key (trip id) always land in the same partition, so the events of
 * ONE trip stay in order, while different trips can be processed in parallel.
 */
@Configuration
@ConditionalOnProperty(name = "cab.events.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Bean
    public NewTopic tripEventsTopic() {
        return TopicBuilder.name(TripEventPublisher.TOPIC).partitions(3).replicas(1).build();
    }
}
