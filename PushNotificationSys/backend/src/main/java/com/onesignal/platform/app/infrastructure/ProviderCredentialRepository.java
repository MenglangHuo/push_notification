package com.onesignal.platform.app.infrastructure;

import com.onesignal.platform.app.domain.ProviderCredential;
import com.onesignal.platform.app.domain.ProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderCredentialRepository extends JpaRepository<ProviderCredential, UUID> {
    Optional<ProviderCredential> findByAppIdAndProviderType(UUID appId, ProviderType providerType);
    List<ProviderCredential> findByAppId(UUID appId);
    void deleteByAppIdAndProviderType(UUID appId, ProviderType providerType);
}
