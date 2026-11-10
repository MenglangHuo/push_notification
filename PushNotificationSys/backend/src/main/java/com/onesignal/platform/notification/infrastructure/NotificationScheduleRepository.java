package com.onesignal.platform.notification.infrastructure;

import com.onesignal.platform.notification.domain.NotificationSchedule;
import com.onesignal.platform.notification.domain.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationScheduleRepository extends JpaRepository<NotificationSchedule, UUID> {

    @Query("SELECT s FROM NotificationSchedule s JOIN FETCH s.notification n JOIN FETCH s.app a " +
           "WHERE s.status = :status AND s.scheduledAt <= :cutoff ORDER BY s.scheduledAt ASC")
    List<NotificationSchedule> findPendingSchedulesDue(
            @Param("status") ScheduleStatus status,
            @Param("cutoff") Instant cutoff
    );

    Optional<NotificationSchedule> findByNotificationId(UUID notificationId);

    List<NotificationSchedule> findByAppIdAndStatus(UUID appId, ScheduleStatus status);
}
