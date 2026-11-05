package com.foodloop.dto;

import com.foodloop.entity.enums.MatchStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MatchResponse(
        Long matchId,
        Long donationId,
        Long ngoId,
        String organizationName,
        Integer rank,
        BigDecimal matchScore,
        BigDecimal distanceKm,
        Object breakdown,
        MatchStatus status,
        LocalDateTime respondedAt,
        String rejectionReason) {
}