package com.foodloop.dto;

import com.foodloop.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 200) String currentPassword,
        @NotBlank @StrongPassword String newPassword) {
}
