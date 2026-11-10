package com.onesignal.platform.event.consumer;

import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.infrastructure.AppRepository;
import com.onesignal.platform.device.domain.Device;
import com.onesignal.platform.device.infrastructure.DeviceRepository;
import com.onesignal.platform.event.api.dto.SdkEventDto;
import com.onesignal.platform.event.domain.ClientEventType;
import com.onesignal.platform.event.event.EventBatchIngestMessage;
import com.onesignal.platform.event.infrastructure.ClientEventRepository;
import com.onesignal.platform.notification.domain.Notification;
import com.onesignal.platform.notification.infrastructure.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventIngestConsumerTest {

    @Mock
    private ClientEventRepository clientEventRepository;
    @Mock
    private AppRepository appRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DeviceRepository deviceRepository;

    @InjectMocks
    private EventIngestConsumer eventIngestConsumer;

    private UUID appId;
    private UUID notificationId;
    private UUID deviceId;
    private App app;
    private Notification notification;
    private Device device;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
        deviceId = UUID.randomUUID();

        app = App.builder().name("Test App").build();
        app.setId(appId);

        notification = Notification.builder()
                .title("Title")
                .body("Body")
                .deliveredCount(10)
                .openedCount(2)
                .build();
        notification.setId(notificationId);

        device = Device.builder().build();
        device.setId(deviceId);
    }

    @Test
    @DisplayName("onEventBatchReceived saves events, updates notification counts, and touches device lastActiveAt")
    void testOnEventBatchReceived() {
        when(appRepository.findById(appId)).thenReturn(Optional.of(app));
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(deviceRepository.findById(deviceId)).thenReturn(Optional.of(device));

        SdkEventDto.EventItem deliveredItem = SdkEventDto.EventItem.builder()
                .notificationId(notificationId)
                .deviceId(deviceId)
                .eventType(ClientEventType.DELIVERED)
                .timestamp(Instant.now())
                .build();

        SdkEventDto.EventItem openedItem = SdkEventDto.EventItem.builder()
                .notificationId(notificationId)
                .deviceId(deviceId)
                .eventType(ClientEventType.OPENED)
                .timestamp(Instant.now())
                .build();

        EventBatchIngestMessage message = EventBatchIngestMessage.builder()
                .appId(appId)
                .events(List.of(deliveredItem, openedItem))
                .build();

        eventIngestConsumer.onEventBatchReceived(message);

        verify(clientEventRepository, times(1)).saveAll(anyList());
        verify(notificationRepository, atLeastOnce()).save(notification);
        verify(deviceRepository, atLeastOnce()).save(device);

        assertThat(notification.getDeliveredCount()).isEqualTo(11);
        assertThat(notification.getOpenedCount()).isEqualTo(3);
        assertThat(device.getLastActiveAt()).isNotNull();
    }
}
