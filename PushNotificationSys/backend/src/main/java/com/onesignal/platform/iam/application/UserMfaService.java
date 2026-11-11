package com.onesignal.platform.iam.application;

import com.onesignal.platform.app.application.CryptoVaultService;
import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.common.exception.AppException;
import com.onesignal.platform.common.exception.ResourceNotFoundException;
import com.onesignal.platform.common.exception.UnauthorizedException;
import com.onesignal.platform.common.security.Argon2PasswordService;
import com.onesignal.platform.common.security.TotpService;
import com.onesignal.platform.iam.api.dto.AuthDto;
import com.onesignal.platform.iam.domain.User;
import com.onesignal.platform.iam.domain.UserMfa;
import com.onesignal.platform.iam.infrastructure.UserMfaRepository;
import com.onesignal.platform.iam.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class UserMfaService {

    private final UserRepository userRepository;
    private final UserMfaRepository userMfaRepository;
    private final TotpService totpService;
    private final CryptoVaultService cryptoVaultService;
    private final Argon2PasswordService passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AuditLogService auditLogService;

    private static final String MFA_SETUP_KEY_PREFIX = "auth:mfa:setup:";
    private static final Duration SETUP_TTL = Duration.ofMinutes(10);
    private static final String APP_NAME = "PushPlatform";

    @Transactional(readOnly = true)
    public AuthDto.MfaStatusResponse getMfaStatus(UUID userId) {
        UserMfa mfa = userMfaRepository.findByUserId(userId).orElse(null);
        if (mfa == null || !mfa.isEnabled()) {
            return AuthDto.MfaStatusResponse.builder()
                    .enabled(false)
                    .remainingBackupCodes(0)
                    .build();
        }
        int remaining = mfa.getBackupCodesHash() != null ? mfa.getBackupCodesHash().size() : 0;
        return AuthDto.MfaStatusResponse.builder()
                .enabled(true)
                .remainingBackupCodes(remaining)
                .build();
    }

    public AuthDto.MfaSetupResponse setupMfa(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String secret = totpService.generateSecret();
        List<String> backupCodes = totpService.generateBackupCodes(10);
        String otpauthUri = totpService.generateOtpAuthUri(APP_NAME, user.getEmail(), secret);
        String qrCodeDataUrl = totpService.generateQrCodeDataUrl(otpauthUri, 240);

        Map<String, Object> setupSession = new HashMap<>();
        setupSession.put("secret", secret);
        setupSession.put("backupCodes", backupCodes);

        redisTemplate.opsForValue().set(MFA_SETUP_KEY_PREFIX + userId, setupSession, SETUP_TTL);

        return AuthDto.MfaSetupResponse.builder()
                .secretKey(secret)
                .otpauthUri(otpauthUri)
                .qrCodeDataUrl(qrCodeDataUrl)
                .backupCodes(backupCodes)
                .build();
    }

    @Transactional
    public AuthDto.MfaStatusResponse confirmMfa(UUID userId, AuthDto.MfaConfirmRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String redisKey = MFA_SETUP_KEY_PREFIX + userId;
        @SuppressWarnings("unchecked")
        Map<String, Object> setupSession = (Map<String, Object>) redisTemplate.opsForValue().get(redisKey);

        if (setupSession == null || !setupSession.containsKey("secret")) {
            throw new AppException("MFA setup session has expired or is invalid. Please start setup again.", HttpStatus.BAD_REQUEST);
        }

        String secret = (String) setupSession.get("secret");
        @SuppressWarnings("unchecked")
        List<String> backupCodes = (List<String>) setupSession.get("backupCodes");

        String rawCode = request.getTotpCode() != null ? request.getTotpCode().trim().replaceAll("[\\s-]+", "") : "";
        if (rawCode.length() != 6) {
            throw new AppException("Invalid TOTP verification code: expected 6 digits, but received " + rawCode.length() + " digits.", HttpStatus.BAD_REQUEST);
        }

        if (!totpService.verifyCode(secret, rawCode)) {
            log.warn("TOTP code verification failed for user {}. Tested code: {}", userId, rawCode);
            throw new AppException("Invalid TOTP verification code. Please check that your device clock is synchronized and that you are using the most recently scanned authenticator account.", HttpStatus.BAD_REQUEST);
        }

        // Encrypt secret with AES-256-GCM
        String encryptedSecret = cryptoVaultService.encrypt(secret);

        // Hash backup recovery codes with Argon2id
        List<String> hashedCodes = backupCodes.stream()
                .map(passwordEncoder::encode)
                .collect(Collectors.toCollection(ArrayList::new));

        UserMfa mfa = userMfaRepository.findByUserId(userId).orElse(null);
        if (mfa == null) {
            mfa = UserMfa.builder()
                    .user(user)
                    .totpSecretEncrypted(encryptedSecret)
                    .enabled(true)
                    .backupCodesHash(hashedCodes)
                    .build();
        } else {
            mfa.setTotpSecretEncrypted(encryptedSecret);
            mfa.setEnabled(true);
            mfa.setBackupCodesHash(hashedCodes);
        }

        UserMfa savedMfa = userMfaRepository.save(mfa);
        if (savedMfa != null) {
            mfa = savedMfa;
        }
        redisTemplate.delete(redisKey);

        UUID defaultOrgId = user.getMemberships().isEmpty() ? null : user.getMemberships().get(0).getOrganization().getId();
        String targetId = mfa.getId() != null ? mfa.getId().toString() : user.getId().toString();
        auditLogService.record(
                defaultOrgId, null, user.getId(), "USER",
                "USER_MFA_ENABLED", "user_mfa", targetId,
                null, Map.of("email", user.getEmail()), null, null
        );

        log.info("MFA enabled successfully for user {}", user.getId());

        return AuthDto.MfaStatusResponse.builder()
                .enabled(true)
                .remainingBackupCodes(hashedCodes.size())
                .build();
    }

    @Transactional
    public void disableMfa(UUID userId, AuthDto.MfaDisableRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid password. Password confirmation is required to disable MFA.");
        }

        UserMfa mfa = userMfaRepository.findByUserId(userId).orElse(null);
        if (mfa != null) {
            userMfaRepository.delete(mfa);
            user.setMfa(null);
        }

        UUID defaultOrgId = user.getMemberships().isEmpty() ? null : user.getMemberships().get(0).getOrganization().getId();
        auditLogService.record(
                defaultOrgId, null, user.getId(), "USER",
                "USER_MFA_DISABLED", "user_mfa", userId.toString(),
                null, Map.of("email", user.getEmail()), null, null
        );

        log.info("MFA disabled for user {}", user.getId());
    }
}
