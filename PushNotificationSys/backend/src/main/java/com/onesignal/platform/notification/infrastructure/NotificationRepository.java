package com.onesignal.platform.notification.infrastructure;

import com.onesignal.platform.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndAppId(UUID id, UUID appId);

    Optional<Notification> findByAppIdAndIdempotencyKey(UUID appId, String idempotencyKey);

    Page<Notification> findByAppIdOrderByCreatedAtDesc(UUID appId, Pageable pageable);

    long countByAppId(UUID appId);

    @Query("SELECT n FROM Notification n WHERE n.app.id = :appId AND n.sentCount > 0 ORDER BY n.sentCount DESC")
    List<Notification> findTopCampaignsByAppId(@Param("appId") UUID appId, Pageable pageable);
}
