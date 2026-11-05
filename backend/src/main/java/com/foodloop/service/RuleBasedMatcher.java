package com.foodloop.service;

import com.foodloop.util.GeoUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure rule-based NGO ranking (same weights as the AI service contract: 30/25/15/15/15).
 * Used as the primary matcher until the Flask service is wired in, and as its fallback afterwards.
 */
public final class RuleBasedMatcher {

    public static final double MAX_RADIUS_KM = 50.0;
    public static final double SPEED_KMH = 30.0;
    public static final double FULL_EXPIRY_MARGIN_HOURS = 6.0;

    static final double W_DISTANCE = 30.0;
    static final double W_CATEGORY = 25.0;
    static final double W_QUANTITY = 15.0;
    static final double W_CAPACITY = 15.0;
    static final double W_EXPIRY = 15.0;
    static final double CATEGORY_GENERAL_POINTS = 10.0;
    static final double QUANTITY_GENERAL_POINTS = 7.5;

    private RuleBasedMatcher() {
    }

    public record DonationInfo(double quantityKg, double latitude, double longitude, LocalDateTime now,
                               LocalDateTime pickupStart, LocalDateTime pickupEnd, LocalDateTime expiresAt) {
    }

    /** openRequirementKg is null when the NGO has no open requirement for the donation category. */
    public record Candidate(long ngoId, double latitude, double longitude, double remainingCapacityKg,
                            Double openRequirementKg, LocalTime availableFrom, LocalTime availableTo) {
    }

    public record Result(long ngoId, int rank, BigDecimal score, BigDecimal distanceKm,
                         Map<String, BigDecimal> breakdown) {
    }

    private record Scored(long ngoId, BigDecimal score, BigDecimal distanceKm, Map<String, BigDecimal> breakdown) {
    }

    public static List<Result> rank(DonationInfo d, List<Candidate> candidates, int topN) {
        List<Scored> scored = new ArrayList<>();
        for (Candidate c : candidates) {
            Scored s = score(d, c);
            if (s != null) {
                scored.add(s);
            }
        }
        scored.sort((a, b) -> {
            int cmp = b.score().compareTo(a.score());
            if (cmp != 0) {
                return cmp;
            }
            cmp = a.distanceKm().compareTo(b.distanceKm());
            if (cmp != 0) {
                return cmp;
            }
            return Long.compare(a.ngoId(), b.ngoId());
        });
        List<Result> out = new ArrayList<>();
        int limit = Math.min(topN, scored.size());
        for (int i = 0; i < limit; i++) {
            Scored s = scored.get(i);
            out.add(new Result(s.ngoId(), i + 1, s.score(), s.distanceKm(), s.breakdown()));
        }
        return out;
    }

    private static Scored score(DonationInfo d, Candidate c) {
        if (d.quantityKg() <= 0) {
            return null;
        }
        double dist = GeoUtils.haversineKm(d.latitude(), d.longitude(), c.latitude(), c.longitude());
        if (dist > MAX_RADIUS_KM) {
            return null;
        }
        if (c.remainingCapacityKg() <= 0) {
            return null;
        }
        LocalDateTime travelEnd = d.now().plusSeconds(Math.round(dist / SPEED_KMH * 3600.0));
        LocalDateTime arrival = travelEnd.isAfter(d.pickupStart()) ? travelEnd : d.pickupStart();
        if (arrival.isAfter(d.pickupEnd()) || !arrival.isBefore(d.expiresAt())) {
            return null;
        }
        if (!withinHours(arrival.toLocalTime(), c.availableFrom(), c.availableTo())) {
            return null;
        }

        double marginHours = Duration.between(arrival, d.expiresAt()).toMinutes() / 60.0;
        boolean hasRequirement = c.openRequirementKg() != null && c.openRequirementKg() > 0;

        double distancePts = W_DISTANCE * (1.0 - dist / MAX_RADIUS_KM);
        double categoryPts = hasRequirement ? W_CATEGORY : CATEGORY_GENERAL_POINTS;
        double quantityPts = hasRequirement
                ? W_QUANTITY * Math.min(1.0, d.quantityKg() / c.openRequirementKg())
                : QUANTITY_GENERAL_POINTS;
        double capacityPts = W_CAPACITY * Math.min(1.0, c.remainingCapacityKg() / d.quantityKg());
        double expiryPts = W_EXPIRY * Math.min(1.0, marginHours / FULL_EXPIRY_MARGIN_HOURS);

        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        breakdown.put("distance", round2(distancePts));
        breakdown.put("category", round2(categoryPts));
        breakdown.put("quantity", round2(quantityPts));
        breakdown.put("capacity", round2(capacityPts));
        breakdown.put("expiry", round2(expiryPts));
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : breakdown.values()) {
            total = total.add(v);
        }
        return new Scored(c.ngoId(), total, round2(dist), breakdown);
    }

    private static boolean withinHours(LocalTime t, LocalTime from, LocalTime to) {
        if (from == null || to == null) {
            return true;
        }
        if (!from.isAfter(to)) {
            return !t.isBefore(from) && !t.isAfter(to);
        }
        return !t.isBefore(from) || !t.isAfter(to);
    }

    private static BigDecimal round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}