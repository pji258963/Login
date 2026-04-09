package com.example.login.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "使用者名稱不可為空")
    @Size(min = 3, max = 50, message = "使用者名稱長度須在 3~50 字元之間")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "使用者名稱只能包含英文、數字與底線")
    private String username;

    @NotBlank(message = "Email 不可為空")
    @Email(message = "Email 格式不正確")
    @Size(max = 100, message = "Email 最長 100 字元")
    private String email;

    /**
     * 密碼強度規則：
     * - 至少 8 個字元
     * - 至少一個大寫字母
     * - 至少一個小寫字母
     * - 至少一個數字
     * - 至少一個特殊字元
     */
    @NotBlank(message = "密碼不可為空")
    @Size(min = 8, max = 100, message = "密碼長度須在 8~100 字元之間")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&_\\-#])[A-Za-z\\d@$!%*?&_\\-#]{8,}$",
        message = "密碼須包含大寫、小寫字母、數字及特殊字元(@$!%*?&_-#)"
    )
    private String password;
}
