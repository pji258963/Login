package com.example.login.config;

import com.example.login.entity.User;
import com.example.login.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

/**
 * 應用程式啟動時的初始化設定
 * - 開發環境自動建立管理員帳號
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 開發環境預設管理員帳號（正式環境不會執行）
     * Email: admin@example.com
     * Password: Admin@123456
     */
    @Bean
    @Profile("!prod")  // 正式環境不執行
    public CommandLineRunner initDevAdmin() {
        return args -> {
            if (userRepository.existsByEmail("admin@example.com")) {
                return;
            }
            User admin = User.builder()
                    .username("admin")
                    .email("admin@example.com")
                    .password(passwordEncoder.encode("Admin@123456"))
                    .roles(Set.of("ROLE_USER", "ROLE_ADMIN"))
                    .emailVerified(true)
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            log.info("====================================================");
            log.info("[DEV] 管理員帳號已建立");
            log.info("[DEV] Email: admin@example.com");
            log.info("[DEV] Password: Admin@123456");
            log.info("====================================================");
        };
    }
}
