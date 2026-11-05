package com.foodloop.dto;

import com.foodloop.entity.enums.MatchStatus;

import java.math.BigDecimal;

public record NgoOfferResponse(
        Long matchId,
        Integer rank,
        BigDecimal matchScore,
        BigDecimal distanceKm,
        Object breakdown,
        MatchStatus status,
        DonationResponse donation) {
}