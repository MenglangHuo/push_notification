package com.onesignal.platform.device.domain;

import com.onesignal.platform.app.domain.App;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "devices", uniqueConstraints = {
        @UniqueConstraint(name = "uq_devices_app_token", columnNames = {"app_id", "push_token"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false)
    private App app;

    @Column(name = "external_user_id", length = 255)
    private String externalUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 32)
    private DevicePlatform platform;

    @Column(name = "push_token", nullable = false, columnDefinition = "TEXT")
    private String pushToken;

    @Column(name = "web_auth_key", columnDefinition = "TEXT")
    private String webAuthKey;

    @Column(name = "web_p256dh_key", columnDefinition = "TEXT")
    private String webP256dhKey;

    @Column(name = "device_model", length = 128)
    private String deviceModel;

    @Column(name = "os_version", length = 64)
    private String osVersion;

    @Column(name = "app_version", length = 64)
    private String appVersion;

    @Column(name = "language", nullable = false, length = 16)
    @Builder.Default
    private String language = "en";

    @Column(name = "timezone", nullable = false, length = 64)
    @Builder.Default
    private String timezone = "UTC";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_active_at", nullable = false)
    @Builder.Default
    private Instant lastActiveAt = Instant.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DeviceTag> tags = new ArrayList<>();
}
