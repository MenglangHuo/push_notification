package com.onesignal.platform.event.consumer;

import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.infrastructure.AppRepository;
import com.onesignal.platform.device.domain.Device;
import com.onesignal.platform.device.infrastructure.DeviceRepository;
import com.onesignal.platform.event.api.dto.SdkEventDto;
import com.onesignal.platform.event.domain.ClientEvent;
import com.onesignal.platform.event.domain.ClientEventType;
import com.onesignal.platform.event.event.EventBatchIngestMessage;
import com.onesignal.platform.event.infrastructure.ClientEventRepository;
import com.onesignal.platform.event.infrastructure.EventIngestProducer;
import com.onesignal.platform.notification.domain.Notification;
import com.onesignal.platform.notification.infrastructure.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventIngestConsumer {

    private final ClientEventRepository clientEventRepository;
    private final AppRepository appRepository;
    private final NotificationRepository notificationRepository;
    private final DeviceRepository deviceRepository;

    @KafkaListener(
            topics = EventIngestProducer.TOPIC_EVENTS_INGEST,
            groupId = "event-ingest-consumer-group"
    )
    @Transactional
    public void onEventBatchReceived(EventBatchIngestMessage message) {
        if (message == null || message.getEvents() == null || message.getEvents().isEmpty()) {
            return;
        }

        UUID appId = message.getAppId();
        Optional<App> appOpt = appRepository.findById(appId);
        if (appOpt.isEmpty()) {
            log.warn("App {} not found for ingested event batch", appId);
            return;
        }

        App app = appOpt.get();
        List<ClientEvent> entitiesToSave = new ArrayList<>();
        Map<UUID, Integer> openedIncrements = new HashMap<>();
        Map<UUID, Integer> deliveredIncrements = new HashMap<>();
        Set<UUID> touchedDeviceIds = new HashSet<>();

        for (SdkEventDto.EventItem item : message.getEvents()) {
            if (item.getEventType() == null) {
                continue;
            }

            Notification notification = null;
            if (item.getNotificationId() != null) {
                notification = notificationRepository.findById(item.getNotificationId()).orElse(null);
                if (notification != null) {
                    if (item.getEventType() == ClientEventType.OPENED) {
                        openedIncrements.merge(notification.getId(), 1, Integer::sum);
                    } else if (item.getEventType() == ClientEventType.DELIVERED) {
                        deliveredIncrements.merge(notification.getId(), 1, Integer::sum);
                    }
                }
            }

            Device device = null;
            if (item.getDeviceId() != null) {
                device = deviceRepository.findById(item.getDeviceId()).orElse(null);
                if (device != null) {
                    touchedDeviceIds.add(device.getId());
                }
            }

            ClientEvent entity = ClientEvent.builder()
                    .app(app)
                    .notification(notification)
                    .device(device)
                    .eventType(item.getEventType())
                    .eventName(item.getEventName())
                    .properties(item.getProperties() != null ? item.getProperties() : new HashMap<>())
                    .clientTimestamp(item.getTimestamp() != null ? item.getTimestamp() : Instant.now())
                    .build();

            entitiesToSave.add(entity);
        }

        clientEventRepository.saveAll(entitiesToSave);

        // Update notification metrics in bulk
        for (var entry : openedIncrements.entrySet()) {
            notificationRepository.findById(entry.getKey()).ifPresent(n -> {
                n.setOpenedCount(n.getOpenedCount() + entry.getValue());
                notificationRepository.save(n);
            });
        }

        for (var entry : deliveredIncrements.entrySet()) {
            notificationRepository.findById(entry.getKey()).ifPresent(n -> {
                n.setDeliveredCount(n.getDeliveredCount() + entry.getValue());
                notificationRepository.save(n);
            });
        }

        // Touch device active timestamp
        for (UUID devId : touchedDeviceIds) {
            deviceRepository.findById(devId).ifPresent(d -> {
                d.setLastActiveAt(Instant.now());
                deviceRepository.save(d);
            });
        }

        log.info("Ingested and processed batch of {} events for app {}", entitiesToSave.size(), appId);
    }
}
