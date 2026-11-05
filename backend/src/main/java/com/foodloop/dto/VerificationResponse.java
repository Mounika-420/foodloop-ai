package com.foodloop.dto;

import com.foodloop.entity.enums.VerificationStatus;

import java.time.LocalDateTime;

public record VerificationResponse(
        Long userId,
        String type,
        String fullName,
        String email,
        String organizationName,
        String registrationNumber,
        VerificationStatus status,
        LocalDateTime createdAt) {
}