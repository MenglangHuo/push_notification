package com.onesignal.platform.iam.application;

import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.common.exception.AppException;
import com.onesignal.platform.common.exception.UnauthorizedException;
import com.onesignal.platform.common.security.Argon2PasswordService;
import com.onesignal.platform.common.security.JwtTokenProvider;
import com.onesignal.platform.iam.api.dto.AuthDto;
import com.onesignal.platform.iam.domain.Membership;
import com.onesignal.platform.iam.domain.Organization;
import com.onesignal.platform.iam.domain.User;
import com.onesignal.platform.app.application.CryptoVaultService;
import com.onesignal.platform.common.security.TotpService;
import com.onesignal.platform.iam.domain.UserMfa;
import com.onesignal.platform.iam.infrastructure.MemberRoleAssignmentRepository;
import com.onesignal.platform.iam.infrastructure.UserMfaRepository;
import com.onesignal.platform.iam.infrastructure.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserMfaRepository userMfaRepository;
    private final MemberRoleAssignmentRepository roleAssignmentRepository;
    private final Argon2PasswordService passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TotpService totpService;
    private final CryptoVaultService cryptoVaultService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AuditLogService auditLogService;

    @Value("${app.jwt.cookie-name:push_refresh_token}")
    private String cookieName;

    @Value("${app.jwt.refresh-token-expiration-seconds:604800}")
    private long refreshTokenExpirationSeconds;

    @Value("${app.jwt.cookie-secure:false}")
    private boolean cookieSecure;

    private static final String REFRESH_KEY_PREFIX = "auth:refresh:";
    private static final String MFA_KEY_PREFIX = "auth:mfa:";

    @Transactional
    public AuthDto.LoginResponse login(AuthDto.LoginRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmailWithMemberships(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new AppException("User account is inactive or suspended", HttpStatus.FORBIDDEN);
        }

        // Check if MFA is enabled
        if (user.getMfa() != null && user.getMfa().isEnabled()) {
            String mfaTicket = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(MFA_KEY_PREFIX + mfaTicket, user.getId().toString(), Duration.ofMinutes(5));
            return AuthDto.LoginResponse.builder()
                    .mfaRequired(true)
                    .mfaTicket(mfaTicket)
                    .build();
        }

        return completeAuthentication(user, response);
    }

    @Transactional
    public AuthDto.LoginResponse verifyMfa(AuthDto.MfaVerifyRequest request, HttpServletResponse response) {
        Object userIdObj = redisTemplate.opsForValue().get(MFA_KEY_PREFIX + request.getMfaTicket());
        if (userIdObj == null) {
            throw new UnauthorizedException("MFA ticket has expired or is invalid");
        }
        redisTemplate.delete(MFA_KEY_PREFIX + request.getMfaTicket());

        UUID userId = UUID.fromString(userIdObj.toString());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        UserMfa mfa = user.getMfa();
        if (mfa == null || !mfa.isEnabled()) {
            throw new UnauthorizedException("MFA is not enabled for this user account");
        }

        String submittedCode = request.getTotpCode() != null ? request.getTotpCode().trim() : "";
        boolean verified = false;

        // 1. Validate Time-based One-Time Password (TOTP)
        String decryptedSecret = cryptoVaultService.decrypt(mfa.getTotpSecretEncrypted());
        if (totpService.verifyCode(decryptedSecret, submittedCode)) {
            verified = true;
        }

        // 2. Fallback: Check emergency backup recovery codes
        if (!verified && mfa.getBackupCodesHash() != null && !mfa.getBackupCodesHash().isEmpty()) {
            String cleanBackupCode = submittedCode.toUpperCase().replaceAll("\\s+", "");
            if (cleanBackupCode.length() == 8 && !cleanBackupCode.contains("-")) {
                cleanBackupCode = cleanBackupCode.substring(0, 4) + "-" + cleanBackupCode.substring(4);
            }

            for (int i = 0; i < mfa.getBackupCodesHash().size(); i++) {
                String hash = mfa.getBackupCodesHash().get(i);
                if (passwordEncoder.matches(cleanBackupCode, hash)) {
                    verified = true;
                    mfa.getBackupCodesHash().remove(i);
                    userMfaRepository.save(mfa);
                    log.info("Emergency backup code used for user {}. Remaining codes: {}",
                            user.getId(), mfa.getBackupCodesHash().size());
                    break;
                }
            }
        }

        if (!verified) {
            throw new UnauthorizedException("Invalid MFA verification code or recovery code");
        }

        return completeAuthentication(user, response);
    }

    @Transactional
    public AuthDto.RefreshTokenResponse refreshToken(String rawRefreshToken, HttpServletResponse response) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Missing refresh token");
        }

        String redisKey = REFRESH_KEY_PREFIX + rawRefreshToken;
        @SuppressWarnings("unchecked")
        Map<String, Object> sessionData = (Map<String, Object>) redisTemplate.opsForValue().get(redisKey);

        if (sessionData == null) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Check reuse detection
        Boolean revoked = (Boolean) sessionData.get("revoked");
        String familyId = (String) sessionData.get("familyId");
        if (Boolean.TRUE.equals(revoked)) {
            // Replay attack detected! Revoke all tokens in this family
            log.warn("Detected refresh token reuse for family {}! Revoking family.", familyId);
            revokeTokenFamily(familyId);
            throw new UnauthorizedException("Token reuse detected. Session terminated.");
        }

        // Invalidate current token
        sessionData.put("revoked", true);
        redisTemplate.opsForValue().set(redisKey, sessionData, Duration.ofSeconds(refreshTokenExpirationSeconds));

        UUID userId = UUID.fromString((UUID.fromString(sessionData.get("userId").toString()).toString()));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User no longer exists"));

        // Issue new rotated refresh token in the same family
        String newRefreshToken = UUID.randomUUID().toString();
        Map<String, Object> newSessionData = new HashMap<>();
        newSessionData.put("userId", userId.toString());
        newSessionData.put("familyId", familyId);
        newSessionData.put("revoked", false);
        redisTemplate.opsForValue().set(REFRESH_KEY_PREFIX + newRefreshToken, newSessionData, Duration.ofSeconds(refreshTokenExpirationSeconds));

        // Set rotated cookie
        setRefreshTokenCookie(response, newRefreshToken);

        UUID defaultOrgId = user.getMemberships().isEmpty() ? null : user.getMemberships().get(0).getOrganization().getId();
        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.isSuperAdmin(), defaultOrgId);

        return AuthDto.RefreshTokenResponse.builder()
                .accessToken(newAccessToken)
                .build();
    }

    public void logout(String rawRefreshToken, HttpServletResponse response) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            redisTemplate.delete(REFRESH_KEY_PREFIX + rawRefreshToken);
        }
        clearRefreshTokenCookie(response);
    }

    @Transactional(readOnly = true)
    public AuthDto.UserProfileResponse getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        return buildUserProfile(user);
    }

    private AuthDto.LoginResponse completeAuthentication(User user, HttpServletResponse response) {
        // Issue refresh token
        String refreshToken = UUID.randomUUID().toString();
        String familyId = UUID.randomUUID().toString();

        Map<String, Object> sessionData = new HashMap<>();
        sessionData.put("userId", user.getId().toString());
        sessionData.put("familyId", familyId);
        sessionData.put("revoked", false);

        redisTemplate.opsForValue().set(REFRESH_KEY_PREFIX + refreshToken, sessionData, Duration.ofSeconds(refreshTokenExpirationSeconds));
        setRefreshTokenCookie(response, refreshToken);

        UUID defaultOrgId = user.getMemberships().isEmpty() ? null : user.getMemberships().get(0).getOrganization().getId();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.isSuperAdmin(), defaultOrgId);

        AuthDto.UserProfileResponse profile = buildUserProfile(user);

        auditLogService.record(
                defaultOrgId, null, user.getId(), "USER",
                "USER_LOGIN", "user", user.getId().toString(),
                null, Map.of("email", user.getEmail()), null, null
        );

        return AuthDto.LoginResponse.builder()
                .accessToken(accessToken)
                .mfaRequired(false)
                .user(profile)
                .build();
    }

    private void revokeTokenFamily(String familyId) {
        // Invalidate in Redis
        try {
            Set<String> keys = redisTemplate.keys(REFRESH_KEY_PREFIX + "*");
            if (keys != null) {
                for (String key : keys) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) redisTemplate.opsForValue().get(key);
                    if (data != null && familyId.equals(data.get("familyId"))) {
                        redisTemplate.delete(key);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to revoke token family: {}", e.getMessage());
        }
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(cookieName, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/admin/api/v1/auth");
        cookie.setMaxAge((int) refreshTokenExpirationSeconds);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/admin/api/v1/auth");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private AuthDto.UserProfileResponse buildUserProfile(User user) {
        List<AuthDto.OrgMembershipSummary> orgSummaries = new ArrayList<>();

        for (Membership m : user.getMemberships()) {
            Organization org = m.getOrganization();
            List<String> roles = m.getRoleAssignments().stream()
                    .map(ra -> ra.getRole().getName())
                    .distinct()
                    .collect(Collectors.toList());

            Set<String> permissions = roleAssignmentRepository.findAllOrgPermissions(user.getId(), org.getId());

            orgSummaries.add(AuthDto.OrgMembershipSummary.builder()
                    .orgId(org.getId())
                    .name(org.getName())
                    .slug(org.getSlug())
                    .tier(org.getTier())
                    .roles(roles)
                    .permissions(permissions)
                    .build());
        }

        boolean mfaEnabled = user.getMfa() != null && user.getMfa().isEnabled();

        return AuthDto.UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .superAdmin(user.isSuperAdmin())
                .mfaEnabled(mfaEnabled)
                .organizations(orgSummaries)
                .build();
    }
}
