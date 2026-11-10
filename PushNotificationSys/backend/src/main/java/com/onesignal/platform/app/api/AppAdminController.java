package com.onesignal.platform.app.api;

import com.onesignal.platform.app.api.dto.AppDto;
import com.onesignal.platform.app.application.AppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AppAdminController {

    private final AppService appService;

    @GetMapping("/admin/api/v1/organizations/{orgId}/apps")
    @PreAuthorize("hasPermission(#orgId, 'org', 'app:read')")
    public ResponseEntity<List<AppDto.AppResponse>> listApps(@PathVariable UUID orgId) {
        return ResponseEntity.ok(appService.listAppsForOrg(orgId));
    }

    @GetMapping("/admin/api/v1/apps/{appId}")
    @PreAuthorize("hasPermission(#appId, 'app', 'app:read')")
    public ResponseEntity<AppDto.AppResponse> getApp(@PathVariable UUID appId) {
        return ResponseEntity.ok(appService.getApp(appId));
    }

    @PostMapping("/admin/api/v1/organizations/{orgId}/apps")
    @PreAuthorize("hasPermission(#orgId, 'org', 'app:create')")
    public ResponseEntity<AppDto.AppResponse> createApp(
            @PathVariable UUID orgId,
            @Valid @RequestBody AppDto.CreateAppRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appService.createApp(orgId, request));
    }

    @PutMapping("/admin/api/v1/apps/{appId}")
    @PreAuthorize("hasPermission(#appId, 'app', 'app:update')")
    public ResponseEntity<AppDto.AppResponse> updateApp(
            @PathVariable UUID appId,
            @Valid @RequestBody AppDto.UpdateAppRequest request) {
        return ResponseEntity.ok(appService.updateApp(appId, request));
    }

    @DeleteMapping("/admin/api/v1/apps/{appId}")
    @PreAuthorize("hasPermission(#appId, 'app', 'app:delete')")
    public ResponseEntity<Void> deleteApp(@PathVariable UUID appId) {
        appService.deleteApp(appId);
        return ResponseEntity.noContent().build();
    }
}
