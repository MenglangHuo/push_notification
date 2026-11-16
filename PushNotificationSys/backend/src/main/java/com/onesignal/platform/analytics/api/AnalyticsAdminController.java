package com.onesignal.platform.analytics.api;

import com.onesignal.platform.analytics.api.dto.AnalyticsDto;
import com.onesignal.platform.analytics.api.dto.DashboardDto;
import com.onesignal.platform.analytics.application.AnalyticsService;
import com.onesignal.platform.analytics.application.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin/api/v1/apps/{appId}/analytics")
@RequiredArgsConstructor
public class AnalyticsAdminController {

    private final AnalyticsService analyticsService;
    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasPermission(#appId, 'app', 'analytics:read')")
    public ResponseEntity<DashboardDto.DashboardSummaryResponse> getDashboardSummary(
            @PathVariable UUID appId,
            @RequestParam(defaultValue = "7d") String range
    ) {
        return ResponseEntity.ok(dashboardService.getDashboardSummary(appId, range));
    }

    @GetMapping("/overview")
    @PreAuthorize("hasPermission(#appId, 'app', 'analytics:read')")
    public ResponseEntity<AnalyticsDto.AnalyticsOverviewResponse> getOverview(
            @PathVariable UUID appId,
            @RequestParam(defaultValue = "7d") String range
    ) {
        return ResponseEntity.ok(analyticsService.getOverview(appId, range));
    }

    @GetMapping("/time-series")
    @PreAuthorize("hasPermission(#appId, 'app', 'analytics:read')")
    public ResponseEntity<AnalyticsDto.TimeSeriesResponse> getTimeSeries(
            @PathVariable UUID appId,
            @RequestParam(defaultValue = "7d") String range
    ) {
        return ResponseEntity.ok(analyticsService.getTimeSeries(appId, range));
    }
}
