package com.onesignal.platform.notification.application;

import com.onesignal.platform.event.api.dto.SdkEventDto;
import com.onesignal.platform.event.domain.ClientEvent;
import com.onesignal.platform.event.domain.ClientEventType;
import com.onesignal.platform.event.infrastructure.ClientEventRepository;
import com.onesignal.platform.notification.api.dto.NotificationDto;
import com.onesignal.platform.notification.domain.DeliveryLog;
import com.onesignal.platform.notification.infrastructure.DeliveryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryLogService {

    private final DeliveryLogRepository deliveryLogRepository;
    private final ClientEventRepository clientEventRepository;

    @Transactional(readOnly = true)
    public Page<NotificationDto.DeliveryLogResponse> getDeliveryLogs(
            UUID appId,
            String provider,
            String status,
            UUID notificationId,
            Pageable pageable
    ) {
        String cleanProvider = (provider != null && !provider.isBlank()) ? provider.trim().toUpperCase() : null;
        String cleanStatus = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : null;

        Page<DeliveryLog> page = deliveryLogRepository.findWithFilters(appId, cleanProvider, cleanStatus, notificationId, pageable);
        return page.map(this::toDeliveryLogResponse);
    }

    @Transactional(readOnly = true)
    public String exportDeliveryLogsCsv(UUID appId) {
        List<DeliveryLog> logs = deliveryLogRepository.findAllForExport(appId);
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            pw.println("id,notification_id,device_id,provider,status,status_code,latency_ms,created_at,error_message");
            for (DeliveryLog dl : logs) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%s,\"%s\",\"%s\"%n",
                        dl.getId(),
                        dl.getNotificationId(),
                        dl.getDeviceId() != null ? dl.getDeviceId() : "",
                        dl.getProvider(),
                        dl.getStatus(),
                        dl.getStatusCode() != null ? dl.getStatusCode() : "",
                        dl.getLatencyMs() != null ? dl.getLatencyMs() : "",
                        dl.getCreatedAt(),
                        dl.getErrorMessage() != null ? dl.getErrorMessage().replace("\"", "\"\"") : "");
            }
        }
        return sw.toString();
    }

    @Transactional(readOnly = true)
    public Page<SdkEventDto.ClientEventResponse> getClientEvents(
            UUID appId,
            UUID notificationId,
            ClientEventType eventType,
            Pageable pageable
    ) {
        Page<ClientEvent> page = clientEventRepository.findWithFilters(appId, notificationId, eventType, pageable);
        return page.map(this::toClientEventResponse);
    }

    private NotificationDto.DeliveryLogResponse toDeliveryLogResponse(DeliveryLog dl) {
        return NotificationDto.DeliveryLogResponse.builder()
                .id(dl.getId())
                .notificationId(dl.getNotificationId())
                .deviceId(dl.getDeviceId())
                .appId(dl.getAppId())
                .provider(dl.getProvider())
                .status(dl.getStatus())
                .statusCode(dl.getStatusCode())
                .errorMessage(dl.getErrorMessage())
                .latencyMs(dl.getLatencyMs())
                .createdAt(dl.getCreatedAt())
                .build();
    }

    private SdkEventDto.ClientEventResponse toClientEventResponse(ClientEvent event) {
        return SdkEventDto.ClientEventResponse.builder()
                .id(event.getId())
                .appId(event.getApp() != null ? event.getApp().getId() : null)
                .notificationId(event.getNotification() != null ? event.getNotification().getId() : null)
                .deviceId(event.getDevice() != null ? event.getDevice().getId() : null)
                .eventType(event.getEventType())
                .eventName(event.getEventName())
                .properties(event.getProperties())
                .clientTimestamp(event.getClientTimestamp())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
