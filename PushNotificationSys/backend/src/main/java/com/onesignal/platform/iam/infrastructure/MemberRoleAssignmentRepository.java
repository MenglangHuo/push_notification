package com.onesignal.platform.iam.infrastructure;

import com.onesignal.platform.iam.domain.MemberRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface MemberRoleAssignmentRepository extends JpaRepository<MemberRoleAssignment, UUID> {

    List<MemberRoleAssignment> findByMembershipId(UUID membershipId);

    void deleteByMembershipId(UUID membershipId);

    @Query("SELECT DISTINCT p.code FROM MemberRoleAssignment mra " +
           "JOIN mra.membership m " +
           "JOIN mra.role r " +
           "JOIN r.permissions p " +
           "WHERE m.user.id = :userId " +
           "AND m.organization.id = :orgId " +
           "AND m.status = 'ACTIVE' " +
           "AND (mra.appId IS NULL OR (:appId IS NOT NULL AND mra.appId = :appId))")
    Set<String> findEffectivePermissions(
            @Param("userId") UUID userId,
            @Param("orgId") UUID orgId,
            @Param("appId") UUID appId
    );

    @Query("SELECT DISTINCT p.code FROM MemberRoleAssignment mra " +
           "JOIN mra.membership m " +
           "JOIN mra.role r " +
           "JOIN r.permissions p " +
           "WHERE m.user.id = :userId " +
           "AND m.organization.id = :orgId " +
           "AND m.status = 'ACTIVE'")
    Set<String> findAllOrgPermissions(
            @Param("userId") UUID userId,
            @Param("orgId") UUID orgId
    );
}
