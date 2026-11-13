package com.foodloop.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ConfirmDeliveryRequest(
        @NotNull(message = "quantityReceivedKg is required")
        @DecimalMin(value = "0.01", message = "Received quantity must be positive") BigDecimal quantityReceivedKg,
        @Size(max = 300) String note) {
}