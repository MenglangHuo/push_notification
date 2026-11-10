package com.onesignal.platform.iam.infrastructure;

import com.onesignal.platform.iam.domain.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Optional<Invitation> findByTokenHash(String tokenHash);

    @Query("SELECT i FROM Invitation i " +
           "JOIN FETCH i.organization " +
           "JOIN FETCH i.role " +
           "JOIN FETCH i.invitedBy " +
           "WHERE i.tokenHash = :tokenHash")
    Optional<Invitation> findByTokenHashWithDetails(@Param("tokenHash") String tokenHash);

    List<Invitation> findByOrganizationIdAndAcceptedAtIsNull(UUID organizationId);
}
