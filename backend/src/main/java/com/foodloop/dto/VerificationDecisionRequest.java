package com.foodloop.dto;

import jakarta.validation.constraints.Size;

public record VerificationDecisionRequest(@Size(max = 255) String reason) {
}