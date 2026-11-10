package com.onesignal.platform.iam.application;

import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.common.exception.AppException;
import com.onesignal.platform.common.exception.ResourceNotFoundException;
import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.iam.api.dto.MemberDto;
import com.onesignal.platform.iam.domain.MemberRoleAssignment;
import com.onesignal.platform.iam.domain.Membership;
import com.onesignal.platform.iam.domain.Role;
import com.onesignal.platform.iam.infrastructure.MemberRoleAssignmentRepository;
import com.onesignal.platform.iam.infrastructure.MembershipRepository;
import com.onesignal.platform.iam.infrastructure.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MembershipService {

    private final MembershipRepository membershipRepository;
    private final MemberRoleAssignmentRepository roleAssignmentRepository;
    private final RoleRepository roleRepository;
    private final PermissionCacheService permissionCacheService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<MemberDto.MemberResponse> listMembers(UUID orgId) {
        return membershipRepository.findAllByOrganizationIdWithRoles(orgId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public MemberDto.MemberResponse assignRole(UUID orgId, UUID membershipId, MemberDto.AssignRoleRequest request) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));

        if (!membership.getOrganization().getId().equals(orgId)) {
            throw new AppException("Membership does not belong to this organization", HttpStatus.BAD_REQUEST);
        }

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        // Check if role is available for this org
        if (role.getOrganization() != null && !role.getOrganization().getId().equals(orgId)) {
            throw new AppException("Role is not available for this organization", HttpStatus.BAD_REQUEST);
        }

        MemberRoleAssignment assignment = MemberRoleAssignment.builder()
                .membership(membership)
                .role(role)
                .appId(request.getAppId())
                .build();

        roleAssignmentRepository.save(assignment);
        membership.getRoleAssignments().add(assignment);

        // Invalidate Redis permissions cache for this user
        permissionCacheService.evictUserPermissions(membership.getUser().getId(), orgId);

        auditLogService.record(
                orgId, request.getAppId(), TenantContextHolder.getUserId(), "USER",
                "ROLE_ASSIGNED", "membership", membershipId.toString(),
                null, Map.of("role", role.getName(), "appId", request.getAppId() != null ? request.getAppId().toString() : "ORG_WIDE"),
                null, null
        );

        return toResponse(membership);
    }

    @Transactional
    public void removeRoleAssignment(UUID orgId, UUID membershipId, UUID assignmentId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));

        if (!membership.getOrganization().getId().equals(orgId)) {
            throw new AppException("Membership does not belong to this organization", HttpStatus.BAD_REQUEST);
        }

        MemberRoleAssignment assignment = roleAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Role assignment not found"));

        roleAssignmentRepository.delete(assignment);
        membership.getRoleAssignments().removeIf(a -> a.getId().equals(assignmentId));

        permissionCacheService.evictUserPermissions(membership.getUser().getId(), orgId);

        auditLogService.record(
                orgId, assignment.getAppId(), TenantContextHolder.getUserId(), "USER",
                "ROLE_REVOKED", "membership", membershipId.toString(),
                Map.of("role", assignment.getRole().getName()), null, null, null
        );
    }

    @Transactional
    public void removeMember(UUID orgId, UUID membershipId) {
        Membership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));

        if (!membership.getOrganization().getId().equals(orgId)) {
            throw new AppException("Membership does not belong to this organization", HttpStatus.BAD_REQUEST);
        }

        UUID userId = membership.getUser().getId();
        membershipRepository.delete(membership);
        permissionCacheService.evictUserPermissions(userId, orgId);

        auditLogService.record(
                orgId, null, TenantContextHolder.getUserId(), "USER",
                "MEMBER_REMOVED", "membership", membershipId.toString(),
                Map.of("userId", userId.toString()), null, null, null
        );
    }

    private MemberDto.MemberResponse toResponse(Membership m) {
        List<MemberDto.RoleAssignmentInfo> roleInfos = m.getRoleAssignments().stream()
                .map(ra -> MemberDto.RoleAssignmentInfo.builder()
                        .assignmentId(ra.getId())
                        .roleId(ra.getRole().getId())
                        .roleName(ra.getRole().getName())
                        .appId(ra.getAppId())
                        .build())
                .collect(Collectors.toList());

        return MemberDto.MemberResponse.builder()
                .membershipId(m.getId())
                .userId(m.getUser().getId())
                .email(m.getUser().getEmail())
                .fullName(m.getUser().getFullName())
                .status(m.getStatus())
                .roleAssignments(roleInfos)
                .build();
    }
}
