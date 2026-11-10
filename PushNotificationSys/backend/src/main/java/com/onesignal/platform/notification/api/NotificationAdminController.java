package com.onesignal.platform.notification.api;

import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.notification.api.dto.NotificationDto;
import com.onesignal.platform.notification.application.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin/api/v1/apps/{appId}/notifications")
@RequiredArgsConstructor
public class NotificationAdminController {

    private final NotificationService notificationService;

    @PostMapping
    @PreAuthorize("hasPermission(#appId, 'app', 'notification:create')")
    public ResponseEntity<NotificationDto.NotificationResponse> createNotification(
            @PathVariable UUID appId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody NotificationDto.CreateNotificationRequest request
    ) {
        UUID userId = TenantContextHolder.getUserId();
        NotificationDto.NotificationResponse response =
                notificationService.createAndDispatch(appId, userId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasPermission(#appId, 'app', 'notification:read')")
    public ResponseEntity<Page<NotificationDto.NotificationResponse>> listNotifications(
            @PathVariable UUID appId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(notificationService.listNotifications(appId, pageRequest));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission(#appId, 'app', 'notification:read')")
    public ResponseEntity<NotificationDto.NotificationResponse> getNotification(
            @PathVariable UUID appId,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(notificationService.getNotification(appId, id));
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasPermission(#appId, 'app', 'notification:read')")
    public ResponseEntity<Page<NotificationDto.DeliveryLogResponse>> listDeliveryLogs(
            @PathVariable UUID appId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(notificationService.listDeliveryLogs(appId, id, pageRequest));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasPermission(#appId, 'app', 'notification:create')")
    public ResponseEntity<NotificationDto.NotificationResponse> cancelNotification(
            @PathVariable UUID appId,
            @PathVariable UUID id
    ) {
        UUID userId = TenantContextHolder.getUserId();
        return ResponseEntity.ok(notificationService.cancelNotification(appId, id, userId));
    }
}
