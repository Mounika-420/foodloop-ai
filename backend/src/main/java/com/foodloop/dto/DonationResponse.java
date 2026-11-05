package com.foodloop.dto;

import com.foodloop.entity.enums.DonationStatus;
import com.foodloop.entity.enums.StorageCondition;
import com.foodloop.entity.enums.Unit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DonationResponse(
        Long id,
        String donationCode,
        CategoryRef category,
        String foodName,
        BigDecimal quantity,
        Unit unit,
        BigDecimal quantityKg,
        LocalDateTime preparedAt,
        LocalDateTime expiresAt,
        StorageCondition storageCondition,
        String allergens,
        boolean vegetarian,
        String description,
        LocationResponse pickupLocation,
        LocalDateTime pickupWindowStart,
        LocalDateTime pickupWindowEnd,
        boolean safetyDeclared,
        DonationStatus status,
        Long donorId,
        LocalDateTime createdAt) {

    public record CategoryRef(Integer id, String name) {
    }

    public record LocationResponse(Long id, String addressLine, String area, String city, String state,
                                   String pincode, BigDecimal latitude, BigDecimal longitude) {
    }
}