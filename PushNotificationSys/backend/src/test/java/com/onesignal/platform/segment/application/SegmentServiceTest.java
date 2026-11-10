package com.onesignal.platform.segment.application;

import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.infrastructure.AppRepository;
import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.device.domain.Device;
import com.onesignal.platform.device.domain.DevicePlatform;
import com.onesignal.platform.device.infrastructure.DeviceRepository;
import com.onesignal.platform.device.infrastructure.DeviceTagRepository;
import com.onesignal.platform.iam.domain.Organization;
import com.onesignal.platform.segment.api.dto.SegmentDto;
import com.onesignal.platform.segment.domain.Segment;
import com.onesignal.platform.segment.infrastructure.SegmentRepository;
import com.onesignal.platform.segment.infrastructure.SegmentSpecificationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SegmentServiceTest {

    @Mock
    private SegmentRepository segmentRepository;
    @Mock
    private AppRepository appRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private DeviceTagRepository deviceTagRepository;
    @Mock
    private SegmentSpecificationBuilder specificationBuilder;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private SegmentService segmentService;

    private UUID appId;
    private UUID userId;
    private App app;

    @BeforeEach
    void setUp() {
        appId = UUID.randomUUID();
        userId = UUID.randomUUID();
        Organization org = Organization.builder().name("Test Org").build();
        org.setId(UUID.randomUUID());
        app = App.builder().organization(org).name("Test App").build();
        app.setId(appId);
    }

    @Test
    @DisplayName("Should create segment and record audit log")
    void testCreateSegment() {
        when(appRepository.findById(appId)).thenReturn(Optional.of(app));
        when(segmentRepository.existsByAppIdAndName(eq(appId), anyString())).thenReturn(false);

        Segment saved = Segment.builder()
                .app(app)
                .name("VIP Users")
                .description("High spending customers")
                .filterAst(Map.of("conjunction", "AND"))
                .active(true)
                .build();
        saved.setId(UUID.randomUUID());

        when(segmentRepository.save(any(Segment.class))).thenReturn(saved);
        when(specificationBuilder.buildSpecification(eq(appId), any())).thenReturn((root, q, cb) -> null);
        when(deviceRepository.count(any(Specification.class))).thenReturn(42L);

        SegmentDto.CreateSegmentRequest request = SegmentDto.CreateSegmentRequest.builder()
                .name("VIP Users")
                .description("High spending customers")
                .filterAst(Map.of("conjunction", "AND"))
                .build();

        SegmentDto.SegmentResponse response = segmentService.createSegment(appId, userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("VIP Users");
        assertThat(response.getEstimatedCount()).isEqualTo(42L);

        verify(auditLogService).record(any(), eq(appId), eq(userId), eq("USER"), eq("SEGMENT_CREATED"), eq("segment"), any(), isNull(), any(), isNull(), isNull());
    }

    @Test
    @DisplayName("Should prevent creating segment with duplicate name")
    void testDuplicateNameThrowsException() {
        when(appRepository.findById(appId)).thenReturn(Optional.of(app));
        when(segmentRepository.existsByAppIdAndName(appId, "Existing Segment")).thenReturn(true);

        SegmentDto.CreateSegmentRequest req = SegmentDto.CreateSegmentRequest.builder()
                .name("Existing Segment")
                .build();

        assertThatThrownBy(() -> segmentService.createSegment(appId, userId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should accurately estimate audience breakdown across platforms")
    void testEstimateAudience() {
        Device d1 = Device.builder().id(UUID.randomUUID()).platform(DevicePlatform.ANDROID).build();
        Device d2 = Device.builder().id(UUID.randomUUID()).platform(DevicePlatform.ANDROID).build();
        Device d3 = Device.builder().id(UUID.randomUUID()).platform(DevicePlatform.IOS).build();
        Device d4 = Device.builder().id(UUID.randomUUID()).platform(DevicePlatform.WEB).build();

        when(specificationBuilder.buildSpecification(eq(appId), any())).thenReturn((root, q, cb) -> null);
        when(deviceRepository.findAll(any(Specification.class))).thenReturn(List.of(d1, d2, d3, d4));

        SegmentDto.AudienceEstimateResponse est = segmentService.estimateAudience(appId, Map.of("conjunction", "AND"));

        assertThat(est.getTotalCount()).isEqualTo(4L);
        assertThat(est.getAndroidCount()).isEqualTo(2L);
        assertThat(est.getIosCount()).isEqualTo(1L);
        assertThat(est.getWebCount()).isEqualTo(1L);
    }
}
