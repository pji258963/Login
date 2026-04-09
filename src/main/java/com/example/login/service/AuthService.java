package com.example.login.service;

import com.example.login.dto.request.*;
import com.example.login.dto.response.AuthResponse;
import com.example.login.entity.*;
import com.example.login.exception.*;
import com.example.login.repository.*;
import com.example.login.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Set;

/**
 * 認證核心業務邏輯
 * ┌─────────────────────────────────────────────────────────┐
 * │ 流程摘要                                                  │
 * │  register → (verify email) → login → (use API)          │
 * │  → refresh token → logout                               │
 * │  forgot password → reset password                       │
 * └─────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${security.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${security.lock-duration-minutes:30}")
    private int lockDurationMinutes;

    @Value("${jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    @Value("${app.email-verification-expiry-minutes:1440}")
    private int emailVerificationExpiryMinutes;

    @Value("${app.password-reset-expiry-minutes:60}")
    private int passwordResetExpiryMinutes;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ── 1. 註冊 ───────────────────────────────────────────────

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UsernameAlreadyExistsException(request.getUsername());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of("ROLE_USER"))
                .emailVerified(false)
                .enabled(true)
                .build();

        userRepository.save(user);

        // 產生 Email 驗證 Token 並寄送
        String verificationToken = generateSecureToken();
        emailVerificationTokenRepository.save(
                EmailVerificationToken.builder()
                        .token(verificationToken)
                        .user(user)
                        .expiryDate(LocalDateTime.now().plusMinutes(emailVerificationExpiryMinutes))
                        .build()
        );
        emailService.sendVerificationEmail(user.getEmail(), verificationToken);

        log.info("新使用者註冊: {}", user.getEmail());
    }

    // ── 2. Email 驗證 ─────────────────────────────────────────

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken evt = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("驗證連結無效或已使用"));

        if (evt.isExpired()) {
            emailVerificationTokenRepository.delete(evt);
            throw new InvalidTokenException("驗證連結已過期，請重新申請");
        }

        User user = evt.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        emailVerificationTokenRepository.delete(evt);
        log.info("Email 驗證成功: {}", user.getEmail());
    }

    // ── 3. 重新發送驗證信 ─────────────────────────────────────

    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UserNotFoundException("找不到此 Email 的帳號"));

        if (user.isEmailVerified()) {
            throw new InvalidTokenException("Email 已完成驗證");
        }

        // 刪除舊的驗證 Token
        emailVerificationTokenRepository.deleteByUser(user);

        String token = generateSecureToken();
        emailVerificationTokenRepository.save(
                EmailVerificationToken.builder()
                        .token(token)
                        .user(user)
                        .expiryDate(LocalDateTime.now().plusMinutes(emailVerificationExpiryMinutes))
                        .build()
        );
        emailService.sendVerificationEmail(user.getEmail(), token);
    }

    // ── 4. 登入 ───────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Email 或密碼錯誤"));

        // 帳號鎖定檢查
        checkAccountLock(user, email);

        // 驗證密碼（透過 Spring Security AuthenticationManager）
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (BadCredentialsException e) {
            handleFailedLogin(user, email);
            throw e;
        } catch (DisabledException e) {
            throw new DisabledException("帳號未啟用，請先驗證 Email");
        }

        // 登入成功：重設失敗計數
        if (user.getFailedLoginAttempts() > 0) {
            userRepository.resetFailedAttempts(email);
        }

        // 產生 Access Token & Refresh Token
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String accessToken = jwtService.generateAccessToken(userDetails);
        RefreshToken refreshToken = createRefreshToken(user, request.getDeviceInfo());

        log.info("使用者登入: {}", email);

        return buildAuthResponse(accessToken, refreshToken.getToken(), user);
    }

    // ── 5. Token 刷新（Refresh Token Rotation）───────────────

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken existingToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException("Refresh token 無效"));

        if (existingToken.isExpired()) {
            refreshTokenRepository.delete(existingToken);
            throw new InvalidTokenException("Refresh token 已過期，請重新登入");
        }

        User user = existingToken.getUser();

        // Token Rotation：刪除舊的，建立新的
        refreshTokenRepository.delete(existingToken);
        RefreshToken newRefreshToken = createRefreshToken(user, existingToken.getDeviceInfo());

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String newAccessToken = jwtService.generateAccessToken(userDetails);

        log.debug("Refresh token 已輪換: {}", user.getEmail());

        return buildAuthResponse(newAccessToken, newRefreshToken.getToken(), user);
    }

    // ── 6. 登出 ───────────────────────────────────────────────

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        // 將 Access Token 加入黑名單
        if (accessToken != null) {
            jwtService.blacklistToken(accessToken);
        }

        // 刪除 Refresh Token
        if (refreshToken != null) {
            refreshTokenRepository.findByToken(refreshToken)
                    .ifPresent(refreshTokenRepository::delete);
        }
    }

    // ── 7. 登出所有裝置 ───────────────────────────────────────

    @Transactional
    public void logoutAllDevices(String email, String currentAccessToken) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("使用者不存在"));

        jwtService.blacklistToken(currentAccessToken);
        refreshTokenRepository.deleteAllByUser(user);
        log.info("使用者登出所有裝置: {}", email);
    }

    // ── 8. 忘記密碼 ───────────────────────────────────────────

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().toLowerCase();
        // 不管帳號是否存在，都回傳同樣訊息（避免帳號枚舉攻擊）
        userRepository.findByEmail(email).ifPresent(user -> {
            // 刪除舊的重設 Token
            passwordResetTokenRepository.deleteAllByUser(user);

            String token = generateSecureToken();
            passwordResetTokenRepository.save(
                    PasswordResetToken.builder()
                            .token(token)
                            .user(user)
                            .expiryDate(LocalDateTime.now().plusMinutes(passwordResetExpiryMinutes))
                            .build()
            );
            emailService.sendPasswordResetEmail(user.getEmail(), token);
        });
        log.info("密碼重設請求: {}", email);
    }

    // ── 9. 重設密碼 ───────────────────────────────────────────

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken prt = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new InvalidTokenException("重設連結無效或已使用"));

        if (prt.isUsed()) {
            throw new InvalidTokenException("重設連結已使用，請重新申請");
        }
        if (prt.isExpired()) {
            passwordResetTokenRepository.delete(prt);
            throw new InvalidTokenException("重設連結已過期，請重新申請");
        }

        User user = prt.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // 標記為已使用並作廢所有 Refresh Token（強制重新登入）
        prt.setUsed(true);
        passwordResetTokenRepository.save(prt);
        refreshTokenRepository.deleteAllByUser(user);

        log.info("密碼重設成功: {}", user.getEmail());
    }

    // ── 私有方法 ──────────────────────────────────────────────

    private void checkAccountLock(User user, String email) {
        if (user.getLockTime() != null) {
            LocalDateTime unlockTime = user.getLockTime().plusMinutes(lockDurationMinutes);
            if (LocalDateTime.now().isBefore(unlockTime)) {
                long minutesLeft = java.time.Duration.between(LocalDateTime.now(), unlockTime).toMinutes() + 1;
                throw new AccountLockedException(
                        String.format("帳號已鎖定，請於 %d 分鐘後再試", minutesLeft));
            } else {
                // 鎖定時間已過，自動解鎖
                userRepository.resetFailedAttempts(email);
                User refreshed = userRepository.findByEmail(email).orElseThrow();
                refreshed.setLockTime(null);
                userRepository.save(refreshed);
            }
        }
    }

    private void handleFailedLogin(User user, String email) {
        userRepository.incrementFailedAttempts(email);

        int newCount = user.getFailedLoginAttempts() + 1;
        if (newCount >= maxFailedAttempts) {
            userRepository.lockUser(email, LocalDateTime.now());
            log.warn("帳號因多次登入失敗而鎖定: {}", email);
        } else {
            log.warn("登入失敗 ({}/{}) : {}", newCount, maxFailedAttempts, email);
        }
    }

    private RefreshToken createRefreshToken(User user, String deviceInfo) {
        String token = generateSecureToken();
        LocalDateTime expiryDate = LocalDateTime.now()
                .plusSeconds(refreshTokenExpirationMs / 1000);

        return refreshTokenRepository.save(
                RefreshToken.builder()
                        .token(token)
                        .user(user)
                        .expiryDate(expiryDate)
                        .deviceInfo(deviceInfo)
                        .build()
        );
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user) {
        long expiresIn = jwtService.extractExpiration(accessToken).getTime() - System.currentTimeMillis();
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresIn / 1000)
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles())
                .build();
    }

    /**
     * 產生加密安全的隨機 Token（48 bytes → 64 字元 Base64 URL-safe 字串）
     */
    private String generateSecureToken() {
        byte[] tokenBytes = new byte[48];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
