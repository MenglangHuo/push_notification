package com.onesignal.platform.analytics.api;

import com.onesignal.platform.analytics.api.dto.DashboardDto;
import com.onesignal.platform.analytics.application.AnalyticsService;
import com.onesignal.platform.analytics.application.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsAdminControllerTest {

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private AnalyticsAdminController controller;

    @Test
    @DisplayName("getDashboardSummary returns response from DashboardService")
    void testGetDashboardSummary() {
        UUID appId = UUID.randomUUID();
        String range = "14d";
        DashboardDto.DashboardSummaryResponse mockResponse = DashboardDto.DashboardSummaryResponse.builder()
                .funnel(DashboardDto.FunnelData.builder().sent(100).build())
                .build();

        when(dashboardService.getDashboardSummary(appId, range)).thenReturn(mockResponse);

        ResponseEntity<DashboardDto.DashboardSummaryResponse> result = controller.getDashboardSummary(appId, range);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(mockResponse);
        verify(dashboardService).getDashboardSummary(appId, range);
    }
}
