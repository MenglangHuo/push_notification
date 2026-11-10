package com.onesignal.platform.notification.application;

import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.event.api.dto.SdkEventDto;
import com.onesignal.platform.event.domain.ClientEvent;
import com.onesignal.platform.event.domain.ClientEventType;
import com.onesignal.platform.event.infrastructure.ClientEventRepository;
import com.onesignal.platform.notification.api.dto.NotificationDto;
import com.onesignal.platform.notification.domain.DeliveryLog;
import com.onesignal.platform.notification.domain.Notification;
import com.onesignal.platform.notification.infrastructure.DeliveryLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryLogServiceTest {

    @Mock
    private DeliveryLogRepository deliveryLogRepository;
    @Mock
    private ClientEventRepository clientEventRepository;

    @InjectMocks
    private DeliveryLogService deliveryLogService;

    private UUID appId;
    private UUID notificationId;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getDeliveryLogs filters by parameters and maps to response DTOs")
    void testGetDeliveryLogs() {
        DeliveryLog dl = DeliveryLog.builder()
                .appId(appId)
                .notificationId(notificationId)
                .provider("FCM")
                .status("SUCCESS")
                .statusCode("200")
                .latencyMs(85)
                .createdAt(Instant.now())
                .build();
        dl.setId(UUID.randomUUID());

        when(deliveryLogRepository.findWithFilters(eq(appId), eq("FCM"), eq("SUCCESS"), eq(notificationId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(dl)));

        Page<NotificationDto.DeliveryLogResponse> result = deliveryLogService.getDeliveryLogs(
                appId, "fcm", "success", notificationId, PageRequest.of(0, 10));

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getProvider()).isEqualTo("FCM");
        assertThat(result.getContent().getFirst().getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("exportDeliveryLogsCsv returns CSV format with correct header and records")
    void testExportDeliveryLogsCsv() {
        DeliveryLog dl = DeliveryLog.builder()
                .appId(appId)
                .notificationId(notificationId)
                .provider("APNS")
                .status("FAILED")
                .statusCode("400")
                .errorMessage("BadDeviceToken")
                .latencyMs(120)
                .createdAt(Instant.now())
                .build();
        dl.setId(UUID.randomUUID());

        when(deliveryLogRepository.findAllForExport(appId)).thenReturn(List.of(dl));

        String csv = deliveryLogService.exportDeliveryLogsCsv(appId);

        assertThat(csv).isNotNull();
        assertThat(csv).contains("id,notification_id,device_id,provider,status,status_code,latency_ms,created_at,error_message");
        assertThat(csv).contains("APNS");
        assertThat(csv).contains("BadDeviceToken");
    }

    @Test
    @DisplayName("getClientEvents returns paginated client event stream")
    void testGetClientEvents() {
        App app = App.builder().name("Test App").build();
        app.setId(appId);

        Notification notification = Notification.builder().title("Promo").build();
        notification.setId(notificationId);

        ClientEvent event = ClientEvent.builder()
                .app(app)
                .notification(notification)
                .eventType(ClientEventType.OPENED)
                .eventName("notification_opened")
                .createdAt(Instant.now())
                .build();
        event.setId(UUID.randomUUID());

        when(clientEventRepository.findWithFilters(eq(appId), eq(notificationId), eq(ClientEventType.OPENED), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(event)));

        Page<SdkEventDto.ClientEventResponse> result = deliveryLogService.getClientEvents(
                appId, notificationId, ClientEventType.OPENED, PageRequest.of(0, 10));

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().getEventType()).isEqualTo(ClientEventType.OPENED);
    }
}
