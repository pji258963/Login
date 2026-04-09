package com.example.login.controller;

import com.example.login.dto.request.*;
import com.example.login.dto.response.AuthResponse;
import com.example.login.dto.response.MessageResponse;
import com.example.login.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 認證相關 API
 *
 * POST /api/auth/register           註冊
 * GET  /api/auth/verify-email       驗證 Email
 * POST /api/auth/resend-verification 重發驗證信
 * POST /api/auth/login              登入
 * POST /api/auth/refresh            刷新 Access Token
 * POST /api/auth/logout             登出
 * POST /api/auth/logout-all         登出所有裝置
 * POST /api/auth/forgot-password    忘記密碼
 * POST /api/auth/reset-password     重設密碼
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponse("註冊成功，請至信箱完成 Email 驗證"));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new MessageResponse("Email 驗證成功，現在可以登入"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerification(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.resendVerificationEmail(request.getEmail());
        return ResponseEntity.ok(new MessageResponse("驗證信已重新發送，請查收信箱"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) RefreshTokenRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String accessToken = extractToken(authHeader);
        String refreshToken = (request != null) ? request.getRefreshToken() : null;

        authService.logout(accessToken, refreshToken);
        return ResponseEntity.ok(new MessageResponse("已成功登出"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<MessageResponse> logoutAllDevices(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal UserDetails userDetails) {

        String accessToken = extractToken(authHeader);
        authService.logoutAllDevices(userDetails.getUsername(), accessToken);
        return ResponseEntity.ok(new MessageResponse("已登出所有裝置"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        // 不論帳號是否存在，回傳相同訊息（防止帳號枚舉）
        return ResponseEntity.ok(new MessageResponse("若此 Email 存在，重設連結已寄出"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(new MessageResponse("密碼重設成功，請重新登入"));
    }

    private String extractToken(String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
