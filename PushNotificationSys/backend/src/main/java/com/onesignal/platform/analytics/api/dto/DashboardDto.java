package com.onesignal.platform.analytics.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

public class DashboardDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardSummaryResponse {
        private AnalyticsDto.AnalyticsOverviewResponse overview;
        private AnalyticsDto.TimeSeriesResponse timeSeries;
        private FunnelData funnel;
        private List<CampaignRanking> topCampaigns;
        private GrowthData growth;
        private List<FailureCategory> failureBreakdown;
        private List<HeatmapCell> heatmap;
        private PeriodComparison periodComparison;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunnelData {
        private long sent;
        private long delivered;
        private long opened;
        private long clicked;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignRanking {
        private UUID notificationId;
        private String title;
        private long sentCount;
        private long deliveredCount;
        private long openedCount;
        private double deliveryRate;
        private double openRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrowthData {
        private List<GrowthPoint> points;
        private long totalNew;
        private long totalChurned;
        private long netGrowth;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrowthPoint {
        private String date;
        private long newDevices;
        private long churnedDevices;
        private long netChange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureCategory {
        private String reason;
        private long count;
        private double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeatmapCell {
        private int dayOfWeek; // (0=Mon..6=Sun)
        private int hour; // (0-23)
        private long value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodComparison {
        private double sentChange;
        private double deliveredChange;
        private double openRateChange;
        private double latencyChange;
    }
}
