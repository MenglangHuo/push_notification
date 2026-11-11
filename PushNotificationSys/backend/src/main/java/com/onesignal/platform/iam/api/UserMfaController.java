package com.onesignal.platform.iam.api;

import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.iam.api.dto.AuthDto;
import com.onesignal.platform.iam.application.UserMfaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/api/v1/auth/mfa")
@RequiredArgsConstructor
public class UserMfaController {

    private final UserMfaService userMfaService;

    @GetMapping("/status")
    public ResponseEntity<AuthDto.MfaStatusResponse> getMfaStatus() {
        UUID userId = TenantContextHolder.getUserId();
        return ResponseEntity.ok(userMfaService.getMfaStatus(userId));
    }

    @PostMapping("/setup")
    public ResponseEntity<AuthDto.MfaSetupResponse> setupMfa() {
        UUID userId = TenantContextHolder.getUserId();
        return ResponseEntity.ok(userMfaService.setupMfa(userId));
    }

    @PostMapping("/confirm")
    public ResponseEntity<AuthDto.MfaStatusResponse> confirmMfa(
            @Valid @RequestBody AuthDto.MfaConfirmRequest request) {
        UUID userId = TenantContextHolder.getUserId();
        return ResponseEntity.ok(userMfaService.confirmMfa(userId, request));
    }

    @PostMapping("/disable")
    public ResponseEntity<Void> disableMfa(
            @Valid @RequestBody AuthDto.MfaDisableRequest request) {
        UUID userId = TenantContextHolder.getUserId();
        userMfaService.disableMfa(userId, request);
        return ResponseEntity.noContent().build();
    }
}
