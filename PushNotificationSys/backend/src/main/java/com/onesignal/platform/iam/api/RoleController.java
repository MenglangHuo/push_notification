package com.onesignal.platform.iam.api;

import com.onesignal.platform.iam.api.dto.RoleDto;
import com.onesignal.platform.iam.application.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/admin/api/v1/organizations/{orgId}/roles")
    @PreAuthorize("hasPermission(#orgId, 'org', 'role:read')")
    public ResponseEntity<List<RoleDto.RoleResponse>> listRoles(@PathVariable UUID orgId) {
        return ResponseEntity.ok(roleService.listRolesForOrg(orgId));
    }

    @GetMapping("/admin/api/v1/permissions")
    public ResponseEntity<List<RoleDto.PermissionResponse>> listPermissions() {
        return ResponseEntity.ok(roleService.listAllPermissions());
    }

    @PostMapping("/admin/api/v1/organizations/{orgId}/roles")
    @PreAuthorize("hasPermission(#orgId, 'org', 'role:create')")
    public ResponseEntity<RoleDto.RoleResponse> createCustomRole(
            @PathVariable UUID orgId,
            @Valid @RequestBody RoleDto.CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(roleService.createCustomRole(orgId, request));
    }

    @PutMapping("/admin/api/v1/organizations/{orgId}/roles/{roleId}")
    @PreAuthorize("hasPermission(#orgId, 'org', 'role:update')")
    public ResponseEntity<RoleDto.RoleResponse> updateCustomRole(
            @PathVariable UUID orgId,
            @PathVariable UUID roleId,
            @Valid @RequestBody RoleDto.UpdateRoleRequest request) {
        return ResponseEntity.ok(roleService.updateCustomRole(orgId, roleId, request));
    }
}
