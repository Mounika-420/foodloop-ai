package com.foodloop.dto;

import java.time.LocalDateTime;

public record ProfileResponse(Long id, String email, String fullName, String phone, String role, String status,
                              boolean emailVerified, LocalDateTime createdAt) {
}
