package com.onesignal.platform.iam.application;

import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.common.exception.AppException;
import com.onesignal.platform.common.exception.ConflictException;
import com.onesignal.platform.common.exception.ResourceNotFoundException;
import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.iam.api.dto.RoleDto;
import com.onesignal.platform.iam.domain.Organization;
import com.onesignal.platform.iam.domain.Permission;
import com.onesignal.platform.iam.domain.Role;
import com.onesignal.platform.iam.infrastructure.OrganizationRepository;
import com.onesignal.platform.iam.infrastructure.PermissionRepository;
import com.onesignal.platform.iam.infrastructure.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<RoleDto.RoleResponse> listRolesForOrg(UUID orgId) {
        return roleRepository.findAllAvailableForOrg(orgId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<RoleDto.PermissionResponse> listAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(p -> RoleDto.PermissionResponse.builder()
                        .id(p.getId())
                        .code(p.getCode())
                        .resource(p.getResource())
                        .action(p.getAction())
                        .description(p.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public RoleDto.RoleResponse createCustomRole(UUID orgId, RoleDto.CreateRoleRequest request) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        if (roleRepository.findByNameAndOrganizationId(request.getName().trim(), orgId).isPresent()) {
            throw new ConflictException("Role with name '" + request.getName() + "' already exists in this organization");
        }

        List<Permission> permissions = permissionRepository.findByCodeIn(request.getPermissionCodes());
        if (permissions.isEmpty()) {
            throw new AppException("Invalid or empty permissions provided", HttpStatus.BAD_REQUEST);
        }

        Role role = Role.builder()
                .organization(org)
                .name(request.getName().trim())
                .description(request.getDescription())
                .system(false)
                .permissions(new HashSet<>(permissions))
                .build();

        Role saved = roleRepository.save(role);

        auditLogService.record(
                orgId, null, TenantContextHolder.getUserId(), "USER",
                "ROLE_CREATED", "role", saved.getId().toString(),
                null, Map.of("name", saved.getName(), "permissions", request.getPermissionCodes()), null, null
        );

        return toResponse(saved);
    }

    @Transactional
    public RoleDto.RoleResponse updateCustomRole(UUID orgId, UUID roleId, RoleDto.UpdateRoleRequest request) {
        Role role = roleRepository.findByIdWithPermissions(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        if (role.isSystem()) {
            throw new AppException("System preset roles cannot be modified", HttpStatus.FORBIDDEN);
        }

        if (role.getOrganization() == null || !role.getOrganization().getId().equals(orgId)) {
            throw new AppException("Role does not belong to this organization", HttpStatus.FORBIDDEN);
        }

        List<Permission> permissions = permissionRepository.findByCodeIn(request.getPermissionCodes());
        role.setName(request.getName().trim());
        role.setDescription(request.getDescription());
        role.setPermissions(new HashSet<>(permissions));

        Role updated = roleRepository.save(role);

        auditLogService.record(
                orgId, null, TenantContextHolder.getUserId(), "USER",
                "ROLE_UPDATED", "role", updated.getId().toString(),
                null, Map.of("name", updated.getName(), "permissions", request.getPermissionCodes()), null, null
        );

        return toResponse(updated);
    }

    private RoleDto.RoleResponse toResponse(Role role) {
        List<String> permissionCodes = role.getPermissions().stream()
                .map(Permission::getCode)
                .sorted()
                .collect(Collectors.toList());

        return RoleDto.RoleResponse.builder()
                .id(role.getId())
                .organizationId(role.getOrganization() != null ? role.getOrganization().getId() : null)
                .name(role.getName())
                .description(role.getDescription())
                .system(role.isSystem())
                .permissions(permissionCodes)
                .build();
    }
}
