package com.onesignal.platform.notification.api;

import com.onesignal.platform.common.exception.UnauthorizedException;
import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.notification.api.dto.NotificationDto;
import com.onesignal.platform.notification.application.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationServerApiController {

    private final NotificationService notificationService;

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_API_CLIENT')")
    public ResponseEntity<NotificationDto.NotificationResponse> createNotification(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody NotificationDto.CreateNotificationRequest request
    ) {
        UUID appId = resolveAppId();
        log.info("Server API creating notification for app: {}", appId);
        NotificationDto.NotificationResponse response =
                notificationService.createAndDispatch(appId, null, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_API_CLIENT')")
    public ResponseEntity<NotificationDto.NotificationResponse> getNotification(
            @PathVariable UUID id
    ) {
        UUID appId = resolveAppId();
        return ResponseEntity.ok(notificationService.getNotification(appId, id));
    }

    private UUID resolveAppId() {
        UUID appId = TenantContextHolder.getAppId();
        if (appId == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UUID uId) {
                appId = uId;
            }
        }
        if (appId == null) {
            throw new UnauthorizedException("Invalid or missing Server API Key credentials");
        }
        return appId;
    }
}
