package com.onesignal.platform.event.infrastructure;

import com.onesignal.platform.event.domain.ClientEvent;
import com.onesignal.platform.event.domain.ClientEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ClientEventRepository extends JpaRepository<ClientEvent, UUID> {

    Page<ClientEvent> findByAppIdOrderByCreatedAtDesc(UUID appId, Pageable pageable);

    Page<ClientEvent> findByAppIdAndNotificationIdOrderByCreatedAtDesc(UUID appId, UUID notificationId, Pageable pageable);

    Page<ClientEvent> findByAppIdAndEventTypeOrderByCreatedAtDesc(UUID appId, ClientEventType eventType, Pageable pageable);

    long countByAppIdAndEventType(UUID appId, ClientEventType eventType);

    long countByAppIdAndCreatedAtBetween(UUID appId, Instant from, Instant to);

    @Query("SELECT e.eventType, COUNT(e) FROM ClientEvent e WHERE e.app.id = :appId GROUP BY e.eventType")
    List<Object[]> countByAppIdGroupedByEventType(@Param("appId") UUID appId);

    @Query("SELECT e FROM ClientEvent e WHERE e.app.id = :appId AND e.createdAt >= :since ORDER BY e.createdAt ASC")
    List<ClientEvent> findRecentEvents(@Param("appId") UUID appId, @Param("since") Instant since);

    @Query("SELECT e FROM ClientEvent e LEFT JOIN e.notification n WHERE e.app.id = :appId " +
           "AND (:notificationId IS NULL OR n.id = :notificationId) " +
           "AND (:eventType IS NULL OR e.eventType = :eventType) " +
           "ORDER BY e.createdAt DESC")
    Page<ClientEvent> findWithFilters(
            @Param("appId") UUID appId,
            @Param("notificationId") UUID notificationId,
            @Param("eventType") ClientEventType eventType,
            Pageable pageable
    );
}
