package com.onesignal.platform.app.api;

import com.onesignal.platform.app.api.dto.CredentialDto;
import com.onesignal.platform.app.application.CredentialService;
import com.onesignal.platform.app.domain.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/api/v1/apps/{appId}/credentials")
@RequiredArgsConstructor
public class CredentialAdminController {

    private final CredentialService credentialService;

    @GetMapping
    @PreAuthorize("hasPermission(#appId, 'app', 'credential:read')")
    public ResponseEntity<List<CredentialDto.CredentialResponse>> listCredentials(@PathVariable UUID appId) {
        return ResponseEntity.ok(credentialService.listCredentials(appId));
    }

    @PostMapping("/fcm")
    @PreAuthorize("hasPermission(#appId, 'app', 'credential:update')")
    public ResponseEntity<CredentialDto.CredentialResponse> uploadFcmCredential(
            @PathVariable UUID appId,
            @RequestBody String serviceAccountJson) {
        return ResponseEntity.ok(credentialService.saveFcmCredential(appId, serviceAccountJson));
    }

    @PostMapping("/apns")
    @PreAuthorize("hasPermission(#appId, 'app', 'credential:update')")
    public ResponseEntity<CredentialDto.CredentialResponse> saveApnsCredential(
            @PathVariable UUID appId,
            @RequestBody CredentialDto.SaveCredentialRequest request) {
        return ResponseEntity.ok(credentialService.saveApnsCredential(
                appId, request.getRawCredential(), request.getKeyId(), request.getTeamId(), request.getBundleId()));
    }

    @PostMapping("/vapid/generate")
    @PreAuthorize("hasPermission(#appId, 'app', 'credential:update')")
    public ResponseEntity<CredentialDto.GenerateVapidResponse> generateVapid(@PathVariable UUID appId) {
        return ResponseEntity.ok(credentialService.generateVapid(appId));
    }

    @DeleteMapping("/{provider}")
    @PreAuthorize("hasPermission(#appId, 'app', 'credential:delete')")
    public ResponseEntity<Void> deleteCredential(
            @PathVariable UUID appId,
            @PathVariable String provider) {
        ProviderType type = ProviderType.valueOf(provider.toUpperCase());
        credentialService.deleteCredential(appId, type);
        return ResponseEntity.noContent().build();
    }
}
