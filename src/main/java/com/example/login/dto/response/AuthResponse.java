package com.example.login.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Set;

@Data
@Builder
public class AuthResponse {

    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;       // Access Token 剩餘秒數

    private String refreshToken;

    private String username;
    private String email;
    private Set<String> roles;
}
