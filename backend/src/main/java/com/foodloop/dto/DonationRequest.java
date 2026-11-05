package com.foodloop.dto;

import com.foodloop.entity.enums.StorageCondition;
import com.foodloop.entity.enums.Unit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DonationRequest(
        @NotNull Integer categoryId,
        Integer foodItemId,
        @NotBlank @Size(max = 120) String foodName,
        @NotNull @DecimalMin(value = "0.01", message = "Quantity must be positive")
        @DecimalMax(value = "100000", message = "Quantity is too large") BigDecimal quantity,
        @NotNull Unit unit,
        @NotNull LocalDateTime preparedAt,
        @NotNull LocalDateTime expiresAt,
        @NotNull StorageCondition storageCondition,
        @Size(max = 200) String allergens,
        Boolean vegetarian,
        @Size(max = 500) String description,
        @Valid LocationInput pickupLocation,
        @NotNull LocalDateTime pickupWindowStart,
        @NotNull LocalDateTime pickupWindowEnd,
        @NotNull Boolean safetyDeclared) {

    public record LocationInput(
            @NotBlank @Size(max = 255) String addressLine,
            @Size(max = 100) String area,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Pincode must be 6 digits") String pincode,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude) {
    }
}