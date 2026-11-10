package com.onesignal.platform.iam.application;

import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.common.exception.ConflictException;
import com.onesignal.platform.common.exception.ResourceNotFoundException;
import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.iam.api.dto.OrgDto;
import com.onesignal.platform.iam.domain.Membership;
import com.onesignal.platform.iam.domain.Organization;
import com.onesignal.platform.iam.domain.Role;
import com.onesignal.platform.iam.domain.User;
import com.onesignal.platform.iam.infrastructure.MembershipRepository;
import com.onesignal.platform.iam.infrastructure.OrganizationRepository;
import com.onesignal.platform.iam.infrastructure.RoleRepository;
import com.onesignal.platform.iam.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<OrgDto.OrgResponse> listUserOrganizations(UUID userId) {
        if (TenantContextHolder.isSuperAdmin()) {
            return organizationRepository.findAll().stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        }

        return membershipRepository.findByUserId(userId).stream()
                .map(m -> toResponse(m.getOrganization()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OrgDto.OrgResponse getOrganization(UUID orgId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        return toResponse(org);
    }

    @Transactional
    public OrgDto.OrgResponse createOrganization(OrgDto.CreateOrgRequest request, UUID creatorUserId) {
        if (organizationRepository.existsBySlug(request.getSlug().toLowerCase())) {
            throw new ConflictException("Organization slug is already taken");
        }

        Organization org = Organization.builder()
                .name(request.getName().trim())
                .slug(request.getSlug().toLowerCase().trim())
                .status("ACTIVE")
                .tier("FREE")
                .quotas(Map.of("max_apps", 5, "max_devices", 100000, "rate_limit_per_min", 1000))
                .build();

        Organization saved = organizationRepository.save(org);

        // Add creator as Owner
        User creator = userRepository.findById(creatorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator user not found"));

        Membership membership = Membership.builder()
                .user(creator)
                .organization(saved)
                .status("ACTIVE")
                .build();
        membershipRepository.save(membership);

        Role ownerRole = roleRepository.findByNameAndOrganizationIdIsNull("Owner")
                .orElseThrow(() -> new ResourceNotFoundException("System Owner role not found"));

        // Assign Owner role
        var mra = com.onesignal.platform.iam.domain.MemberRoleAssignment.builder()
                .membership(membership)
                .role(ownerRole)
                .appId(null)
                .build();
        membership.getRoleAssignments().add(mra);
        membershipRepository.save(membership);

        auditLogService.record(
                saved.getId(), null, creatorUserId, "USER",
                "ORG_CREATED", "organization", saved.getId().toString(),
                null, Map.of("name", saved.getName(), "slug", saved.getSlug()), null, null
        );

        return toResponse(saved);
    }

    @Transactional
    public OrgDto.OrgResponse updateOrganization(UUID orgId, OrgDto.UpdateOrgRequest request) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        Map<String, Object> before = Map.of("name", org.getName(), "tier", org.getTier());

        org.setName(request.getName().trim());
        if (request.getTier() != null) {
            org.setTier(request.getTier());
        }
        if (request.getQuotas() != null) {
            org.setQuotas(request.getQuotas());
        }

        Organization updated = organizationRepository.save(org);

        auditLogService.record(
                org.getId(), null, TenantContextHolder.getUserId(), "USER",
                "ORG_UPDATED", "organization", org.getId().toString(),
                before, Map.of("name", updated.getName(), "tier", updated.getTier()), null, null
        );

        return toResponse(updated);
    }

    private OrgDto.OrgResponse toResponse(Organization org) {
        return OrgDto.OrgResponse.builder()
                .id(org.getId())
                .name(org.getName())
                .slug(org.getSlug())
                .status(org.getStatus())
                .tier(org.getTier())
                .quotas(org.getQuotas())
                .createdAt(org.getCreatedAt())
                .build();
    }
}
