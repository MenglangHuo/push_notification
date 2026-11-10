package com.onesignal.platform.iam.api;

import com.onesignal.platform.iam.api.dto.MemberDto;
import com.onesignal.platform.iam.application.MembershipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/api/v1/organizations/{orgId}/members")
@RequiredArgsConstructor
public class MemberController {

    private final MembershipService membershipService;

    @GetMapping
    @PreAuthorize("hasPermission(#orgId, 'org', 'member:read')")
    public ResponseEntity<List<MemberDto.MemberResponse>> listMembers(@PathVariable UUID orgId) {
        return ResponseEntity.ok(membershipService.listMembers(orgId));
    }

    @PostMapping("/{membershipId}/roles")
    @PreAuthorize("hasPermission(#orgId, 'org', 'member:update')")
    public ResponseEntity<MemberDto.MemberResponse> assignRole(
            @PathVariable UUID orgId,
            @PathVariable UUID membershipId,
            @Valid @RequestBody MemberDto.AssignRoleRequest request) {
        return ResponseEntity.ok(membershipService.assignRole(orgId, membershipId, request));
    }

    @DeleteMapping("/{membershipId}/roles/{assignmentId}")
    @PreAuthorize("hasPermission(#orgId, 'org', 'member:update')")
    public ResponseEntity<Void> removeRole(
            @PathVariable UUID orgId,
            @PathVariable UUID membershipId,
            @PathVariable UUID assignmentId) {
        membershipService.removeRoleAssignment(orgId, membershipId, assignmentId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{membershipId}")
    @PreAuthorize("hasPermission(#orgId, 'org', 'member:remove')")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID orgId,
            @PathVariable UUID membershipId) {
        membershipService.removeMember(orgId, membershipId);
        return ResponseEntity.noContent().build();
    }
}
