package com.onesignal.platform.app.domain;

import com.onesignal.platform.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "provider_credentials", uniqueConstraints = {
        @UniqueConstraint(name = "uq_credentials_app_provider", columnNames = {"app_id", "provider_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderCredential extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false)
    private App app;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private ProviderType providerType;

    @Column(name = "credential_data_encrypted", nullable = false, columnDefinition = "TEXT")
    private String credentialDataEncrypted;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_masked", columnDefinition = "jsonb")
    private Map<String, Object> metadataMasked;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
