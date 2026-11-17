package com.foodloop.dto;

import com.foodloop.entity.enums.Availability;
import jakarta.validation.constraints.NotNull;

public record AvailabilityRequest(@NotNull Availability availability) {
}