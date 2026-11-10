package com.onesignal.platform.notification.application;

import com.onesignal.platform.dispatch.event.NotificationCreatedEvent;
import com.onesignal.platform.dispatch.service.NotificationEventProducer;
import com.onesignal.platform.notification.domain.Notification;
import com.onesignal.platform.notification.domain.NotificationSchedule;
import com.onesignal.platform.notification.domain.NotificationStatus;
import com.onesignal.platform.notification.domain.ScheduleStatus;
import com.onesignal.platform.notification.infrastructure.NotificationRepository;
import com.onesignal.platform.notification.infrastructure.NotificationScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignSchedulerService {

    private final NotificationScheduleRepository scheduleRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationEventProducer producer;

    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(name = "campaign_scheduler_trigger", lockAtLeastFor = "PT2S", lockAtMostFor = "PT10S")
    @Transactional
    public void processDueSchedules() {
        Instant now = Instant.now();
        List<NotificationSchedule> dueSchedules = scheduleRepository.findPendingSchedulesDue(ScheduleStatus.PENDING, now);

        if (dueSchedules.isEmpty()) {
            return;
        }

        log.info("Found {} scheduled campaign(s) due for dispatch at {}", dueSchedules.size(), now);

        for (NotificationSchedule schedule : dueSchedules) {
            try {
                triggerSchedule(schedule, now);
            } catch (Exception e) {
                log.error("Failed to trigger scheduled notification {}: {}", schedule.getId(), e.getMessage(), e);
                schedule.setStatus(ScheduleStatus.FAILED);
                scheduleRepository.save(schedule);
            }
        }
    }

    private void triggerSchedule(NotificationSchedule schedule, Instant now) {
        Notification notification = schedule.getNotification();
        if (notification == null) {
            log.warn("Schedule {} has null notification, marking failed", schedule.getId());
            schedule.setStatus(ScheduleStatus.FAILED);
            scheduleRepository.save(schedule);
            return;
        }

        if (notification.getStatus() == NotificationStatus.CANCELLED) {
            log.info("Scheduled notification {} was cancelled, skipping", notification.getId());
            schedule.setStatus(ScheduleStatus.CANCELLED);
            scheduleRepository.save(schedule);
            return;
        }

        // 1. Mark schedule as TRIGGERED
        schedule.setStatus(ScheduleStatus.TRIGGERED);
        schedule.setTriggeredAt(now);
        scheduleRepository.save(schedule);

        // 2. Transition notification status to QUEUED
        notification.setStatus(NotificationStatus.QUEUED);
        notificationRepository.save(notification);

        // 3. Emit Kafka event to begin pipeline routing & dispatch
        producer.sendNotificationCreated(NotificationCreatedEvent.builder()
                .notificationId(notification.getId())
                .appId(schedule.getApp().getId())
                .build());

        log.info("Successfully triggered scheduled notification {} for app {}",
                notification.getId(), schedule.getApp().getId());
    }
}
