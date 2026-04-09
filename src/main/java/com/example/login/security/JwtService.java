package com.example.login.security;

import com.example.login.entity.BlacklistedToken;
import com.example.login.repository.BlacklistedTokenRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * JWT 核心服務
 * - 產生 Access Token（包含 jti、roles claim）
 * - 驗證 Token 合法性（簽名、過期、黑名單）
 * - 登出時將 jti 加入黑名單
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    // ── Token 產生 ────────────────────────────────────────────

    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", userDetails.getAuthorities()
                .stream()
                .map(a -> a.getAuthority())
                .toList());
        return buildToken(claims, userDetails.getUsername(), accessTokenExpirationMs);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .id(UUID.randomUUID().toString())   // jti：用於黑名單
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    // ── Token 驗證 ────────────────────────────────────────────

    /**
     * 完整驗證：合法簽名 + 未過期 + 不在黑名單
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = extractAllClaims(token);
            String username = claims.getSubject();
            String jti = claims.getId();

            boolean notExpired = !claims.getExpiration().before(new Date());
            boolean usernameMatch = username.equals(userDetails.getUsername());
            boolean notBlacklisted = !blacklistedTokenRepository.existsByJti(jti);

            return notExpired && usernameMatch && notBlacklisted;
        } catch (JwtException e) {
            log.warn("JWT 驗證失敗: {}", e.getMessage());
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    // ── 黑名單 ────────────────────────────────────────────────

    /**
     * 登出時，將 Access Token 的 jti 加入黑名單
     * 黑名單記錄保留至 Token 原始過期時間，之後由排程清除
     */
    @Transactional
    public void blacklistToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            String jti = claims.getId();

            if (!blacklistedTokenRepository.existsByJti(jti)) {
                LocalDateTime expiredAt = claims.getExpiration()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();

                blacklistedTokenRepository.save(
                        BlacklistedToken.builder()
                                .jti(jti)
                                .expiredAt(expiredAt)
                                .build()
                );
            }
        } catch (JwtException e) {
            log.warn("無法將 token 加入黑名單（已過期或格式錯誤）: {}", e.getMessage());
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
