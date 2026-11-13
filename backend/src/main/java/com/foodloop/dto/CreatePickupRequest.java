package com.foodloop.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreatePickupRequest(
        @NotNull(message = "donationId is required") Long donationId,
        @NotNull(message = "scheduledPickupAt is required") LocalDateTime scheduledPickupAt) {
}