package com.onesignal.platform.notification.domain;

import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "notifications", uniqueConstraints = {
        @UniqueConstraint(name = "uq_notifications_app_idempotency", columnNames = {"app_id", "idempotency_key"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false)
    private App app;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "deep_link", columnDefinition = "TEXT")
    private String deepLink;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_data", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> customData = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 32)
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.HIGH;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    @Builder.Default
    private TargetType targetType = TargetType.ALL;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_payload", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> targetPayload = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.QUEUED;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "sent_count", nullable = false)
    @Builder.Default
    private int sentCount = 0;

    @Column(name = "delivered_count", nullable = false)
    @Builder.Default
    private int deliveredCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private int failedCount = 0;

    @Column(name = "opened_count", nullable = false)
    @Builder.Default
    private int openedCount = 0;

    @Column(name = "created_by")
    private UUID createdBy;
}
