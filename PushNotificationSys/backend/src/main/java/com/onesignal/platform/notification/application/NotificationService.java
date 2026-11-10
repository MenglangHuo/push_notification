package com.onesignal.platform.notification.application;

import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.infrastructure.AppRepository;
import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.common.exception.ResourceNotFoundException;
import com.onesignal.platform.dispatch.event.NotificationCreatedEvent;
import com.onesignal.platform.dispatch.service.NotificationEventProducer;
import com.onesignal.platform.notification.api.dto.NotificationDto;
import com.onesignal.platform.notification.domain.DeliveryLog;
import com.onesignal.platform.notification.domain.Notification;
import com.onesignal.platform.notification.domain.NotificationPriority;
import com.onesignal.platform.notification.domain.NotificationStatus;
import com.onesignal.platform.notification.domain.TargetType;
import com.onesignal.platform.notification.infrastructure.DeliveryLogRepository;
import com.onesignal.platform.notification.infrastructure.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final com.onesignal.platform.notification.infrastructure.NotificationScheduleRepository scheduleRepository;
    private final AppRepository appRepository;
    private final NotificationEventProducer producer;
    private final AuditLogService auditLogService;

    @Transactional
    public NotificationDto.NotificationResponse createAndDispatch(
            UUID appId,
            UUID userId,
            NotificationDto.CreateNotificationRequest request,
            String idempotencyKey
    ) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("App not found: " + appId));

        // 1. Idempotency Check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Notification> existingOpt = notificationRepository.findByAppIdAndIdempotencyKey(appId, idempotencyKey.trim());
            if (existingOpt.isPresent()) {
                log.info("Idempotent hit for key: {} in app: {}", idempotencyKey, appId);
                return toNotificationResponse(existingOpt.get());
            }
        }

        boolean isScheduled = request.getScheduledAt() != null && request.getScheduledAt().isAfter(java.time.Instant.now());

        // 2. Persist Notification entity
        Notification notification = Notification.builder()
                .app(app)
                .title(request.getTitle().trim())
                .body(request.getBody().trim())
                .imageUrl(request.getImageUrl())
                .deepLink(request.getDeepLink())
                .customData(request.getCustomData() != null ? request.getCustomData() : new HashMap<>())
                .priority(request.getPriority() != null ? request.getPriority() : NotificationPriority.HIGH)
                .targetType(request.getTargetType() != null ? request.getTargetType() : TargetType.ALL)
                .targetPayload(request.getTargetPayload() != null ? request.getTargetPayload() : new HashMap<>())
                .status(isScheduled ? NotificationStatus.SCHEDULED : NotificationStatus.QUEUED)
                .idempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey.trim() : null)
                .createdBy(userId)
                .build();

        Notification saved = notificationRepository.save(notification);

        if (isScheduled) {
            // Schedule for future execution via CampaignSchedulerService
            com.onesignal.platform.notification.domain.NotificationSchedule schedule =
                    com.onesignal.platform.notification.domain.NotificationSchedule.builder()
                            .app(app)
                            .notification(saved)
                            .scheduleType(request.getScheduleType() != null
                                    ? request.getScheduleType()
                                    : com.onesignal.platform.notification.domain.ScheduleType.FIXED_TIME)
                            .scheduledAt(request.getScheduledAt())
                            .userTargetTime(request.getUserTargetTime())
                            .status(com.onesignal.platform.notification.domain.ScheduleStatus.PENDING)
                            .build();
            scheduleRepository.save(schedule);
            log.info("Scheduled notification {} for future dispatch at {}", saved.getId(), request.getScheduledAt());
        } else {
            // Emit Kafka event for immediate asynchronous router & worker dispatch
            producer.sendNotificationCreated(NotificationCreatedEvent.builder()
                    .notificationId(saved.getId())
                    .appId(appId)
                    .build());
            log.info("Created and queued immediate notification {} for app {}", saved.getId(), appId);
        }

        // 4. Record audit log
        auditLogService.record(
                app.getOrganization().getId(),
                appId,
                userId,
                userId != null ? "USER" : "API_KEY",
                isScheduled ? "NOTIFICATION_SCHEDULED" : "NOTIFICATION_CREATED",
                "notification",
                saved.getId().toString(),
                null,
                Map.of(
                        "title", saved.getTitle(),
                        "targetType", saved.getTargetType().name(),
                        "scheduled", isScheduled
                ),
                null,
                null
        );

        return toNotificationResponse(saved);
    }

    @Transactional
    public NotificationDto.NotificationResponse cancelNotification(UUID appId, UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findByIdAndAppId(notificationId, appId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));

        if (notification.getStatus() != NotificationStatus.SCHEDULED && notification.getStatus() != NotificationStatus.QUEUED) {
            throw new IllegalStateException("Only SCHEDULED or QUEUED notifications can be cancelled. Current status: " + notification.getStatus());
        }

        notification.setStatus(NotificationStatus.CANCELLED);
        Notification updated = notificationRepository.save(notification);

        scheduleRepository.findByNotificationId(notificationId).ifPresent(s -> {
            s.setStatus(com.onesignal.platform.notification.domain.ScheduleStatus.CANCELLED);
            scheduleRepository.save(s);
        });

        auditLogService.record(
                notification.getApp().getOrganization().getId(),
                appId,
                userId,
                "USER",
                "NOTIFICATION_CANCELLED",
                "notification",
                notificationId.toString(),
                null,
                Map.of("title", notification.getTitle()),
                null,
                null
        );

        log.info("Cancelled notification {} in app {}", notificationId, appId);
        return toNotificationResponse(updated);
    }

    @Transactional(readOnly = true)
    public NotificationDto.NotificationResponse getNotification(UUID appId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndAppId(notificationId, appId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        return toNotificationResponse(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto.NotificationResponse> listNotifications(UUID appId, Pageable pageable) {
        return notificationRepository.findByAppIdOrderByCreatedAtDesc(appId, pageable)
                .map(this::toNotificationResponse);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto.DeliveryLogResponse> listDeliveryLogs(UUID appId, UUID notificationId, Pageable pageable) {
        return deliveryLogRepository.findByNotificationIdOrderByCreatedAtDesc(notificationId, pageable)
                .map(this::toDeliveryLogResponse);
    }

    private NotificationDto.NotificationResponse toNotificationResponse(Notification notif) {
        var scheduleOpt = scheduleRepository.findByNotificationId(notif.getId());

        return NotificationDto.NotificationResponse.builder()
                .id(notif.getId())
                .appId(notif.getApp().getId())
                .title(notif.getTitle())
                .body(notif.getBody())
                .imageUrl(notif.getImageUrl())
                .deepLink(notif.getDeepLink())
                .customData(notif.getCustomData())
                .priority(notif.getPriority())
                .targetType(notif.getTargetType())
                .targetPayload(notif.getTargetPayload())
                .status(notif.getStatus())
                .idempotencyKey(notif.getIdempotencyKey())
                .scheduledAt(scheduleOpt.map(com.onesignal.platform.notification.domain.NotificationSchedule::getScheduledAt).orElse(null))
                .scheduleType(scheduleOpt.map(com.onesignal.platform.notification.domain.NotificationSchedule::getScheduleType).orElse(null))
                .userTargetTime(scheduleOpt.map(com.onesignal.platform.notification.domain.NotificationSchedule::getUserTargetTime).orElse(null))
                .sentCount(notif.getSentCount())
                .deliveredCount(notif.getDeliveredCount())
                .failedCount(notif.getFailedCount())
                .openedCount(notif.getOpenedCount())
                .createdAt(notif.getCreatedAt())
                .updatedAt(notif.getUpdatedAt())
                .build();
    }

    private NotificationDto.DeliveryLogResponse toDeliveryLogResponse(DeliveryLog log) {
        return NotificationDto.DeliveryLogResponse.builder()
                .id(log.getId())
                .notificationId(log.getNotificationId())
                .deviceId(log.getDeviceId())
                .appId(log.getAppId())
                .provider(log.getProvider())
                .status(log.getStatus())
                .statusCode(log.getStatusCode())
                .errorMessage(log.getErrorMessage())
                .latencyMs(log.getLatencyMs())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
