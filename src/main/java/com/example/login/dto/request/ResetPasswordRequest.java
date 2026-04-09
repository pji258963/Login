package com.example.login.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Token 不可為空")
    private String token;

    @NotBlank(message = "新密碼不可為空")
    @Size(min = 8, max = 100, message = "密碼長度須在 8~100 字元之間")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#])[A-Za-z\\d@$!%*?&_\\-#]{8,}$",
        message = "密碼須包含大寫、小寫字母、數字及特殊字元"
    )
    private String newPassword;
}
