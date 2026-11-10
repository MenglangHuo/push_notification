package com.onesignal.platform.apikey.infrastructure;

import com.onesignal.platform.apikey.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByAppIdOrderByCreatedAtDesc(UUID appId);
    @org.springframework.data.jpa.repository.Query("SELECT k FROM ApiKey k JOIN FETCH k.app a JOIN FETCH a.organization WHERE k.keyHash = :keyHash")
    Optional<ApiKey> findByKeyHash(@org.springframework.data.repository.query.Param("keyHash") String keyHash);
    Optional<ApiKey> findByKeyPrefix(String keyPrefix);
    Optional<ApiKey> findByIdAndAppId(UUID id, UUID appId);
}
