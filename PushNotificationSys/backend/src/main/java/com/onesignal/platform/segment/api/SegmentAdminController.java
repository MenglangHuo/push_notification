package com.onesignal.platform.segment.api;

import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.segment.api.dto.SegmentDto;
import com.onesignal.platform.segment.application.SegmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/api/v1/apps/{appId}/segments")
@RequiredArgsConstructor
public class SegmentAdminController {

    private final SegmentService segmentService;

    @GetMapping
    @PreAuthorize("hasPermission(#appId, 'segment:read')")
    public ResponseEntity<List<SegmentDto.SegmentResponse>> listSegments(@PathVariable UUID appId) {
        return ResponseEntity.ok(segmentService.listSegments(appId));
    }

    @PostMapping
    @PreAuthorize("hasPermission(#appId, 'segment:create')")
    public ResponseEntity<SegmentDto.SegmentResponse> createSegment(
            @PathVariable UUID appId,
            @Valid @RequestBody SegmentDto.CreateSegmentRequest request
    ) {
        UUID userId = TenantContextHolder.getUserId();
        SegmentDto.SegmentResponse response = segmentService.createSegment(appId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{segmentId}")
    @PreAuthorize("hasPermission(#appId, 'segment:read')")
    public ResponseEntity<SegmentDto.SegmentResponse> getSegment(
            @PathVariable UUID appId,
            @PathVariable UUID segmentId
    ) {
        return ResponseEntity.ok(segmentService.getSegment(appId, segmentId));
    }

    @PutMapping("/{segmentId}")
    @PreAuthorize("hasPermission(#appId, 'segment:update')")
    public ResponseEntity<SegmentDto.SegmentResponse> updateSegment(
            @PathVariable UUID appId,
            @PathVariable UUID segmentId,
            @Valid @RequestBody SegmentDto.UpdateSegmentRequest request
    ) {
        UUID userId = TenantContextHolder.getUserId();
        return ResponseEntity.ok(segmentService.updateSegment(appId, segmentId, userId, request));
    }

    @DeleteMapping("/{segmentId}")
    @PreAuthorize("hasPermission(#appId, 'segment:delete')")
    public ResponseEntity<Void> deleteSegment(
            @PathVariable UUID appId,
            @PathVariable UUID segmentId
    ) {
        UUID userId = TenantContextHolder.getUserId();
        segmentService.deleteSegment(appId, segmentId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/estimate")
    @PreAuthorize("hasPermission(#appId, 'segment:read')")
    public ResponseEntity<SegmentDto.AudienceEstimateResponse> estimateAudience(
            @PathVariable UUID appId,
            @RequestBody SegmentDto.AudienceEstimateRequest request
    ) {
        return ResponseEntity.ok(segmentService.estimateAudience(appId, request.getFilterAst()));
    }

    @GetMapping("/tag-keys")
    @PreAuthorize("hasPermission(#appId, 'segment:read')")
    public ResponseEntity<List<String>> getTagKeys(@PathVariable UUID appId) {
        return ResponseEntity.ok(segmentService.getAvailableTagKeys(appId));
    }
}
