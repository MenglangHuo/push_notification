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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DeliveryLogRepository deliveryLogRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private ClientEventRepository clientEventRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private UUID appId;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getOverview calculates delivery rates, open rates, and provider breakdown correctly")
    void testGetOverview() {
        when(deviceRepository.countByAppIdAndActiveTrue(appId)).thenReturn(100L);
        when(deviceRepository.countByAppIdAndPlatformAndActiveTrue(appId, DevicePlatform.ANDROID)).thenReturn(60L);
        when(deviceRepository.countByAppIdAndPlatformAndActiveTrue(appId, DevicePlatform.IOS)).thenReturn(30L);
        when(deviceRepository.countByAppIdAndPlatformAndActiveTrue(appId, DevicePlatform.WEB)).thenReturn(10L);

        List<Object[]> providerAggregates = List.of(
                new Object[]{"FCM", "SUCCESS", 50L, 80.0},
                new Object[]{"FCM", "FAILED", 5L, 0.0},
                new Object[]{"APNS", "SUCCESS", 25L, 65.0}
        );
        when(deliveryLogRepository.findProviderAggregates(appId)).thenReturn(providerAggregates);

        Notification n = Notification.builder()
                .sentCount(100)
                .deliveredCount(90)
                .openedCount(45)
                .failedCount(10)
                .build();
        n.setId(UUID.randomUUID());

        when(notificationRepository.findByAppIdOrderByCreatedAtDesc(eq(appId), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(n)));
        when(clientEventRepository.countByAppIdAndEventType(appId, ClientEventType.DELIVERED)).thenReturn(90L);
        when(clientEventRepository.countByAppIdAndEventType(appId, ClientEventType.OPENED)).thenReturn(45L);

        AnalyticsDto.AnalyticsOverviewResponse overview = analyticsService.getOverview(appId, "7d");

        assertThat(overview).isNotNull();
        assertThat(overview.getActiveDevices()).isEqualTo(100L);
        assertThat(overview.getPlatformBreakdown().get("ANDROID")).isEqualTo(60L);
        assertThat(overview.getTotalSent()).isEqualTo(100L);
        assertThat(overview.getTotalDelivered()).isEqualTo(90L);
        assertThat(overview.getTotalOpened()).isEqualTo(45L);
        assertThat(overview.getDeliveryRate()).isEqualTo(90.0);
        assertThat(overview.getOpenRate()).isEqualTo(50.0);
        assertThat(overview.getProviderBreakdown()).hasSize(2);
    }

    @Test
    @DisplayName("getTimeSeries returns bucketed points for specified range")
    void testGetTimeSeries() {
        DeliveryLog log1 = DeliveryLog.builder()
                .appId(appId)
                .notificationId(UUID.randomUUID())
                .provider("FCM")
                .status("SUCCESS")
                .latencyMs(75)
                .createdAt(Instant.now())
                .build();
        log1.setId(UUID.randomUUID());

        when(deliveryLogRepository.findByAppIdAndCreatedAtAfterOrderByCreatedAtAsc(eq(appId), any(Instant.class)))
                .thenReturn(List.of(log1));
        when(clientEventRepository.findRecentEvents(eq(appId), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        AnalyticsDto.TimeSeriesResponse response = analyticsService.getTimeSeries(appId, "7d");

        assertThat(response).isNotNull();
        assertThat(response.getPoints()).isNotEmpty();
        assertThat(response.getPoints()).hasSize(7);
    }
}
