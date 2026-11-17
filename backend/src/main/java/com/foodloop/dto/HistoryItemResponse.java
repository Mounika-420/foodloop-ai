package com.foodloop.dto;

import com.foodloop.entity.enums.DeliveryStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HistoryItemResponse(
        Long deliveryId,
        Long pickupId,
        Long donationId,
        String category,
        BigDecimal quantityKg,
        Long ngoId,
        Long volunteerId,
        DeliveryStatus status,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt,
        LocalDateTime receiptConfirmedAt,
        BigDecimal quantityReceivedKg,
        String failureReason) {
}