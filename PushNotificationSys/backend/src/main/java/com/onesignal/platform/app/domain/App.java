package com.onesignal.platform.app.domain;

import com.onesignal.platform.common.domain.BaseEntity;
import com.onesignal.platform.iam.domain.Organization;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "apps", uniqueConstraints = {
        @UniqueConstraint(name = "uq_apps_org_name", columnNames = {"organization_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class App extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "app", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProviderCredential> credentials = new ArrayList<>();
}
