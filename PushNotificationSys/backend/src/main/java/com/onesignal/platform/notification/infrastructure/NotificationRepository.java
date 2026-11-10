package com.onesignal.platform.notification.infrastructure;

import com.onesignal.platform.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndAppId(UUID id, UUID appId);

    Optional<Notification> findByAppIdAndIdempotencyKey(UUID appId, String idempotencyKey);

    Page<Notification> findByAppIdOrderByCreatedAtDesc(UUID appId, Pageable pageable);

    long countByAppId(UUID appId);
}
