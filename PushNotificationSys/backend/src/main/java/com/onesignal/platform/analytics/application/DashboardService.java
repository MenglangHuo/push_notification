package com.onesignal.platform.analytics.application;

import com.onesignal.platform.analytics.api.dto.AnalyticsDto;
import com.onesignal.platform.analytics.api.dto.DashboardDto;
import com.onesignal.platform.device.infrastructure.DeviceRepository;
import com.onesignal.platform.event.domain.ClientEventType;
import com.onesignal.platform.event.infrastructure.ClientEventRepository;
import com.onesignal.platform.notification.domain.Notification;
import com.onesignal.platform.notification.infrastructure.DeliveryLogRepository;
import com.onesignal.platform.notification.infrastructure.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AnalyticsService analyticsService;
    private final NotificationRepository notificationRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final DeviceRepository deviceRepository;
    private final ClientEventRepository clientEventRepository;

    @Qualifier("dashboardExecutor")
    private final Executor dashboardExecutor;

    @Transactional(readOnly = true)
    public DashboardDto.DashboardSummaryResponse getDashboardSummary(UUID appId, String range) {
        log.info("Fetching dashboard summary for appId: {}, range: {}", appId, range);

        CompletableFuture<AnalyticsDto.AnalyticsOverviewResponse> overviewFuture =
                CompletableFuture.supplyAsync(() -> computeOverview(appId, range), dashboardExecutor);

        CompletableFuture<AnalyticsDto.TimeSeriesResponse> timeSeriesFuture =
                CompletableFuture.supplyAsync(() -> computeTimeSeries(appId, range), dashboardExecutor);

        CompletableFuture<DashboardDto.FunnelData> funnelFuture =
                CompletableFuture.supplyAsync(() -> computeFunnel(appId, range), dashboardExecutor);

        CompletableFuture<List<DashboardDto.CampaignRanking>> topCampaignsFuture =
                CompletableFuture.supplyAsync(() -> computeTopCampaigns(appId), dashboardExecutor);

        CompletableFuture<DashboardDto.GrowthData> growthFuture =
                CompletableFuture.supplyAsync(() -> computeGrowth(appId, range), dashboardExecutor);

        CompletableFuture<List<DashboardDto.FailureCategory>> failureFuture =
                CompletableFuture.supplyAsync(() -> computeFailureBreakdown(appId, range), dashboardExecutor);

        CompletableFuture<List<DashboardDto.HeatmapCell>> heatmapFuture =
                CompletableFuture.supplyAsync(() -> computeHeatmap(appId, range), dashboardExecutor);

        CompletableFuture<DashboardDto.PeriodComparison> periodComparisonFuture =
                CompletableFuture.supplyAsync(() -> computePeriodComparison(appId, range), dashboardExecutor);

        CompletableFuture.allOf(
                overviewFuture,
                timeSeriesFuture,
                funnelFuture,
                topCampaignsFuture,
                growthFuture,
                failureFuture,
                heatmapFuture,
                periodComparisonFuture
        ).join();

        return DashboardDto.DashboardSummaryResponse.builder()
                .overview(overviewFuture.join())
                .timeSeries(timeSeriesFuture.join())
                .funnel(funnelFuture.join())
                .topCampaigns(topCampaignsFuture.join())
                .growth(growthFuture.join())
                .failureBreakdown(failureFuture.join())
                .heatmap(heatmapFuture.join())
                .periodComparison(periodComparisonFuture.join())
                .build();
    }

    private AnalyticsDto.AnalyticsOverviewResponse computeOverview(UUID appId, String range) {
        try {
            return analyticsService.getOverview(appId, range);
        } catch (Exception e) {
            log.error("Failed to compute overview for appId {}: {}", appId, e.getMessage(), e);
            return AnalyticsDto.AnalyticsOverviewResponse.builder()
                    .platformBreakdown(Collections.emptyMap())
                    .providerBreakdown(Collections.emptyList())
                    .build();
        }
    }

    private AnalyticsDto.TimeSeriesResponse computeTimeSeries(UUID appId, String range) {
        try {
            return analyticsService.getTimeSeries(appId, range);
        } catch (Exception e) {
            log.error("Failed to compute time series for appId {}: {}", appId, e.getMessage(), e);
            return AnalyticsDto.TimeSeriesResponse.builder()
                    .points(Collections.emptyList())
                    .build();
        }
    }

    private DashboardDto.FunnelData computeFunnel(UUID appId, String range) {
        try {
            Instant since = resolveRangeStart(range);
            long delivered = clientEventRepository.countByAppIdAndEventTypeAfter(appId, ClientEventType.DELIVERED, since);
            long opened = clientEventRepository.countByAppIdAndEventTypeAfter(appId, ClientEventType.OPENED, since);
            long clicked = clientEventRepository.countByAppIdAndEventTypeAfter(appId, ClientEventType.CLICKED, since);

            long notifSent = 0;
            long notifDelivered = 0;
            long notifOpened = 0;
            List<Notification> notifs = notificationRepository.findByAppIdOrderByCreatedAtDesc(appId, PageRequest.of(0, 1000)).getContent();
            for (Notification n : notifs) {
                if (n.getCreatedAt() != null && n.getCreatedAt().isAfter(since)) {
                    notifSent += n.getSentCount();
                    notifDelivered += n.getDeliveredCount();
                    notifOpened += n.getOpenedCount();
                }
            }

            long finalDelivered = Math.max(delivered, notifDelivered);
            long finalOpened = Math.max(opened, notifOpened);
            long finalSent = Math.max(notifSent, finalDelivered);

            return DashboardDto.FunnelData.builder()
                    .sent(finalSent)
                    .delivered(finalDelivered)
                    .opened(finalOpened)
                    .clicked(clicked)
                    .build();
        } catch (Exception e) {
            log.error("Failed to compute funnel for appId {}: {}", appId, e.getMessage(), e);
            return DashboardDto.FunnelData.builder()
                    .sent(0)
                    .delivered(0)
                    .opened(0)
                    .clicked(0)
                    .build();
        }
    }

    private List<DashboardDto.CampaignRanking> computeTopCampaigns(UUID appId) {
        try {
            List<Notification> campaigns = notificationRepository.findTopCampaignsByAppId(appId, PageRequest.of(0, 10));
            return campaigns.stream().map(n -> {
                long sent = n.getSentCount();
                long delivered = n.getDeliveredCount();
                long opened = n.getOpenedCount();
                double deliveryRate = sent > 0 ? ((double) delivered / sent * 100.0) : 0.0;
                double openRate = delivered > 0 ? ((double) opened / delivered * 100.0) : 0.0;

                return DashboardDto.CampaignRanking.builder()
                        .notificationId(n.getId())
                        .title(n.getTitle())
                        .sentCount(sent)
                        .deliveredCount(delivered)
                        .openedCount(opened)
                        .deliveryRate(Math.min(100.0, Math.round(deliveryRate * 10.0) / 10.0))
                        .openRate(Math.min(100.0, Math.round(openRate * 10.0) / 10.0))
                        .build();
            }).toList();
        } catch (Exception e) {
            log.error("Failed to compute top campaigns for appId {}: {}", appId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private DashboardDto.GrowthData computeGrowth(UUID appId, String range) {
        try {
            int days = parseRangeDays(range);
            Instant since = resolveRangeStart(range);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            Map<String, long[]> dailyCounts = new LinkedHashMap<>();
            LocalDate start = LocalDate.now(ZoneId.of("UTC")).minusDays(days - 1);
            for (int i = 0; i < days; i++) {
                String key = start.plusDays(i).format(formatter);
                dailyCounts.put(key, new long[]{0L, 0L});
            }

            List<Object[]> newRows = deviceRepository.countNewDevicesPerDay(appId, since);
            long totalNew = 0;
            for (Object[] row : newRows) {
                if (row[0] != null && row[1] != null) {
                    String dateKey = row[0].toString();
                    long count = ((Number) row[1]).longValue();
                    long[] counts = dailyCounts.computeIfAbsent(dateKey, k -> new long[]{0L, 0L});
                    counts[0] += count;
                    totalNew += count;
                }
            }

            List<Object[]> churnedRows = deviceRepository.countChurnedDevicesPerDay(appId, since);
            long totalChurned = 0;
            for (Object[] row : churnedRows) {
                if (row[0] != null && row[1] != null) {
                    String dateKey = row[0].toString();
                    long count = ((Number) row[1]).longValue();
                    long[] counts = dailyCounts.computeIfAbsent(dateKey, k -> new long[]{0L, 0L});
                    counts[1] += count;
                    totalChurned += count;
                }
            }

            List<DashboardDto.GrowthPoint> points = dailyCounts.entrySet().stream()
                    .map(entry -> {
                        long newDevs = entry.getValue()[0];
                        long churnedDevs = entry.getValue()[1];
                        return DashboardDto.GrowthPoint.builder()
                                .date(entry.getKey())
                                .newDevices(newDevs)
                                .churnedDevices(churnedDevs)
                                .netChange(newDevs - churnedDevs)
                                .build();
                    })
                    .toList();

            return DashboardDto.GrowthData.builder()
                    .points(points)
                    .totalNew(totalNew)
                    .totalChurned(totalChurned)
                    .netGrowth(totalNew - totalChurned)
                    .build();
        } catch (Exception e) {
            log.error("Failed to compute growth for appId {}: {}", appId, e.getMessage(), e);
            return DashboardDto.GrowthData.builder()
                    .points(Collections.emptyList())
                    .totalNew(0)
                    .totalChurned(0)
                    .netGrowth(0)
                    .build();
        }
    }

    private List<DashboardDto.FailureCategory> computeFailureBreakdown(UUID appId, String range) {
        try {
            Instant since = resolveRangeStart(range);
            List<Object[]> rows = deliveryLogRepository.findFailureReasonAggregates(appId, since);

            long totalFailures = 0;
            for (Object[] row : rows) {
                if (row[1] != null) {
                    totalFailures += ((Number) row[1]).longValue();
                }
            }

            List<DashboardDto.FailureCategory> categories = new ArrayList<>();
            for (Object[] row : rows) {
                String reason = row[0] != null ? row[0].toString() : "Unknown";
                long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
                double percentage = totalFailures > 0 ? ((double) count / totalFailures * 100.0) : 0.0;
                categories.add(DashboardDto.FailureCategory.builder()
                        .reason(reason)
                        .count(count)
                        .percentage(Math.round(percentage * 10.0) / 10.0)
                        .build());
            }
            return categories;
        } catch (Exception e) {
            log.error("Failed to compute failure breakdown for appId {}: {}", appId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<DashboardDto.HeatmapCell> computeHeatmap(UUID appId, String range) {
        try {
            Instant since = resolveRangeStart(range);
            List<Object[]> rows = deliveryLogRepository.findHourlyDayOfWeekAggregates(appId, since);

            // Pre-populate 7 days (0=Mon..6=Sun) x 24 hours (0-23)
            Map<String, DashboardDto.HeatmapCell> cellMap = new LinkedHashMap<>();
            for (int d = 0; d < 7; d++) {
                for (int h = 0; h < 24; h++) {
                    String key = d + "_" + h;
                    cellMap.put(key, DashboardDto.HeatmapCell.builder()
                            .dayOfWeek(d)
                            .hour(h)
                            .value(0L)
                            .build());
                }
            }

            for (Object[] row : rows) {
                if (row[0] != null && row[1] != null && row[2] != null) {
                    int pgDow = ((Number) row[0]).intValue();
                    // Postgres EXTRACT(DOW) is 0=Sun..6=Sat. Convert to 0=Mon..6=Sun
                    int dayOfWeek = (pgDow + 6) % 7;
                    int hour = ((Number) row[1]).intValue();
                    long count = ((Number) row[2]).longValue();
                    String key = dayOfWeek + "_" + hour;
                    DashboardDto.HeatmapCell cell = cellMap.get(key);
                    if (cell != null) {
                        cell.setValue(cell.getValue() + count);
                    } else {
                        cellMap.put(key, DashboardDto.HeatmapCell.builder()
                                .dayOfWeek(dayOfWeek)
                                .hour(hour)
                                .value(count)
                                .build());
                    }
                }
            }

            return new ArrayList<>(cellMap.values());
        } catch (Exception e) {
            log.error("Failed to compute heatmap for appId {}: {}", appId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private DashboardDto.PeriodComparison computePeriodComparison(UUID appId, String range) {
        try {
            Instant currentStart = resolveRangeStart(range);
            Instant prevStart = computePreviousRange(range);

            List<Object[]> currentLogs = deliveryLogRepository.findProviderAggregatesAfter(appId, currentStart);
            List<Object[]> totalLogs = deliveryLogRepository.findProviderAggregatesAfter(appId, prevStart);

            long currentSuccess = 0;
            long currentFailed = 0;
            double currentLatencySum = 0;
            for (Object[] row : currentLogs) {
                String status = (String) row[1];
                long count = ((Number) row[2]).longValue();
                double avgLat = ((Number) row[3]).doubleValue();
                if ("SUCCESS".equalsIgnoreCase(status)) {
                    currentSuccess += count;
                    currentLatencySum += (avgLat * count);
                } else if ("FAILED".equalsIgnoreCase(status)) {
                    currentFailed += count;
                }
            }
            long currentSent = currentSuccess + currentFailed;
            long currentDelivered = currentSuccess;
            double currentAvgLatency = currentSuccess > 0 ? (currentLatencySum / currentSuccess) : 0.0;

            long totalSuccess = 0;
            long totalFailed = 0;
            double totalLatencySum = 0;
            for (Object[] row : totalLogs) {
                String status = (String) row[1];
                long count = ((Number) row[2]).longValue();
                double avgLat = ((Number) row[3]).doubleValue();
                if ("SUCCESS".equalsIgnoreCase(status)) {
                    totalSuccess += count;
                    totalLatencySum += (avgLat * count);
                } else if ("FAILED".equalsIgnoreCase(status)) {
                    totalFailed += count;
                }
            }
            long prevSuccess = Math.max(0, totalSuccess - currentSuccess);
            long prevFailed = Math.max(0, totalFailed - currentFailed);
            long prevSent = prevSuccess + prevFailed;
            long prevDelivered = prevSuccess;
            double prevLatencySum = Math.max(0, totalLatencySum - currentLatencySum);
            double prevAvgLatency = prevSuccess > 0 ? (prevLatencySum / prevSuccess) : 0.0;

            long currentOpened = clientEventRepository.countByAppIdAndEventTypeAfter(appId, ClientEventType.OPENED, currentStart);
            long totalOpened = clientEventRepository.countByAppIdAndEventTypeAfter(appId, ClientEventType.OPENED, prevStart);
            long prevOpened = Math.max(0, totalOpened - currentOpened);

            double currentOpenRate = currentDelivered > 0 ? ((double) currentOpened / currentDelivered * 100.0) : 0.0;
            double prevOpenRate = prevDelivered > 0 ? ((double) prevOpened / prevDelivered * 100.0) : 0.0;

            double sentChange = computePercentageChange(currentSent, prevSent);
            double deliveredChange = computePercentageChange(currentDelivered, prevDelivered);
            double openRateChange = computePercentageChange(currentOpenRate, prevOpenRate);
            double latencyChange = computePercentageChange(currentAvgLatency, prevAvgLatency);

            return DashboardDto.PeriodComparison.builder()
                    .sentChange(sentChange)
                    .deliveredChange(deliveredChange)
                    .openRateChange(openRateChange)
                    .latencyChange(latencyChange)
                    .build();
        } catch (Exception e) {
            log.error("Failed to compute period comparison for appId {}: {}", appId, e.getMessage(), e);
            return DashboardDto.PeriodComparison.builder()
                    .sentChange(0.0)
                    .deliveredChange(0.0)
                    .openRateChange(0.0)
                    .latencyChange(0.0)
                    .build();
        }
    }

    private double computePercentageChange(double current, double previous) {
        if (previous == 0.0) {
            return current > 0.0 ? 100.0 : 0.0;
        }
        double change = ((current - previous) / previous) * 100.0;
        return Math.round(change * 10.0) / 10.0;
    }

    Instant resolveRangeStart(String range) {
        int days = parseRangeDays(range);
        return Instant.now().minus(Duration.ofDays(days));
    }

    Instant computePreviousRange(String range) {
        int days = parseRangeDays(range);
        return Instant.now().minus(Duration.ofDays(days * 2L));
    }

    private int parseRangeDays(String range) {
        if ("30d".equalsIgnoreCase(range)) {
            return 30;
        } else if ("24h".equalsIgnoreCase(range) || "1d".equalsIgnoreCase(range)) {
            return 1;
        }
        return 7;
    }
}
