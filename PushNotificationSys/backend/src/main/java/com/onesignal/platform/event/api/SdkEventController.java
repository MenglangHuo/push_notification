package com.onesignal.platform.event.api;

import com.onesignal.platform.common.exception.UnauthorizedException;
import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.device.infrastructure.DeviceRepository;
import com.onesignal.platform.event.api.dto.SdkEventDto;
import com.onesignal.platform.event.event.EventBatchIngestMessage;
import com.onesignal.platform.event.infrastructure.EventIngestProducer;
import com.onesignal.platform.notification.infrastructure.NotificationRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/sdk/v1")
@RequiredArgsConstructor
public class SdkEventController {

    private final EventIngestProducer eventIngestProducer;
    private final NotificationRepository notificationRepository;
    private final DeviceRepository deviceRepository;

    @PostMapping("/events")
    public ResponseEntity<SdkEventDto.IngestEventsResponse> ingestEvents(
            @Valid @RequestBody SdkEventDto.IngestEventsRequest request,
            HttpServletRequest httpRequest
    ) {
        UUID appId = resolveAppId(request, httpRequest);
        if (appId == null) {
            throw new UnauthorizedException("Missing authenticated app scope");
        }

        int count = request.getEvents() != null ? request.getEvents().size() : 0;
        log.info("Ingesting {} SDK client events for app {}", count, appId);

        eventIngestProducer.sendEventBatch(EventBatchIngestMessage.builder()
                .appId(appId)
                .events(request.getEvents())
                .build());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
                SdkEventDto.IngestEventsResponse.builder()
                        .acceptedCount(count)
                        .status("QUEUED")
                        .build()
        );
    }

    private UUID resolveAppId(SdkEventDto.IngestEventsRequest request, HttpServletRequest httpRequest) {
        UUID appId = TenantContextHolder.getAppId();
        if (appId != null) {
            return appId;
        }

        if (request != null && request.getAppId() != null) {
            return request.getAppId();
        }

        String headerAppId = httpRequest.getHeader("X-App-Id");
        if (headerAppId != null && !headerAppId.isBlank()) {
            try {
                return UUID.fromString(headerAppId.trim());
            } catch (IllegalArgumentException ignored) {}
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID uId) {
            boolean isApiKey = auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_SDK_CLIENT".equals(a.getAuthority()) || "ROLE_API_CLIENT".equals(a.getAuthority()));
            if (isApiKey) {
                return uId;
            }
        }

        if (request != null && request.getEvents() != null) {
            for (SdkEventDto.EventItem item : request.getEvents()) {
                if (item.getNotificationId() != null) {
                    try {
                        var notifOpt = notificationRepository.findById(item.getNotificationId());
                        if (notifOpt.isPresent()) {
                            return notifOpt.get().getApp().getId();
                        }
                    } catch (Exception ignored) {}
                }
                if (item.getDeviceId() != null) {
                    try {
                        var devOpt = deviceRepository.findById(item.getDeviceId());
                        if (devOpt.isPresent()) {
                            return devOpt.get().getApp().getId();
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        return null;
    }
}
