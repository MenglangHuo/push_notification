package com.onesignal.platform.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class AnalyticsDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalyticsOverviewResponse {
        private long totalSent;
        private long totalDelivered;
        private long totalOpened;
        private long totalFailed;
        private double deliveryRate; // percentage
        private double openRate; // percentage
        private double avgLatencyMs;
        private long activeDevices;
        private Map<String, Long> platformBreakdown;
        private List<ProviderStat> providerBreakdown;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderStat {
        private String provider;
        private long sentCount;
        private long failedCount;
        private double avgLatencyMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesPoint {
        private String timestamp;
        private long sent;
        private long delivered;
        private long opened;
        private long failed;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesResponse {
        private List<TimeSeriesPoint> points;
    }
}
