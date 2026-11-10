package com.onesignal.platform.notification.application;

import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.dispatch.event.NotificationCreatedEvent;
import com.onesignal.platform.dispatch.service.NotificationEventProducer;
import com.onesignal.platform.notification.domain.Notification;
import com.onesignal.platform.notification.domain.NotificationSchedule;
import com.onesignal.platform.notification.domain.NotificationStatus;
import com.onesignal.platform.notification.domain.ScheduleStatus;
import com.onesignal.platform.notification.infrastructure.NotificationRepository;
import com.onesignal.platform.notification.infrastructure.NotificationScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignSchedulerServiceTest {

    @Mock
    private NotificationScheduleRepository scheduleRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationEventProducer producer;

    @InjectMocks
    private CampaignSchedulerService schedulerService;

    private UUID appId;
    private App app;
    private Notification notification;
    private NotificationSchedule schedule;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID();
        app = App.builder().name("Test App").build();
        app.setId(appId);

        notification = Notification.builder()
                .app(app)
                .title("Scheduled Flash Sale")
                .body("Deals start now!")
                .status(NotificationStatus.SCHEDULED)
                .build();
        notification.setId(UUID.randomUUID());

        schedule = NotificationSchedule.builder()
                .app(app)
                .notification(notification)
                .scheduledAt(Instant.now().minusSeconds(10))
                .status(ScheduleStatus.PENDING)
                .build();
        schedule.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Should trigger due scheduled campaigns and emit Kafka events")
    void testProcessDueSchedules() {
        when(scheduleRepository.findPendingSchedulesDue(eq(ScheduleStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(schedule));

        schedulerService.processDueSchedules();

        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.TRIGGERED);
        assertThat(schedule.getTriggeredAt()).isNotNull();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.QUEUED);

        verify(scheduleRepository).save(schedule);
        verify(notificationRepository).save(notification);

        ArgumentCaptor<NotificationCreatedEvent> eventCaptor = ArgumentCaptor.forClass(NotificationCreatedEvent.class);
        verify(producer).sendNotificationCreated(eventCaptor.capture());

        NotificationCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getNotificationId()).isEqualTo(notification.getId());
        assertThat(event.getAppId()).isEqualTo(appId);
    }

    @Test
    @DisplayName("Should skip cancelled notifications when schedule fires")
    void testSkipCancelledNotification() {
        notification.setStatus(NotificationStatus.CANCELLED);
        when(scheduleRepository.findPendingSchedulesDue(eq(ScheduleStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(schedule));

        schedulerService.processDueSchedules();

        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.CANCELLED);
        verify(scheduleRepository).save(schedule);
        verify(producer, never()).sendNotificationCreated(any());
    }
}
