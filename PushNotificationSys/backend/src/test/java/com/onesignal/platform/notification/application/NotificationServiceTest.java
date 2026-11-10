package com.onesignal.platform.notification.application;

import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.infrastructure.AppRepository;
import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.dispatch.event.NotificationCreatedEvent;
import com.onesignal.platform.dispatch.service.NotificationEventProducer;
import com.onesignal.platform.iam.domain.Organization;
import com.onesignal.platform.notification.api.dto.NotificationDto;
import com.onesignal.platform.notification.domain.Notification;
import com.onesignal.platform.notification.domain.NotificationPriority;
import com.onesignal.platform.notification.domain.NotificationStatus;
import com.onesignal.platform.notification.domain.TargetType;
import com.onesignal.platform.notification.infrastructure.DeliveryLogRepository;
import com.onesignal.platform.notification.infrastructure.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private DeliveryLogRepository deliveryLogRepository;

    @Mock
    private AppRepository appRepository;

    @Mock
    private NotificationEventProducer producer;

    @Mock
    private com.onesignal.platform.notification.infrastructure.NotificationScheduleRepository scheduleRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private NotificationService notificationService;

    private UUID appId;
    private App app;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID();
        Organization org = Organization.builder().name("Acme Org").build();
        org.setId(UUID.randomUUID());
        app = App.builder().organization(org).name("Acme App").build();
        app.setId(appId);
    }

    @Test
    void testCreateAndDispatchSuccess() {
        when(appRepository.findById(appId)).thenReturn(Optional.of(app));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        NotificationDto.CreateNotificationRequest request = NotificationDto.CreateNotificationRequest.builder()
                .title("Flash Sale!")
                .body("50% off everything today only.")
                .priority(NotificationPriority.HIGH)
                .targetType(TargetType.ALL)
                .build();

        NotificationDto.NotificationResponse response =
                notificationService.createAndDispatch(appId, UUID.randomUUID(), request, "idemp-key-123");

        assertNotNull(response);
        assertEquals("Flash Sale!", response.getTitle());
        assertEquals(NotificationStatus.QUEUED, response.getStatus());

        verify(notificationRepository).save(any(Notification.class));
        verify(producer).sendNotificationCreated(any(NotificationCreatedEvent.class));
    }

    @Test
    void testIdempotencyReturnsExistingWithoutRepublishing() {
        Notification existing = Notification.builder()
                .app(app)
                .title("Duplicate Title")
                .body("Duplicate Body")
                .idempotencyKey("idemp-unique-456")
                .status(NotificationStatus.COMPLETED)
                .build();
        existing.setId(UUID.randomUUID());

        when(appRepository.findById(appId)).thenReturn(Optional.of(app));
        when(notificationRepository.findByAppIdAndIdempotencyKey(appId, "idemp-unique-456"))
                .thenReturn(Optional.of(existing));

        NotificationDto.CreateNotificationRequest request = NotificationDto.CreateNotificationRequest.builder()
                .title("Duplicate Title")
                .body("Duplicate Body")
                .build();

        NotificationDto.NotificationResponse response =
                notificationService.createAndDispatch(appId, null, request, "idemp-unique-456");

        assertEquals(existing.getId(), response.getId());
        assertEquals(NotificationStatus.COMPLETED, response.getStatus());
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(producer, never()).sendNotificationCreated(any());
    }

    @Test
    void testCreateScheduledNotification() {
        when(appRepository.findById(appId)).thenReturn(Optional.of(app));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        java.time.Instant futureTime = java.time.Instant.now().plusSeconds(3600);
        NotificationDto.CreateNotificationRequest request = NotificationDto.CreateNotificationRequest.builder()
                .title("Scheduled Sale")
                .body("Coming soon")
                .scheduledAt(futureTime)
                .scheduleType(com.onesignal.platform.notification.domain.ScheduleType.FIXED_TIME)
                .build();

        NotificationDto.NotificationResponse response =
                notificationService.createAndDispatch(appId, UUID.randomUUID(), request, null);

        assertNotNull(response);
        assertEquals(NotificationStatus.SCHEDULED, response.getStatus());

        verify(scheduleRepository).save(any(com.onesignal.platform.notification.domain.NotificationSchedule.class));
        verify(producer, never()).sendNotificationCreated(any());
    }

    @Test
    void testCancelScheduledNotification() {
        UUID notifId = UUID.randomUUID();
        Notification scheduled = Notification.builder()
                .app(app)
                .title("Future notification")
                .body("Text")
                .status(NotificationStatus.SCHEDULED)
                .build();
        scheduled.setId(notifId);

        when(notificationRepository.findByIdAndAppId(notifId, appId)).thenReturn(Optional.of(scheduled));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.onesignal.platform.notification.domain.NotificationSchedule schedule =
                com.onesignal.platform.notification.domain.NotificationSchedule.builder()
                        .app(app)
                        .notification(scheduled)
                        .status(com.onesignal.platform.notification.domain.ScheduleStatus.PENDING)
                        .build();
        when(scheduleRepository.findByNotificationId(notifId)).thenReturn(Optional.of(schedule));

        NotificationDto.NotificationResponse response =
                notificationService.cancelNotification(appId, notifId, UUID.randomUUID());

        assertNotNull(response);
        assertEquals(NotificationStatus.CANCELLED, response.getStatus());
        assertEquals(com.onesignal.platform.notification.domain.ScheduleStatus.CANCELLED, schedule.getStatus());
    }
}
