package com.onesignal.platform.iam.infrastructure;

import com.onesignal.platform.iam.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByNameAndOrganizationId(String name, UUID organizationId);
    Optional<Role> findByNameAndOrganizationIdIsNull(String name);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.organization.id = :orgId OR r.organization IS NULL")
    List<Role> findAllAvailableForOrg(@Param("orgId") UUID orgId);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.id = :id")
    Optional<Role> findByIdWithPermissions(@Param("id") UUID id);
}
