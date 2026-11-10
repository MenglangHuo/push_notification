package com.onesignal.platform.iam.application;

import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.common.exception.AppException;
import com.onesignal.platform.common.exception.ConflictException;
import com.onesignal.platform.common.exception.ResourceNotFoundException;
import com.onesignal.platform.common.security.Argon2PasswordService;
import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.iam.api.dto.MemberDto;
import com.onesignal.platform.iam.domain.*;
import com.onesignal.platform.iam.infrastructure.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final MemberRoleAssignmentRepository roleAssignmentRepository;
    private final Argon2PasswordService passwordEncoder;
    private final PermissionCacheService permissionCacheService;
    private final AuditLogService auditLogService;

    private static final Duration INVITATION_EXPIRATION = Duration.ofDays(3);
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public MemberDto.InvitationResponse createInvitation(UUID orgId, MemberDto.InviteMemberRequest request, UUID invitedByUserId) {
        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));

        User invitedBy = userRepository.findById(invitedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Inviting user not found"));

        String email = request.getEmail().toLowerCase().trim();

        // Check if user is already a member
        userRepository.findByEmail(email).ifPresent(u -> {
            if (membershipRepository.findByUserIdAndOrganizationId(u.getId(), orgId).isPresent()) {
                throw new ConflictException("User with email " + email + " is already a member of this organization");
            }
        });

        // Generate token and hash
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String tokenHash = hashToken(rawToken);

        Invitation invitation = Invitation.builder()
                .organization(org)
                .email(email)
                .role(role)
                .appId(request.getAppId())
                .tokenHash(tokenHash)
                .invitedBy(invitedBy)
                .expiresAt(Instant.now().plus(INVITATION_EXPIRATION))
                .build();

        Invitation saved = invitationRepository.save(invitation);

        auditLogService.record(
                orgId, request.getAppId(), invitedByUserId, "USER",
                "INVITATION_CREATED", "invitation", saved.getId().toString(),
                null, Map.of("email", email, "role", role.getName()), null, null
        );

        log.info("Invitation issued for {} to org {}. Raw accept token: {}", email, org.getName(), rawToken);

        return MemberDto.InvitationResponse.builder()
                .id(saved.getId())
                .email(saved.getEmail())
                .roleId(role.getId())
                .roleName(role.getName())
                .appId(saved.getAppId())
                .expiresAt(saved.getExpiresAt())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional
    public void acceptInvitation(MemberDto.AcceptInviteRequest request) {
        String tokenHash = hashToken(request.getToken().trim());

        Invitation invitation = invitationRepository.findByTokenHashWithDetails(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or unrecognized invitation token"));

        if (invitation.isExpired()) {
            throw new AppException("This invitation has expired", HttpStatus.GONE);
        }

        if (invitation.isAccepted()) {
            throw new ConflictException("This invitation has already been accepted");
        }

        String email = invitation.getEmail().toLowerCase().trim();
        Organization org = invitation.getOrganization();

        // Find or create User
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .email(email)
                    .fullName(request.getFullName().trim())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .superAdmin(false)
                    .status("ACTIVE")
                    .build();
            return userRepository.save(newUser);
        });

        // Ensure user is active
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            user.setStatus("ACTIVE");
            userRepository.save(user);
        }

        // Create Membership
        Membership membership = membershipRepository.findByUserIdAndOrganizationId(user.getId(), org.getId())
                .orElseGet(() -> {
                    Membership m = Membership.builder()
                            .user(user)
                            .organization(org)
                            .status("ACTIVE")
                            .build();
                    return membershipRepository.save(m);
                });

        // Assign Role
        MemberRoleAssignment assignment = MemberRoleAssignment.builder()
                .membership(membership)
                .role(invitation.getRole())
                .appId(invitation.getAppId())
                .build();
        roleAssignmentRepository.save(assignment);
        membership.getRoleAssignments().add(assignment);

        // Mark accepted
        invitation.setAcceptedAt(Instant.now());
        invitationRepository.save(invitation);

        // Evict permission cache
        permissionCacheService.evictUserPermissions(user.getId(), org.getId());

        auditLogService.record(
                org.getId(), invitation.getAppId(), user.getId(), "USER",
                "INVITATION_ACCEPTED", "invitation", invitation.getId().toString(),
                null, Map.of("email", email, "role", invitation.getRole().getName()), null, null
        );
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    }
}
