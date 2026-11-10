package com.onesignal.platform.iam.api;

import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.iam.api.dto.MemberDto;
import com.onesignal.platform.iam.application.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping("/admin/api/v1/organizations/{orgId}/invitations")
    @PreAuthorize("hasPermission(#orgId, 'org', 'member:invite')")
    public ResponseEntity<MemberDto.InvitationResponse> createInvitation(
            @PathVariable UUID orgId,
            @Valid @RequestBody MemberDto.InviteMemberRequest request) {
        UUID actorId = TenantContextHolder.getUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invitationService.createInvitation(orgId, request, actorId));
    }

    @PostMapping("/admin/api/v1/invitations/accept")
    public ResponseEntity<Void> acceptInvitation(
            @Valid @RequestBody MemberDto.AcceptInviteRequest request) {
        invitationService.acceptInvitation(request);
        return ResponseEntity.ok().build();
    }
}
