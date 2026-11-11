package com.onesignal.platform.iam.application;

import com.onesignal.platform.app.application.CryptoVaultService;
import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.common.exception.AppException;
import com.onesignal.platform.common.exception.UnauthorizedException;
import com.onesignal.platform.common.security.Argon2PasswordService;
import com.onesignal.platform.common.security.TotpService;
import com.onesignal.platform.iam.api.dto.AuthDto;
import com.onesignal.platform.iam.domain.User;
import com.onesignal.platform.iam.domain.UserMfa;
import com.onesignal.platform.iam.infrastructure.UserMfaRepository;
import com.onesignal.platform.iam.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserMfaServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMfaRepository userMfaRepository;

    @Mock
    private TotpService totpService;

    @Mock
    private CryptoVaultService cryptoVaultService;

    @Mock
    private Argon2PasswordService passwordEncoder;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private AuditLogService auditLogService;

    private UserMfaService userMfaService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        userMfaService = new UserMfaService(
                userRepository,
                userMfaRepository,
                totpService,
                cryptoVaultService,
                passwordEncoder,
                redisTemplate,
                auditLogService
        );
    }

    @Test
    @DisplayName("getMfaStatus returns disabled when user has no MFA or enabled=false")
    void getMfaStatus_disabled() {
        UUID userId = UUID.randomUUID();
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.empty());

        AuthDto.MfaStatusResponse status = userMfaService.getMfaStatus(userId);
        assertThat(status.isEnabled()).isFalse();
        assertThat(status.getRemainingBackupCodes()).isEqualTo(0);
    }

    @Test
    @DisplayName("getMfaStatus returns enabled with count of remaining backup codes")
    void getMfaStatus_enabled() {
        UUID userId = UUID.randomUUID();
        UserMfa mfa = UserMfa.builder()
                .enabled(true)
                .backupCodesHash(List.of("h1", "h2", "h3"))
                .build();
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(mfa));

        AuthDto.MfaStatusResponse status = userMfaService.getMfaStatus(userId);
        assertThat(status.isEnabled()).isTrue();
        assertThat(status.getRemainingBackupCodes()).isEqualTo(3);
    }

    @Test
    @DisplayName("setupMfa generates secret, QR code data URL, backup codes, and caches session in Redis")
    void setupMfa_success() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().email("admin@pushnotify.local").build();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(totpService.generateSecret()).thenReturn("JBSWY3DPEHPK3PXP");
        when(totpService.generateBackupCodes(10)).thenReturn(List.of("CODE-1111", "CODE-2222"));
        when(totpService.generateOtpAuthUri(anyString(), eq("admin@pushnotify.local"), eq("JBSWY3DPEHPK3PXP")))
                .thenReturn("otpauth://totp/PushPlatform:admin?secret=JBSWY3DPEHPK3PXP");
        when(totpService.generateQrCodeDataUrl(anyString(), anyInt())).thenReturn("data:image/png;base64,mockQrData");

        AuthDto.MfaSetupResponse response = userMfaService.setupMfa(userId);

        assertThat(response.getSecretKey()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(response.getQrCodeDataUrl()).isEqualTo("data:image/png;base64,mockQrData");
        assertThat(response.getBackupCodes()).hasSize(2);

        verify(valueOperations).set(eq("auth:mfa:setup:" + userId), anyMap(), eq(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("confirmMfa with valid TOTP code encrypts secret, hashes backup codes, and enables MFA")
    void confirmMfa_success() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().email("admin@pushnotify.local").build();
        user.setId(userId);

        Map<String, Object> session = new HashMap<>();
        session.put("secret", "JBSWY3DPEHPK3PXP");
        session.put("backupCodes", List.of("CODE-1111", "CODE-2222"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(valueOperations.get("auth:mfa:setup:" + userId)).thenReturn(session);
        when(totpService.verifyCode("JBSWY3DPEHPK3PXP", "123456")).thenReturn(true);
        when(cryptoVaultService.encrypt("JBSWY3DPEHPK3PXP")).thenReturn("enc_gcm_secret");
        when(passwordEncoder.encode(anyString())).thenReturn("argon2_hash");
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userMfaRepository.save(any(UserMfa.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthDto.MfaConfirmRequest request = new AuthDto.MfaConfirmRequest();
        request.setTotpCode("123456");

        AuthDto.MfaStatusResponse response = userMfaService.confirmMfa(userId, request);

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getRemainingBackupCodes()).isEqualTo(2);

        verify(userMfaRepository).save(any(UserMfa.class));
        verify(redisTemplate).delete("auth:mfa:setup:" + userId);
        verify(auditLogService).record(any(), any(), eq(userId), any(), eq("USER_MFA_ENABLED"), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("confirmMfa throws AppException when session has expired")
    void confirmMfa_expiredSession_throws() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().email("admin@pushnotify.local").build();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(valueOperations.get("auth:mfa:setup:" + userId)).thenReturn(null);

        AuthDto.MfaConfirmRequest request = new AuthDto.MfaConfirmRequest();
        request.setTotpCode("123456");

        assertThatThrownBy(() -> userMfaService.confirmMfa(userId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("expired or is invalid");
    }

    @Test
    @DisplayName("disableMfa removes MFA record when valid password provided")
    void disableMfa_validPassword_success() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("admin@pushnotify.local")
                .passwordHash("hashed_pwd")
                .build();
        user.setId(userId);

        UserMfa mfa = UserMfa.builder().user(user).enabled(true).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "hashed_pwd")).thenReturn(true);
        when(userMfaRepository.findByUserId(userId)).thenReturn(Optional.of(mfa));

        AuthDto.MfaDisableRequest request = new AuthDto.MfaDisableRequest();
        request.setPassword("Password123!");

        userMfaService.disableMfa(userId, request);

        verify(userMfaRepository).delete(mfa);
        verify(auditLogService).record(any(), any(), eq(userId), any(), eq("USER_MFA_DISABLED"), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("disableMfa throws UnauthorizedException when password does not match")
    void disableMfa_invalidPassword_throws() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("admin@pushnotify.local")
                .passwordHash("hashed_pwd")
                .build();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed_pwd")).thenReturn(false);

        AuthDto.MfaDisableRequest request = new AuthDto.MfaDisableRequest();
        request.setPassword("WrongPassword");

        assertThatThrownBy(() -> userMfaService.disableMfa(userId, request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid password");
    }
}
