package com.onesignal.platform.apikey.api;

import com.onesignal.platform.apikey.api.dto.ApiKeyDto;
import com.onesignal.platform.apikey.application.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/api/v1/apps/{appId}/api-keys")
@RequiredArgsConstructor
public class ApiKeyAdminController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    @PreAuthorize("hasPermission(#appId, 'app', 'apikey:read')")
    public ResponseEntity<List<ApiKeyDto.ApiKeySummaryResponse>> listKeys(@PathVariable UUID appId) {
        return ResponseEntity.ok(apiKeyService.listKeysForApp(appId));
    }

    @PostMapping
    @PreAuthorize("hasPermission(#appId, 'app', 'apikey:create')")
    public ResponseEntity<ApiKeyDto.ApiKeyCreatedResponse> createKey(
            @PathVariable UUID appId,
            @Valid @RequestBody ApiKeyDto.CreateApiKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiKeyService.createKey(appId, request));
    }

    @PostMapping("/{keyId}/rotate")
    @PreAuthorize("hasPermission(#appId, 'app', 'apikey:rotate')")
    public ResponseEntity<ApiKeyDto.RotateApiKeyResponse> rotateKey(
            @PathVariable UUID appId,
            @PathVariable UUID keyId) {
        return ResponseEntity.ok(apiKeyService.rotateKey(appId, keyId));
    }

    @DeleteMapping("/{keyId}")
    @PreAuthorize("hasPermission(#appId, 'app', 'apikey:revoke')")
    public ResponseEntity<Void> revokeKey(
            @PathVariable UUID appId,
            @PathVariable UUID keyId) {
        apiKeyService.revokeKey(appId, keyId);
        return ResponseEntity.noContent().build();
    }
}
