package com.foodloop.dto;

import com.foodloop.entity.enums.DeliveryStatus;
import com.foodloop.entity.enums.PickupStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PickupResponse(
        Long id,
        Long donationId,
        String category,
        BigDecimal quantityKg,
        Long ngoId,
        String ngoName,
        Long volunteerId,
        LocalDateTime scheduledPickupAt,
        PickupStatus status,
        LocalDateTime assignedAt,
        LocalDateTime createdAt,
        LocalDateTime pickupWindowStart,
        LocalDateTime pickupWindowEnd,
        LocalDateTime expiresAt,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        Double distanceKm,
        Long deliveryId,
        DeliveryStatus deliveryStatus) {
}