package com.onesignal.platform.analytics.application;

import com.onesignal.platform.analytics.api.dto.AnalyticsDto;
import com.onesignal.platform.analytics.api.dto.DashboardDto;
import com.onesignal.platform.device.infrastructure.DeviceRepository;
import com.onesignal.platform.event.domain.ClientEventType;
import com.onesignal.platform.event.infrastructure.ClientEventRepository;
import com.onesignal.platform.notification.domain.Notification;
import com.onesignal.platform.notification.infrastructure.DeliveryLogRepository;
import com.onesignal.platform.notification.infrastructure.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DeliveryLogRepository deliveryLogRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private ClientEventRepository clientEventRepository;

    // Direct synchronous executor for testing
    private final Executor directExecutor = Runnable::run;

    private DashboardService dashboardService;

    private UUID appId;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID();
        dashboardService = new DashboardService(
                analyticsService,
                notificationRepository,
                deliveryLogRepository,
                deviceRepository,
                clientEventRepository,
                directExecutor
        );
    }

    @Test
    @DisplayName("getDashboardSummary aggregates all dashboard sections successfully")
    void testGetDashboardSummarySuccess() {
        // Mock overview
        AnalyticsDto.AnalyticsOverviewResponse mockOverview = AnalyticsDto.AnalyticsOverviewResponse.builder()
                .totalSent(500)
                .totalDelivered(450)
                .deliveryRate(90.0)
                .build();
        when(analyticsService.getOverview(appId, "7d")).thenReturn(mockOverview);

        // Mock time series
        AnalyticsDto.TimeSeriesResponse mockTimeSeries = AnalyticsDto.TimeSeriesResponse.builder()
                .points(Collections.emptyList())
                .build();
        when(analyticsService.getTimeSeries(appId, "7d")).thenReturn(mockTimeSeries);

        // Mock funnel
        when(clientEventRepository.countByAppIdAndEventTypeAfter(eq(appId), eq(ClientEventType.DELIVERED), any(Instant.class)))
                .thenReturn(400L);
        when(clientEventRepository.countByAppIdAndEventTypeAfter(eq(appId), eq(ClientEventType.OPENED), any(Instant.class)))
                .thenReturn(200L);
        when(clientEventRepository.countByAppIdAndEventTypeAfter(eq(appId), eq(ClientEventType.CLICKED), any(Instant.class)))
                .thenReturn(80L);

        Notification n1 = Notification.builder()
                .title("Campaign 1")
                .sentCount(500)
                .deliveredCount(450)
                .openedCount(220)
                .build();
        n1.setId(UUID.randomUUID());
        n1.setCreatedAt(Instant.now());

        when(notificationRepository.findByAppIdOrderByCreatedAtDesc(eq(appId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(n1)));

        // Mock top campaigns
        when(notificationRepository.findTopCampaignsByAppId(eq(appId), any(Pageable.class)))
                .thenReturn(List.of(n1));

        // Mock growth
        List<Object[]> newDevices = new ArrayList<>();
        newDevices.add(new Object[]{"2026-11-10", 15L});
        List<Object[]> churnedDevices = new ArrayList<>();
        churnedDevices.add(new Object[]{"2026-11-10", 3L});
        when(deviceRepository.countNewDevicesPerDay(eq(appId), any(Instant.class))).thenReturn(newDevices);
        when(deviceRepository.countChurnedDevicesPerDay(eq(appId), any(Instant.class))).thenReturn(churnedDevices);

        // Mock failures
        List<Object[]> failureAggregates = new ArrayList<>();
        failureAggregates.add(new Object[]{"DeviceUnregistered", 20L});
        failureAggregates.add(new Object[]{"QuotaExceeded", 5L});
        when(deliveryLogRepository.findFailureReasonAggregates(eq(appId), any(Instant.class)))
                .thenReturn(failureAggregates);

        // Mock heatmap
        List<Object[]> heatmapRows = new ArrayList<>();
        heatmapRows.add(new Object[]{1, 14, 50L}); // Monday (1 in PG -> 0 in Mon..Sun), 14:00
        when(deliveryLogRepository.findHourlyDayOfWeekAggregates(eq(appId), any(Instant.class)))
                .thenReturn(heatmapRows);

        // Mock provider aggregates for period comparison
        List<Object[]> currentLogs = new ArrayList<>();
        currentLogs.add(new Object[]{"FCM", "SUCCESS", 200L, 50.0});
        List<Object[]> totalLogs = new ArrayList<>();
        totalLogs.add(new Object[]{"FCM", "SUCCESS", 350L, 55.0});
        when(deliveryLogRepository.findProviderAggregatesAfter(eq(appId), any(Instant.class)))
                .thenReturn(currentLogs)
                .thenReturn(totalLogs);

        DashboardDto.DashboardSummaryResponse response = dashboardService.getDashboardSummary(appId, "7d");

        assertThat(response).isNotNull();
        assertThat(response.getOverview()).isEqualTo(mockOverview);
        assertThat(response.getTimeSeries()).isEqualTo(mockTimeSeries);

        // Funnel verification
        assertThat(response.getFunnel()).isNotNull();
        assertThat(response.getFunnel().getSent()).isEqualTo(500L);
        assertThat(response.getFunnel().getDelivered()).isEqualTo(450L);
        assertThat(response.getFunnel().getOpened()).isEqualTo(220L);
        assertThat(response.getFunnel().getClicked()).isEqualTo(80L);

        // Top campaigns verification
        assertThat(response.getTopCampaigns()).hasSize(1);
        DashboardDto.CampaignRanking campaign = response.getTopCampaigns().get(0);
        assertThat(campaign.getTitle()).isEqualTo("Campaign 1");
        assertThat(campaign.getDeliveryRate()).isEqualTo(90.0);
        assertThat(campaign.getOpenRate()).isEqualTo(48.9);

        // Growth verification
        assertThat(response.getGrowth()).isNotNull();
        assertThat(response.getGrowth().getTotalNew()).isEqualTo(15L);
        assertThat(response.getGrowth().getTotalChurned()).isEqualTo(3L);
        assertThat(response.getGrowth().getNetGrowth()).isEqualTo(12L);

        // Failure breakdown verification
        assertThat(response.getFailureBreakdown()).hasSize(2);
        assertThat(response.getFailureBreakdown().get(0).getReason()).isEqualTo("DeviceUnregistered");
        assertThat(response.getFailureBreakdown().get(0).getCount()).isEqualTo(20L);
        assertThat(response.getFailureBreakdown().get(0).getPercentage()).isEqualTo(80.0);

        // Heatmap verification (7 * 24 = 168 cells)
        assertThat(response.getHeatmap()).hasSize(168);

        // Period comparison verification
        assertThat(response.getPeriodComparison()).isNotNull();
    }

    @Test
    @DisplayName("getDashboardSummary handles exceptions gracefully with defaults")
    void testGetDashboardSummaryGracefulFailure() {
        when(analyticsService.getOverview(eq(appId), any())).thenThrow(new RuntimeException("DB down"));
        when(analyticsService.getTimeSeries(eq(appId), any())).thenThrow(new RuntimeException("DB down"));
        when(clientEventRepository.countByAppIdAndEventTypeAfter(any(), any(), any())).thenThrow(new RuntimeException("DB down"));
        when(notificationRepository.findByAppIdOrderByCreatedAtDesc(any(), any())).thenThrow(new RuntimeException("DB down"));
        when(notificationRepository.findTopCampaignsByAppId(any(), any())).thenThrow(new RuntimeException("DB down"));
        when(deviceRepository.countNewDevicesPerDay(any(), any())).thenThrow(new RuntimeException("DB down"));
        when(deliveryLogRepository.findFailureReasonAggregates(any(), any())).thenThrow(new RuntimeException("DB down"));
        when(deliveryLogRepository.findHourlyDayOfWeekAggregates(any(), any())).thenThrow(new RuntimeException("DB down"));
        when(deliveryLogRepository.findProviderAggregatesAfter(any(), any())).thenThrow(new RuntimeException("DB down"));

        DashboardDto.DashboardSummaryResponse response = dashboardService.getDashboardSummary(appId, "7d");

        assertThat(response).isNotNull();
        assertThat(response.getOverview()).isNotNull();
        assertThat(response.getTimeSeries().getPoints()).isEmpty();
        assertThat(response.getFunnel().getSent()).isEqualTo(0L);
        assertThat(response.getTopCampaigns()).isEmpty();
        assertThat(response.getGrowth().getPoints()).isEmpty();
        assertThat(response.getFailureBreakdown()).isEmpty();
        assertThat(response.getHeatmap()).isEmpty();
        assertThat(response.getPeriodComparison().getSentChange()).isEqualTo(0.0);
    }
}
