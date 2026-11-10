package com.onesignal.platform.device.api;

import com.onesignal.platform.common.exception.UnauthorizedException;
import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.device.api.dto.DeviceDto;
import com.onesignal.platform.device.application.DeviceRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/sdk/v1")
@RequiredArgsConstructor
public class DeviceSdkController {

    private final DeviceRegistrationService deviceRegistrationService;

    @PostMapping({"/devices", "/devices/register"})
    public ResponseEntity<DeviceDto.DeviceResponse> registerDevice(
            @Valid @RequestBody DeviceDto.RegisterDeviceRequest request
    ) {
        UUID appId = resolveAppId();
        log.info("SDK client registering device on platform {} for app {}", request.getPlatform(), appId);
        DeviceDto.DeviceResponse response = deviceRegistrationService.registerDevice(appId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/vapid-key", "/devices/vapid-key"})
    public ResponseEntity<Map<String, String>> getVapidPublicKey() {
        UUID appId = resolveAppId();
        String publicKey = deviceRegistrationService.getVapidPublicKey(appId);
        if (publicKey == null || publicKey.isBlank()) {
            return ResponseEntity.ok(Map.of("publicKey", ""));
        }
        return ResponseEntity.ok(Map.of("publicKey", publicKey));
    }

    private UUID resolveAppId() {
        UUID appId = TenantContextHolder.getAppId();
        if (appId == null) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UUID uId) {
                appId = uId;
            }
        }
        if (appId == null) {
            throw new UnauthorizedException("Invalid or missing SDK client credentials");
        }
        return appId;
    }
}
