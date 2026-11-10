package com.onesignal.platform.iam.application;

import com.onesignal.platform.audit.application.AuditLogService;
import com.onesignal.platform.common.exception.UnauthorizedException;
import com.onesignal.platform.common.security.Argon2PasswordService;
import com.onesignal.platform.common.security.JwtTokenProvider;
import com.onesignal.platform.iam.api.dto.AuthDto;
import com.onesignal.platform.iam.domain.Organization;
import com.onesignal.platform.iam.domain.User;
import com.onesignal.platform.iam.infrastructure.MemberRoleAssignmentRepository;
import com.onesignal.platform.iam.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MemberRoleAssignmentRepository roleAssignmentRepository;

    @Mock
    private Argon2PasswordService passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private HttpServletResponse httpServletResponse;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        authService = new AuthService(
                userRepository,
                roleAssignmentRepository,
                passwordEncoder,
                jwtTokenProvider,
                redisTemplate,
                auditLogService
        );

        ReflectionTestUtils.setField(authService, "cookieName", "push_refresh_token");
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationSeconds", 604800L);
        ReflectionTestUtils.setField(authService, "cookieSecure", false);
    }

    @Test
    @DisplayName("Login with valid password returns JWT and user profile")
    void login_validCredentials_success() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("test@pushnotify.local")
                .passwordHash("argon2_hash")
                .fullName("Test User")
                .superAdmin(false)
                .status("ACTIVE")
                .build();
        user.setId(userId);

        AuthDto.LoginRequest request = new AuthDto.LoginRequest();
        request.setEmail("test@pushnotify.local");
        request.setPassword("Password123!");

        when(userRepository.findByEmailWithMemberships("test@pushnotify.local")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "argon2_hash")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(eq(userId), eq("test@pushnotify.local"), eq(false), any()))
                .thenReturn("mock_jwt_access_token");

        AuthDto.LoginResponse response = authService.login(request, httpServletResponse);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock_jwt_access_token");
        assertThat(response.isMfaRequired()).isFalse();
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getEmail()).isEqualTo("test@pushnotify.local");

        verify(valueOperations).set(startsWith("auth:refresh:"), any(), any(Duration.class));
        verify(httpServletResponse).addCookie(any());
    }

    @Test
    @DisplayName("Login with invalid password throws UnauthorizedException")
    void login_invalidPassword_throwsUnauthorized() {
        User user = User.builder()
                .email("test@pushnotify.local")
                .passwordHash("argon2_hash")
                .fullName("Test User")
                .superAdmin(false)
                .status("ACTIVE")
                .build();

        AuthDto.LoginRequest request = new AuthDto.LoginRequest();
        request.setEmail("test@pushnotify.local");
        request.setPassword("WrongPassword");

        when(userRepository.findByEmailWithMemberships("test@pushnotify.local")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "argon2_hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, httpServletResponse))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    @DisplayName("Refresh token rotation successfully issues new access token")
    void refreshToken_validToken_rotatesSuccessfully() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("test@pushnotify.local")
                .fullName("Test User")
                .superAdmin(false)
                .status("ACTIVE")
                .build();
        user.setId(userId);

        String rawToken = "valid_refresh_token_uuid";
        Map<String, Object> session = new HashMap<>();
        session.put("userId", userId.toString());
        session.put("familyId", "family_123");
        session.put("revoked", false);

        when(valueOperations.get("auth:refresh:" + rawToken)).thenReturn(session);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(eq(userId), eq("test@pushnotify.local"), eq(false), any()))
                .thenReturn("new_rotated_jwt_token");

        AuthDto.RefreshTokenResponse response = authService.refreshToken(rawToken, httpServletResponse);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new_rotated_jwt_token");
        // Prior token marked revoked
        assertThat(session.get("revoked")).isEqualTo(true);
        verify(httpServletResponse).addCookie(any());
    }

    @Test
    @DisplayName("Refresh token reuse detection throws UnauthorizedException")
    void refreshToken_reusedToken_triggersRevocationAndThrows() {
        String reusedToken = "already_revoked_token";
        Map<String, Object> session = new HashMap<>();
        session.put("userId", UUID.randomUUID().toString());
        session.put("familyId", "family_compromised");
        session.put("revoked", true);

        when(valueOperations.get("auth:refresh:" + reusedToken)).thenReturn(session);

        assertThatThrownBy(() -> authService.refreshToken(reusedToken, httpServletResponse))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Token reuse detected");
    }
}
