package com.onesignal.platform.realtime.api;

import com.onesignal.platform.realtime.api.dto.PresenceDto;
import com.onesignal.platform.realtime.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin/api/v1/apps/{appId}/presence")
@RequiredArgsConstructor
public class PresenceAdminController {

    private final PresenceService presenceService;

    @GetMapping("/stats")
    @PreAuthorize("hasPermission(#appId, 'app', 'device:read')")
    public ResponseEntity<PresenceDto.PresenceStatsResponse> getPresenceStats(@PathVariable UUID appId) {
        long onlineCount = presenceService.getOnlineCount(appId);
        long localSessionCount = presenceService.getLocalSessionCount(appId);

        return ResponseEntity.ok(PresenceDto.PresenceStatsResponse.builder()
                .appId(appId)
                .onlineCount(onlineCount)
                .localSessionCount(localSessionCount)
                .build());
    }
}
