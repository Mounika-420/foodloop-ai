package com.foodloop.dto;

import com.foodloop.entity.enums.UserStatus;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String role,
        UserStatus status,
        boolean emailVerified,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt) {
}