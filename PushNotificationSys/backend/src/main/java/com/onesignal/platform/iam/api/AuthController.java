package com.onesignal.platform.iam.api;

import com.onesignal.platform.common.security.TenantContextHolder;
import com.onesignal.platform.iam.api.dto.AuthDto;
import com.onesignal.platform.iam.application.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.jwt.cookie-name:push_refresh_token}")
    private String cookieName;

    @PostMapping("/login")
    public ResponseEntity<AuthDto.LoginResponse> login(
            @Valid @RequestBody AuthDto.LoginRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.login(request, response));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<AuthDto.LoginResponse> verifyMfa(
            @Valid @RequestBody AuthDto.MfaVerifyRequest request,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.verifyMfa(request, response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDto.RefreshTokenResponse> refresh(
            @CookieValue(name = "${app.jwt.cookie-name:push_refresh_token}", required = false) String refreshTokenCookie,
            HttpServletResponse response) {
        return ResponseEntity.ok(authService.refreshToken(refreshTokenCookie, response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${app.jwt.cookie-name:push_refresh_token}", required = false) String refreshTokenCookie,
            HttpServletResponse response) {
        authService.logout(refreshTokenCookie, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDto.UserProfileResponse> getCurrentUser() {
        UUID userId = TenantContextHolder.getUserId();
        return ResponseEntity.ok(authService.getUserProfile(userId));
    }
}
