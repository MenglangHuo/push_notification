package com.onesignal.platform.iam.infrastructure;

import com.onesignal.platform.iam.domain.UserMfa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserMfaRepository extends JpaRepository<UserMfa, UUID> {
    Optional<UserMfa> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
