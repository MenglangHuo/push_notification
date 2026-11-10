package com.onesignal.platform.event.infrastructure;

import com.onesignal.platform.event.event.EventBatchIngestMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventIngestProducer {

    public static final String TOPIC_EVENTS_INGEST = "events.ingest";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendEventBatch(EventBatchIngestMessage message) {
        String key = message.getAppId() != null ? message.getAppId().toString() : "global";
        kafkaTemplate.send(TOPIC_EVENTS_INGEST, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to enqueue event batch to Kafka: {}", ex.getMessage(), ex);
                    } else {
                        log.debug("Successfully enqueued event batch of {} items to {}",
                                message.getEvents() != null ? message.getEvents().size() : 0,
                                TOPIC_EVENTS_INGEST);
                    }
                });
    }
}
