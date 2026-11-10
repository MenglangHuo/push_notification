package com.onesignal.platform.notification.api;

import com.onesignal.platform.event.api.dto.SdkEventDto;
import com.onesignal.platform.event.domain.ClientEventType;
import com.onesignal.platform.notification.api.dto.NotificationDto;
import com.onesignal.platform.notification.application.DeliveryLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin/api/v1/apps/{appId}/logs")
@RequiredArgsConstructor
public class DeliveryLogAdminController {

    private final DeliveryLogService deliveryLogService;

    @GetMapping("/delivery")
    @PreAuthorize("hasPermission(#appId, 'app', 'log:read')")
    public ResponseEntity<Page<NotificationDto.DeliveryLogResponse>> getDeliveryLogs(
            @PathVariable UUID appId,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID notificationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(deliveryLogService.getDeliveryLogs(appId, provider, status, notificationId, pageRequest));
    }

    @GetMapping("/delivery/export")
    @PreAuthorize("hasPermission(#appId, 'app', 'log:export')")
    public ResponseEntity<String> exportDeliveryLogs(
            @PathVariable UUID appId
    ) {
        String csv = deliveryLogService.exportDeliveryLogsCsv(appId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"delivery-logs-" + appId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping("/events")
    @PreAuthorize("hasPermission(#appId, 'app', 'log:read')")
    public ResponseEntity<Page<SdkEventDto.ClientEventResponse>> getClientEvents(
            @PathVariable UUID appId,
            @RequestParam(required = false) UUID notificationId,
            @RequestParam(required = false) ClientEventType eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(deliveryLogService.getClientEvents(appId, notificationId, eventType, pageRequest));
    }
}
