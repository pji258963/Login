package com.example.login.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private Set<String> roles;
    private boolean emailVerified;
    private LocalDateTime createdAt;
}
