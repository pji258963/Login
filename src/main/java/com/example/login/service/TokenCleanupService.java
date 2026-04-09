package com.example.login.service;

import com.example.login.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 定期清理過期 Token，避免資料庫無限膨脹
 * 每小時執行一次（可依需求調整）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Scheduled(cron = "0 0 * * * *")  // 每小時整點執行
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        log.info("開始清理過期 Token...");

        refreshTokenRepository.deleteAllExpiredTokens(now);
        blacklistedTokenRepository.deleteAllExpiredTokens(now);
        passwordResetTokenRepository.deleteAllExpiredTokens(now);
        emailVerificationTokenRepository.deleteAllExpiredTokens(now);

        log.info("過期 Token 清理完成");
    }
}
