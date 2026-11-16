package com.onesignal.platform.app.infrastructure;

import com.onesignal.platform.app.domain.App;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppRepository extends JpaRepository<App, UUID> {
    List<App> findByOrganizationId(UUID organizationId);
    Optional<App> findByIdAndOrganizationId(UUID id, UUID organizationId);
    boolean existsByOrganizationIdAndName(UUID organizationId, String name);

    @Query("SELECT DISTINCT a FROM App a " +
           "JOIN MemberRoleAssignment mra ON mra.appId = a.id " +
           "JOIN mra.membership m " +
           "WHERE m.user.id = :userId " +
           "AND m.organization.id = :orgId " +
           "AND m.status = 'ACTIVE' " +
           "AND a.organization.id = :orgId")
    List<App> findAppsExplicitlyAssignedToUser(
            @Param("userId") UUID userId,
            @Param("orgId") UUID orgId
    );
}
