package com.example.login.controller;

import com.example.login.dto.response.UserResponse;
import com.example.login.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 使用者管理 API
 *
 * GET  /api/users/me           取得當前使用者資訊
 * GET  /api/admin/users        取得所有使用者（ADMIN）
 * PATCH /api/admin/users/{id}/disable  停用帳號（ADMIN）
 * PATCH /api/admin/users/{id}/enable   啟用帳號（ADMIN）
 * PATCH /api/admin/users/{id}/unlock   解鎖帳號（ADMIN）
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/users/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userService.getCurrentUser(userDetails.getUsername()));
    }

    @GetMapping("/api/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PatchMapping("/api/admin/users/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> disableUser(@PathVariable UUID id) {
        userService.disableUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/admin/users/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> enableUser(@PathVariable UUID id) {
        userService.enableUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/admin/users/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unlockUser(@PathVariable UUID id) {
        userService.unlockUser(id);
        return ResponseEntity.noContent().build();
    }
}
