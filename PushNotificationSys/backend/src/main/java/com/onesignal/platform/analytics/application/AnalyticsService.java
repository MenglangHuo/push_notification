package com.onesignal.platform.analytics.application;

import com.onesignal.platform.analytics.api.dto.AnalyticsDto;
import com.onesignal.platform.device.domain.DevicePlatform;
import com.onesignal.platform.device.infrastructure.DeviceRepository;
import com.onesignal.platform.event.domain.ClientEventType;
import com.onesignal.platform.event.infrastructure.ClientEventRepository;
import com.onesignal.platform.notification.domain.DeliveryLog;
import com.onesignal.platform.notification.domain.Notification;
import com.onesignal.platform.notification.infrastructure.DeliveryLogRepository;
import com.onesignal.platform.notification.infrastructure.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final NotificationRepository notificationRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final DeviceRepository deviceRepository;
    private final ClientEventRepository clientEventRepository;

    @Transactional(readOnly = true)
    public AnalyticsDto.AnalyticsOverviewResponse getOverview(UUID appId, String range) {
        long activeDevices = deviceRepository.countByAppIdAndActiveTrue(appId);

        // Platform breakdown
        Map<String, Long> platformBreakdown = new LinkedHashMap<>();
        platformBreakdown.put("ANDROID", deviceRepository.countByAppIdAndPlatformAndActiveTrue(appId, DevicePlatform.ANDROID));
        platformBreakdown.put("IOS", deviceRepository.countByAppIdAndPlatformAndActiveTrue(appId, DevicePlatform.IOS));
        platformBreakdown.put("WEB", deviceRepository.countByAppIdAndPlatformAndActiveTrue(appId, DevicePlatform.WEB));

        // Delivery log provider stats
        List<Object[]> providerRows = deliveryLogRepository.findProviderAggregates(appId);
        Map<String, ProviderAccumulator> accumMap = new HashMap<>();
        for (Object[] row : providerRows) {
            String provider = (String) row[0];
            String status = (String) row[1];
            long count = ((Number) row[2]).longValue();
            double avgLat = ((Number) row[3]).doubleValue();

            ProviderAccumulator acc = accumMap.computeIfAbsent(provider, k -> new ProviderAccumulator(provider));
            if ("SUCCESS".equalsIgnoreCase(status)) {
                acc.sent += count;
                acc.totalLatency += (avgLat * count);
                acc.latencyCount += count;
            } else if ("FAILED".equalsIgnoreCase(status)) {
                acc.failed += count;
            }
        }

        List<AnalyticsDto.ProviderStat> providerStats = new ArrayList<>();
        double totalLatencyWeighted = 0;
        long totalLatencyCount = 0;

        for (ProviderAccumulator acc : accumMap.values()) {
            double avg = acc.latencyCount > 0 ? (acc.totalLatency / acc.latencyCount) : 0.0;
            providerStats.add(AnalyticsDto.ProviderStat.builder()
                    .provider(acc.provider)
                    .sentCount(acc.sent)
                    .failedCount(acc.failed)
                    .avgLatencyMs(Math.round(avg * 10.0) / 10.0)
                    .build());
            totalLatencyWeighted += acc.totalLatency;
            totalLatencyCount += acc.latencyCount;
        }

        double overallAvgLatency = totalLatencyCount > 0 ? (totalLatencyWeighted / totalLatencyCount) : 120.0;

        // Sum notification delivery numbers
        long totalSent = 0;
        long totalDelivered = 0;
        long totalOpened = 0;
        long totalFailed = 0;

        List<Notification> notifs = notificationRepository.findByAppIdOrderByCreatedAtDesc(appId, org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
        for (Notification n : notifs) {
            totalSent += n.getSentCount();
            totalDelivered += n.getDeliveredCount();
            totalOpened += n.getOpenedCount();
            totalFailed += n.getFailedCount();
        }

        // Include events count if higher
        long eventDelivered = clientEventRepository.countByAppIdAndEventType(appId, ClientEventType.DELIVERED);
        long eventOpened = clientEventRepository.countByAppIdAndEventType(appId, ClientEventType.OPENED);
        totalDelivered = Math.max(totalDelivered, eventDelivered);
        totalOpened = Math.max(totalOpened, eventOpened);

        double deliveryRate = totalSent > 0 ? ((double) totalDelivered / totalSent * 100.0) : 100.0;
        double openRate = totalDelivered > 0 ? ((double) totalOpened / totalDelivered * 100.0) : 0.0;

        return AnalyticsDto.AnalyticsOverviewResponse.builder()
                .totalSent(totalSent)
                .totalDelivered(totalDelivered)
                .totalOpened(totalOpened)
                .totalFailed(totalFailed)
                .deliveryRate(Math.min(100.0, Math.round(deliveryRate * 10.0) / 10.0))
                .openRate(Math.min(100.0, Math.round(openRate * 10.0) / 10.0))
                .avgLatencyMs(Math.round(overallAvgLatency * 10.0) / 10.0)
                .activeDevices(activeDevices)
                .platformBreakdown(platformBreakdown)
                .providerBreakdown(providerStats)
                .build();
    }

    @Transactional(readOnly = true)
    public AnalyticsDto.TimeSeriesResponse getTimeSeries(UUID appId, String range) {
        int days = "30d".equalsIgnoreCase(range) ? 30 : "24h".equalsIgnoreCase(range) ? 1 : 7;
        Instant since = Instant.now().minus(Duration.ofDays(days));

        List<DeliveryLog> logs = deliveryLogRepository.findByAppIdAndCreatedAtAfterOrderByCreatedAtAsc(appId, since);
        List<com.onesignal.platform.event.domain.ClientEvent> events = clientEventRepository.findRecentEvents(appId, since);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Map<String, Bucket> buckets = new LinkedHashMap<>();

        // Pre-populate consecutive days
        LocalDate start = LocalDate.now().minusDays(days - 1);
        for (int i = 0; i < days; i++) {
            String key = start.plusDays(i).format(formatter);
            buckets.put(key, new Bucket(key));
        }

        // Aggregate delivery logs
        for (DeliveryLog log : logs) {
            String dateKey = log.getCreatedAt().atZone(ZoneId.of("UTC")).format(formatter);
            Bucket b = buckets.get(dateKey);
            if (b != null) {
                if ("SUCCESS".equalsIgnoreCase(log.getStatus())) {
                    b.sent++;
                    b.delivered++;
                } else if ("FAILED".equalsIgnoreCase(log.getStatus())) {
                    b.failed++;
                }
            }
        }

        // Aggregate client events
        for (var ev : events) {
            String dateKey = ev.getCreatedAt().atZone(ZoneId.of("UTC")).format(formatter);
            Bucket b = buckets.get(dateKey);
            if (b != null) {
                if (ev.getEventType() == ClientEventType.OPENED) {
                    b.opened++;
                }
            }
        }

        List<AnalyticsDto.TimeSeriesPoint> points = buckets.values().stream()
                .map(b -> AnalyticsDto.TimeSeriesPoint.builder()
                        .timestamp(b.date)
                        .sent(b.sent)
                        .delivered(b.delivered)
                        .opened(b.opened)
                        .failed(b.failed)
                        .build())
                .toList();

        return AnalyticsDto.TimeSeriesResponse.builder().points(points).build();
    }

    private static class ProviderAccumulator {
        final String provider;
        long sent = 0;
        long failed = 0;
        double totalLatency = 0;
        long latencyCount = 0;

        ProviderAccumulator(String provider) {
            this.provider = provider;
        }
    }

    private static class Bucket {
        final String date;
        long sent = 0;
        long delivered = 0;
        long opened = 0;
        long failed = 0;

        Bucket(String date) {
            this.date = date;
        }
    }
}
