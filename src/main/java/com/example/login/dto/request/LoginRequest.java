package com.example.login.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email 不可為空")
    @Email(message = "Email 格式不正確")
    private String email;

    @NotBlank(message = "密碼不可為空")
    private String password;

    /**
     * 裝置識別資訊（選用），用於區分不同裝置的 Session
     */
    private String deviceInfo;
}
