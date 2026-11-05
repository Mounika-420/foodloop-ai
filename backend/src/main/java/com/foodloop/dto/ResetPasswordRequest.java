package com.foodloop.dto;

import com.foodloop.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @StrongPassword String newPassword) {
}
