package com.onesignal.platform.device.api;

import com.onesignal.platform.device.api.dto.DeviceDto;
import com.onesignal.platform.device.application.DeviceRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin/api/v1/apps/{appId}/devices")
@RequiredArgsConstructor
public class DeviceAdminController {

    private final DeviceRegistrationService deviceRegistrationService;

    @PostMapping
    @PreAuthorize("hasPermission(#appId, 'app', 'device:create')")
    public ResponseEntity<DeviceDto.DeviceResponse> registerDevice(
            @PathVariable UUID appId,
            @jakarta.validation.Valid @RequestBody DeviceDto.RegisterDeviceRequest request
    ) {
        return ResponseEntity.ok(deviceRegistrationService.registerDevice(appId, request));
    }

    @GetMapping
    @PreAuthorize("hasPermission(#appId, 'app', 'device:read')")
    public ResponseEntity<Page<DeviceDto.DeviceResponse>> listDevices(
            @PathVariable UUID appId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("lastActiveAt").descending());
        return ResponseEntity.ok(deviceRegistrationService.listDevices(appId, active, pageRequest));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission(#appId, 'app', 'device:read')")
    public ResponseEntity<DeviceDto.DeviceStatsResponse> getStats(@PathVariable UUID appId) {
        return ResponseEntity.ok(deviceRegistrationService.getStats(appId));
    }

    @DeleteMapping("/{deviceId}")
    @PreAuthorize("hasPermission(#appId, 'app', 'device:delete')")
    public ResponseEntity<Void> deleteDevice(
            @PathVariable UUID appId,
            @PathVariable UUID deviceId
    ) {
        deviceRegistrationService.deleteDevice(appId, deviceId);
        return ResponseEntity.noContent().build();
    }
}
