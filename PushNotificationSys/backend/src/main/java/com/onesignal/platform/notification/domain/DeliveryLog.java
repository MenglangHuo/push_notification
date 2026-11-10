package com.onesignal.platform.notification.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "app_id", nullable = false)
    private UUID appId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider; // FCM, APNS, WEB_PUSH

    @Column(name = "status", nullable = false, length = 32)
    private String status; // SUCCESS, FAILED, RETRYING

    @Column(name = "status_code", length = 64)
    private String statusCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
