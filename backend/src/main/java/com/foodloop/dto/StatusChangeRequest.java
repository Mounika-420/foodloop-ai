package com.foodloop.dto;

import com.foodloop.entity.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusChangeRequest(
        @NotNull UserStatus status,
        @Size(max = 255) String reason) {
}