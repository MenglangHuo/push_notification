package com.onesignal.platform.iam.infrastructure;

import com.onesignal.platform.iam.domain.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    Optional<Membership> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);
    List<Membership> findByOrganizationId(UUID organizationId);
    List<Membership> findByUserId(UUID userId);

    @Query("SELECT m FROM Membership m " +
           "JOIN FETCH m.user u " +
           "LEFT JOIN FETCH m.roleAssignments ra " +
           "LEFT JOIN FETCH ra.role r " +
           "WHERE m.organization.id = :orgId")
    List<Membership> findAllByOrganizationIdWithRoles(@Param("orgId") UUID orgId);
}
