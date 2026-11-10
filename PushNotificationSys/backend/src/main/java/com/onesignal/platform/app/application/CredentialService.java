package com.onesignal.platform.app.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesignal.platform.app.api.dto.CredentialDto;
import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.domain.ProviderCredential;
import com.onesignal.platform.app.domain.ProviderType;
import com.onesignal.platform.app.infrastructure.AppRepository;
import com.onesignal.platform.app.infrastructure.ProviderCredentialRepository;
import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.common.exception.AppException;
import com.onesignal.platform.common.exception.ResourceNotFoundException;
import com.onesignal.platform.common.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialService {

    private final ProviderCredentialRepository credentialRepository;
    private final AppRepository appRepository;
    private final CryptoVaultService cryptoVaultService;
    private final VapidKeyGenerator vapidKeyGenerator;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<CredentialDto.CredentialResponse> listCredentials(UUID appId) {
        return credentialRepository.findByAppId(appId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CredentialDto.CredentialResponse saveFcmCredential(UUID appId, String serviceAccountJson) {
        App app = getAppOrThrow(appId);

        Map<String, Object> maskedMeta = new HashMap<>();
        try {
            JsonNode root = objectMapper.readTree(serviceAccountJson);
            if (!root.has("project_id") || !root.has("private_key")) {
                throw new AppException("Invalid Firebase service account JSON: missing project_id or private_key", HttpStatus.BAD_REQUEST);
            }
            maskedMeta.put("project_id", root.get("project_id").asText());
            maskedMeta.put("client_email", root.path("client_email").asText());
            maskedMeta.put("type", "service_account");
        } catch (Exception e) {
            if (e instanceof AppException ae) throw ae;
            throw new AppException("Malformed JSON file provided: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        String encrypted = cryptoVaultService.encrypt(serviceAccountJson);
        ProviderCredential cred = upsertCredential(app, ProviderType.FCM, encrypted, maskedMeta);

        auditLogService.record(
                app.getOrganization().getId(), appId, TenantContextHolder.getUserId(), "USER",
                "CREDENTIAL_CONFIGURED", "credential", cred.getId().toString(),
                null, Map.of("provider", "FCM", "project_id", maskedMeta.get("project_id")), null, null
        );

        return toResponse(cred);
    }

    @Transactional
    public CredentialDto.CredentialResponse saveApnsCredential(
            UUID appId, String rawP8Key, String keyId, String teamId, String bundleId) {
        App app = getAppOrThrow(appId);

        if (rawP8Key == null || rawP8Key.isBlank() || keyId == null || teamId == null || bundleId == null) {
            throw new AppException("APNs key, Key ID, Team ID, and Bundle ID are all required", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> maskedMeta = Map.of(
                "key_id", keyId.trim(),
                "team_id", teamId.trim(),
                "bundle_id", bundleId.trim()
        );

        String encrypted = cryptoVaultService.encrypt(rawP8Key.trim());
        ProviderCredential cred = upsertCredential(app, ProviderType.APNS, encrypted, maskedMeta);

        auditLogService.record(
                app.getOrganization().getId(), appId, TenantContextHolder.getUserId(), "USER",
                "CREDENTIAL_CONFIGURED", "credential", cred.getId().toString(),
                null, Map.of("provider", "APNS", "key_id", keyId), null, null
        );

        return toResponse(cred);
    }

    @Transactional
    public CredentialDto.GenerateVapidResponse generateVapid(UUID appId) {
        App app = getAppOrThrow(appId);

        VapidKeyGenerator.VapidKeyPair keyPair = vapidKeyGenerator.generate();
        String encryptedPrivateKey = cryptoVaultService.encrypt(keyPair.getPrivateKey());

        Map<String, Object> maskedMeta = Map.of(
                "public_key", keyPair.getPublicKey()
        );

        ProviderCredential cred = upsertCredential(app, ProviderType.WEBPUSH, encryptedPrivateKey, maskedMeta);

        auditLogService.record(
                app.getOrganization().getId(), appId, TenantContextHolder.getUserId(), "USER",
                "VAPID_GENERATED", "credential", cred.getId().toString(),
                null, Map.of("provider", "WEBPUSH"), null, null
        );

        return CredentialDto.GenerateVapidResponse.builder()
                .publicKey(keyPair.getPublicKey())
                .build();
    }

    @Transactional
    public void deleteCredential(UUID appId, ProviderType providerType) {
        App app = getAppOrThrow(appId);
        credentialRepository.deleteByAppIdAndProviderType(appId, providerType);

        auditLogService.record(
                app.getOrganization().getId(), appId, TenantContextHolder.getUserId(), "USER",
                "CREDENTIAL_DELETED", "credential", providerType.name(),
                Map.of("provider", providerType.name()), null, null, null
        );
    }

    private App getAppOrThrow(UUID appId) {
        return appRepository.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("App not found"));
    }

    private ProviderCredential upsertCredential(App app, ProviderType type, String encryptedData, Map<String, Object> metadata) {
        ProviderCredential cred = credentialRepository.findByAppIdAndProviderType(app.getId(), type)
                .orElseGet(() -> ProviderCredential.builder()
                        .app(app)
                        .providerType(type)
                        .active(true)
                        .build());

        cred.setCredentialDataEncrypted(encryptedData);
        cred.setMetadataMasked(metadata);
        cred.setActive(true);
        return credentialRepository.save(cred);
    }

    private CredentialDto.CredentialResponse toResponse(ProviderCredential cred) {
        return CredentialDto.CredentialResponse.builder()
                .id(cred.getId())
                .providerType(cred.getProviderType())
                .active(cred.isActive())
                .metadataMasked(cred.getMetadataMasked())
                .updatedAt(cred.getUpdatedAt())
                .build();
    }
}
