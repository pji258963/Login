package com.example.login.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refresh Token 實體
 * - 儲存於資料庫，使每次 refresh 後可進行 Rotation（舊 token 作廢）
 * - 每個裝置/Session 可擁有一個 refresh token
 */
@Entity
@Table(name = "refresh_tokens",
       indexes = @Index(name = "idx_refresh_token_value", columnList = "token"))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    /**
     * 可記錄裝置資訊（選用），方便管理多裝置 Session
     */
    @Column(length = 255)
    private String deviceInfo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiryDate);
    }
}
