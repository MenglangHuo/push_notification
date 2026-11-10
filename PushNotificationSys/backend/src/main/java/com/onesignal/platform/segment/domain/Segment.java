package com.onesignal.platform.segment.domain;

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
@Table(name = "segments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_segments_app_name", columnNames = {"app_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Segment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", nullable = false)
    private App app;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_ast", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> filterAst = new HashMap<>();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_by")
    private UUID createdBy;
}
