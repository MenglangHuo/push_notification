package com.onesignal.platform.segment.application;

import com.onesignal.platform.app.domain.App;
import com.onesignal.platform.app.infrastructure.AppRepository;
import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.common.exception.ResourceNotFoundException;
import com.onesignal.platform.device.domain.Device;
import com.onesignal.platform.device.domain.DevicePlatform;
import com.onesignal.platform.device.infrastructure.DeviceRepository;
import com.onesignal.platform.device.infrastructure.DeviceTagRepository;
import com.onesignal.platform.segment.api.dto.SegmentDto;
import com.onesignal.platform.segment.domain.Segment;
import com.onesignal.platform.segment.infrastructure.SegmentRepository;
import com.onesignal.platform.segment.infrastructure.SegmentSpecificationBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SegmentService {

    private final SegmentRepository segmentRepository;
    private final AppRepository appRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceTagRepository deviceTagRepository;
    private final SegmentSpecificationBuilder specificationBuilder;
    private final AuditLogService auditLogService;

    @Transactional
    public SegmentDto.SegmentResponse createSegment(
            UUID appId,
            UUID userId,
            SegmentDto.CreateSegmentRequest request
    ) {
        App app = appRepository.findById(appId)
                .orElseThrow(() -> new ResourceNotFoundException("App not found: " + appId));

        if (segmentRepository.existsByAppIdAndName(appId, request.getName().trim())) {
            throw new IllegalArgumentException("Segment with name '" + request.getName() + "' already exists in this app");
        }

        Map<String, Object> ast = request.getFilterAst() != null
                ? request.getFilterAst()
                : Map.of("conjunction", "AND", "rules", List.of());

        Segment segment = Segment.builder()
                .app(app)
                .name(request.getName().trim())
                .description(request.getDescription())
                .filterAst(ast)
                .active(true)
                .createdBy(userId)
                .build();

        Segment saved = segmentRepository.save(segment);

        auditLogService.record(
                app.getOrganization().getId(),
                appId,
                userId,
                "USER",
                "SEGMENT_CREATED",
                "segment",
                saved.getId().toString(),
                null,
                Map.of("name", saved.getName()),
                null,
                null
        );

        log.info("Created segment {} for app {}", saved.getId(), appId);
        return toResponse(saved, countAudience(appId, saved.getFilterAst()));
    }

    @Transactional
    public SegmentDto.SegmentResponse updateSegment(
            UUID appId,
            UUID segmentId,
            UUID userId,
            SegmentDto.UpdateSegmentRequest request
    ) {
        Segment segment = segmentRepository.findByIdAndAppId(segmentId, appId)
                .orElseThrow(() -> new ResourceNotFoundException("Segment not found: " + segmentId));

        if (!segment.getName().equalsIgnoreCase(request.getName().trim())
                && segmentRepository.existsByAppIdAndName(appId, request.getName().trim())) {
            throw new IllegalArgumentException("Segment with name '" + request.getName() + "' already exists in this app");
        }

        segment.setName(request.getName().trim());
        segment.setDescription(request.getDescription());
        if (request.getFilterAst() != null) {
            segment.setFilterAst(request.getFilterAst());
        }
        if (request.getActive() != null) {
            segment.setActive(request.getActive());
        }

        Segment updated = segmentRepository.save(segment);

        auditLogService.record(
                updated.getApp().getOrganization().getId(),
                appId,
                userId,
                "USER",
                "SEGMENT_UPDATED",
                "segment",
                updated.getId().toString(),
                null,
                Map.of("name", updated.getName()),
                null,
                null
        );

        log.info("Updated segment {} for app {}", updated.getId(), appId);
        return toResponse(updated, countAudience(appId, updated.getFilterAst()));
    }

    @Transactional
    public void deleteSegment(UUID appId, UUID segmentId, UUID userId) {
        Segment segment = segmentRepository.findByIdAndAppId(segmentId, appId)
                .orElseThrow(() -> new ResourceNotFoundException("Segment not found: " + segmentId));

        segmentRepository.delete(segment);

        auditLogService.record(
                segment.getApp().getOrganization().getId(),
                appId,
                userId,
                "USER",
                "SEGMENT_DELETED",
                "segment",
                segmentId.toString(),
                Map.of("name", segment.getName()),
                null,
                null,
                null
        );

        log.info("Deleted segment {} from app {}", segmentId, appId);
    }

    @Transactional(readOnly = true)
    public SegmentDto.SegmentResponse getSegment(UUID appId, UUID segmentId) {
        Segment segment = segmentRepository.findByIdAndAppId(segmentId, appId)
                .orElseThrow(() -> new ResourceNotFoundException("Segment not found: " + segmentId));
        return toResponse(segment, countAudience(appId, segment.getFilterAst()));
    }

    @Transactional(readOnly = true)
    public List<SegmentDto.SegmentResponse> listSegments(UUID appId) {
        List<Segment> segments = segmentRepository.findByAppIdOrderByNameAsc(appId);
        return segments.stream()
                .map(s -> toResponse(s, countAudience(appId, s.getFilterAst())))
                .toList();
    }

    @Transactional(readOnly = true)
    public SegmentDto.AudienceEstimateResponse estimateAudience(UUID appId, Map<String, Object> filterAst) {
        Specification<Device> spec = specificationBuilder.buildSpecification(appId, filterAst);
        List<Device> matching = deviceRepository.findAll(spec);

        long total = matching.size();
        long android = matching.stream().filter(d -> d.getPlatform() == DevicePlatform.ANDROID).count();
        long ios = matching.stream().filter(d -> d.getPlatform() == DevicePlatform.IOS).count();
        long web = matching.stream().filter(d -> d.getPlatform() == DevicePlatform.WEB).count();

        return SegmentDto.AudienceEstimateResponse.builder()
                .totalCount(total)
                .androidCount(android)
                .iosCount(ios)
                .webCount(web)
                .build();
    }

    @Transactional(readOnly = true)
    public List<String> getAvailableTagKeys(UUID appId) {
        return deviceTagRepository.findDistinctTagKeysByAppId(appId);
    }

    @Transactional(readOnly = true)
    public List<Device> resolveDevicesForSegment(UUID appId, UUID segmentId) {
        Segment segment = segmentRepository.findByIdAndAppId(segmentId, appId)
                .orElseThrow(() -> new ResourceNotFoundException("Segment not found: " + segmentId));
        Specification<Device> spec = specificationBuilder.buildSpecification(appId, segment.getFilterAst());
        return deviceRepository.findAll(spec);
    }

    private long countAudience(UUID appId, Map<String, Object> filterAst) {
        try {
            Specification<Device> spec = specificationBuilder.buildSpecification(appId, filterAst);
            return deviceRepository.count(spec);
        } catch (Exception e) {
            log.warn("Error estimating audience count for app {}: {}", appId, e.getMessage());
            return 0L;
        }
    }

    private SegmentDto.SegmentResponse toResponse(Segment segment, long estimatedCount) {
        return SegmentDto.SegmentResponse.builder()
                .id(segment.getId())
                .appId(segment.getApp().getId())
                .name(segment.getName())
                .description(segment.getDescription())
                .filterAst(segment.getFilterAst())
                .active(segment.isActive())
                .estimatedCount(estimatedCount)
                .createdAt(segment.getCreatedAt())
                .updatedAt(segment.getUpdatedAt())
                .build();
    }
}
