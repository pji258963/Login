package com.example.login.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Email 發送服務
 * - dev-mode=true 時，僅在 console 印出連結（不需要 SMTP 設定）
 * - 正式環境設 app.email.dev-mode=false 並配置 SMTP
 */
@Slf4j
@Service
public class EmailService {

    @Value("${app.email.dev-mode:true}")
    private boolean devMode;

    @Value("${app.base-url}")
    private String baseUrl;

    // 若 devMode=true，此 bean 可能不存在，用 required=false
    private final JavaMailSender mailSender;

    public EmailService(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String toEmail, String token) {
        String link = baseUrl + "/api/auth/verify-email?token=" + token;

        if (devMode) {
            log.info("====================================================");
            log.info("[DEV] Email 驗證連結 → {}", link);
            log.info("====================================================");
            return;
        }

        sendEmail(toEmail,
                "【登入系統】Email 驗證",
                "請點擊以下連結完成 Email 驗證（24小時內有效）：\n\n" + link +
                "\n\n若非本人操作，請忽略此信。");
    }

    public void sendPasswordResetEmail(String toEmail, String token) {
        String link = baseUrl + "/api/auth/reset-password?token=" + token;

        if (devMode) {
            log.info("====================================================");
            log.info("[DEV] 密碼重設連結 → {}", link);
            log.info("====================================================");
            return;
        }

        sendEmail(toEmail,
                "【登入系統】密碼重設",
                "請點擊以下連結重設您的密碼（60分鐘內有效）：\n\n" + link +
                "\n\n若非本人操作，請立即聯繫客服。");
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Email 已發送至: {}", to);
        } catch (Exception e) {
            log.error("Email 發送失敗 [{}]: {}", to, e.getMessage());
            // 不拋出例外，避免因 Email 失敗影響主要流程
            // 正式環境可考慮加入重試機制（如 Spring Retry）
        }
    }
}
