package com.example.login.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Access Token 黑名單
 * - 使用者登出時，將尚未過期的 Access Token 加入黑名單
 * - JwtAuthenticationFilter 每次驗證時查詢此表
 * - 定期清理過期的黑名單記錄
 */
@Entity
@Table(name = "blacklisted_tokens",
       indexes = @Index(name = "idx_blacklisted_token", columnList = "token"))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlacklistedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 儲存 JWT 的 jti (JWT ID) claim 而非完整 token，節省儲存空間
     */
    @Column(nullable = false, unique = true, length = 100)
    private String jti;

    /**
     * Token 原始過期時間，用於排程清理
     */
    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime blacklistedAt;

    @PrePersist
    protected void onCreate() {
        blacklistedAt = LocalDateTime.now();
    }
}
