package com.onesignal.platform.apikey.application;

import com.onesignal.platform.apikey.api.dto.ApiKeyDto;
import com.onesignal.platform.apikey.domain.ApiKey;
import com.onesignal.platform.apikey.domain.KeyType;
import com.onesignal.platform.apikey.infrastructure.ApiKeyRepository;
import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.infrastructure.AppRepository;
import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.common.exception.ResourceNotFoundException;
import com.onesignal.platform.common.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final AppRepository appRepository;
    private final AuditLogService auditLogService;

    private final SecureRandom secureRandom = new SecureRandom();
    private static final String ALPHANUMERIC = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    @Transactional(readOnly = true)
    public List<ApiKeyDto.ApiKeySummaryResponse> listKeysForApp(UUID appId) {
        return apiKeyRepository.findByAppIdOrderByCreatedAtDesc(appId).stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ApiKeyDto.ApiKeyCreatedResponse createKey(UUID appId, ApiKeyDto.CreateApiKeyRequest request) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("App not found"));

        String prefix = request.getKeyType() == KeyType.SERVER_REST ? "os_live_" : "os_pub_";
        String randomPart = generateRandomString(32);
        String rawKey = prefix + randomPart;

        String keyPrefix = rawKey.substring(0, Math.min(rawKey.length(), 14));
        String keyHash = hashKey(rawKey);

        ApiKey key = ApiKey.builder()
                .app(app)
                .keyType(request.getKeyType())
                .name(request.getName().trim())
                .keyPrefix(keyPrefix)
                .keyHash(keyHash)
                .scopes(request.getScopes() != null ? request.getScopes() : Collections.emptyList())
                .allowedOrigins(request.getAllowedOrigins() != null ? request.getAllowedOrigins() : Collections.emptyList())
                .allowedBundleIds(request.getAllowedBundleIds() != null ? request.getAllowedBundleIds() : Collections.emptyList())
                .expiresAt(request.getExpiresAt())
                .revoked(false)
                .build();

        ApiKey saved = apiKeyRepository.save(key);

        auditLogService.record(
                app.getOrganization().getId(), appId, TenantContextHolder.getUserId(), "USER",
                "API_KEY_CREATED", "api_key", saved.getId().toString(),
                null, Map.of("name", saved.getName(), "type", saved.getKeyType().name(), "prefix", keyPrefix),
                null, null
        );

        return ApiKeyDto.ApiKeyCreatedResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .keyType(saved.getKeyType())
                .keyPrefix(saved.getKeyPrefix())
                .rawSecretKey(rawKey) // Shown once!
                .scopes(saved.getScopes())
                .allowedOrigins(saved.getAllowedOrigins())
                .allowedBundleIds(saved.getAllowedBundleIds())
                .expiresAt(saved.getExpiresAt())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public ApiKeyDto.RotateApiKeyResponse rotateKey(UUID appId, UUID keyId) {
        ApiKey key = apiKeyRepository.findByIdAndAppId(keyId, appId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found"));

        String prefix = key.getKeyType() == KeyType.SERVER_REST ? "os_live_" : "os_pub_";
        String rawKey = prefix + generateRandomString(32);
        String keyPrefix = rawKey.substring(0, Math.min(rawKey.length(), 14));
        String keyHash = hashKey(rawKey);

        key.setKeyPrefix(keyPrefix);
        key.setKeyHash(keyHash);
        apiKeyRepository.save(key);

        auditLogService.record(
                key.getApp().getOrganization().getId(), appId, TenantContextHolder.getUserId(), "USER",
                "API_KEY_ROTATED", "api_key", key.getId().toString(),
                null, Map.of("name", key.getName(), "newPrefix", keyPrefix), null, null
        );

        return ApiKeyDto.RotateApiKeyResponse.builder()
                .id(key.getId())
                .name(key.getName())
                .keyPrefix(keyPrefix)
                .rawSecretKey(rawKey) // Shown once!
                .build();
    }

    @Transactional
    public void revokeKey(UUID appId, UUID keyId) {
        ApiKey key = apiKeyRepository.findByIdAndAppId(keyId, appId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found"));

        key.setRevoked(true);
        apiKeyRepository.save(key);

        auditLogService.record(
                key.getApp().getOrganization().getId(), appId, TenantContextHolder.getUserId(), "USER",
                "API_KEY_REVOKED", "api_key", key.getId().toString(),
                null, Map.of("name", key.getName()), null, null
        );
    }

    @Transactional
    public Optional<ApiKey> verifyAndTouchKey(String rawSecretKey, KeyType expectedType) {
        if (rawSecretKey == null || rawSecretKey.isBlank()) {
            return Optional.empty();
        }

        String hash = hashKey(rawSecretKey.trim());
        Optional<ApiKey> apiKeyOpt = apiKeyRepository.findByKeyHash(hash);

        if (apiKeyOpt.isEmpty()) {
            return Optional.empty();
        }

        ApiKey key = apiKeyOpt.get();
        if (key.isRevoked() || key.isExpired()) {
            return Optional.empty();
        }

        if (expectedType != null && key.getKeyType() != expectedType) {
            return Optional.empty();
        }

        // Touch last_used_at
        key.setLastUsedAt(Instant.now());
        apiKeyRepository.save(key);
        return Optional.of(key);
    }

    public String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(secureRandom.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    private ApiKeyDto.ApiKeySummaryResponse toSummaryResponse(ApiKey key) {
        return ApiKeyDto.ApiKeySummaryResponse.builder()
                .id(key.getId())
                .name(key.getName())
                .keyType(key.getKeyType())
                .keyPrefix(key.getKeyPrefix())
                .scopes(key.getScopes())
                .allowedOrigins(key.getAllowedOrigins())
                .allowedBundleIds(key.getAllowedBundleIds())
                .expiresAt(key.getExpiresAt())
                .lastUsedAt(key.getLastUsedAt())
                .revoked(key.isRevoked())
                .createdAt(key.getCreatedAt())
                .build();
    }
}
