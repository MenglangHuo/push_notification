package com.onesignal.platform.iam.api;

import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.iam.api.dto.OrgDto;
import com.onesignal.platform.iam.application.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping
    public ResponseEntity<List<OrgDto.OrgResponse>> listOrganizations() {
        UUID userId = TenantContextHolder.getUserId();
        return ResponseEntity.ok(organizationService.listUserOrganizations(userId));
    }

    @GetMapping("/{orgId}")
    @PreAuthorize("hasPermission(#orgId, 'org', 'org:read')")
    public ResponseEntity<OrgDto.OrgResponse> getOrganization(@PathVariable UUID orgId) {
        return ResponseEntity.ok(organizationService.getOrganization(orgId));
    }

    @PostMapping
    public ResponseEntity<OrgDto.OrgResponse> createOrganization(
            @Valid @RequestBody OrgDto.CreateOrgRequest request) {
        UUID userId = TenantContextHolder.getUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.createOrganization(request, userId));
    }

    @PutMapping("/{orgId}")
    @PreAuthorize("hasPermission(#orgId, 'org', 'org:update')")
    public ResponseEntity<OrgDto.OrgResponse> updateOrganization(
            @PathVariable UUID orgId,
            @Valid @RequestBody OrgDto.UpdateOrgRequest request) {
        return ResponseEntity.ok(organizationService.updateOrganization(orgId, request));
    }
}
