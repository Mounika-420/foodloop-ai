package com.foodloop.service;

import com.foodloop.service.RuleBasedMatcher.Candidate;
import com.foodloop.service.RuleBasedMatcher.DonationInfo;
import com.foodloop.service.RuleBasedMatcher.Result;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedMatcherTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 10, 0);

    private DonationInfo donation(double qty) {
        return new DonationInfo(qty, 11.0, 76.95, NOW, NOW.plusMinutes(30), NOW.plusHours(3), NOW.plusHours(8));
    }

    private Candidate ngo(long id, double lat, double lon, double remaining, Double req) {
        return new Candidate(id, lat, lon, remaining, req, LocalTime.of(8, 0), LocalTime.of(21, 0));
    }

    @Test
    void perfectMatchScoresOneHundred() {
        List<Result> r = RuleBasedMatcher.rank(donation(20), List.of(ngo(1, 11.0, 76.95, 100, 20.0)), 5);
        assertEquals(1, r.size());
        assertEquals(new BigDecimal("100.00"), r.get(0).score());
        assertEquals(1, r.get(0).rank());
    }

    @Test
    void closerNgoRanksHigherWhenOtherFactorsAreEqual() {
        List<Result> r = RuleBasedMatcher.rank(donation(20),
                List.of(ngo(2, 11.2, 76.95, 100, 20.0), ngo(1, 11.0, 76.95, 100, 20.0)), 5);
        assertEquals(2, r.size());
        assertEquals(1L, r.get(0).ngoId());
        assertEquals(2L, r.get(1).ngoId());
        assertTrue(r.get(0).score().compareTo(r.get(1).score()) > 0);
    }

    @Test
    void ngoOutsideRadiusIsExcluded() {
        assertTrue(RuleBasedMatcher.rank(donation(20), List.of(ngo(1, 11.5, 76.95, 100, 20.0)), 5).isEmpty());
    }

    @Test
    void ngoClosedAtPickupTimeIsExcluded() {
        Candidate closed = new Candidate(1, 11.0, 76.95, 100, 20.0, LocalTime.of(20, 0), LocalTime.of(23, 0));
        assertTrue(RuleBasedMatcher.rank(donation(20), List.of(closed), 5).isEmpty());
    }

    @Test
    void ngoThatCannotArriveInsidePickupWindowIsExcluded() {
        DonationInfo tight = new DonationInfo(20, 11.0, 76.95, NOW, NOW.plusMinutes(10), NOW.plusHours(1), NOW.plusHours(8));
        assertTrue(RuleBasedMatcher.rank(tight, List.of(ngo(1, 11.36, 76.95, 100, 20.0)), 5).isEmpty());
    }

    @Test
    void ngoWithNoRemainingCapacityIsExcluded() {
        assertTrue(RuleBasedMatcher.rank(donation(20), List.of(ngo(1, 11.0, 76.95, 0, 20.0)), 5).isEmpty());
    }

    @Test
    void ngoWithoutOpenRequirementGetsGeneralPoints() {
        Result r = RuleBasedMatcher.rank(donation(20), List.of(ngo(1, 11.0, 76.95, 100, null)), 5).get(0);
        assertEquals(new BigDecimal("10.00"), r.breakdown().get("category"));
        assertEquals(new BigDecimal("7.50"), r.breakdown().get("quantity"));
    }

    @Test
    void quantityPointsScaleWithOpenRequirement() {
        Result r = RuleBasedMatcher.rank(donation(36), List.of(ngo(1, 11.0, 76.95, 150, 40.0)), 5).get(0);
        assertEquals(new BigDecimal("13.50"), r.breakdown().get("quantity"));
        assertEquals(new BigDecimal("15.00"), r.breakdown().get("capacity"));
    }

    @Test
    void onlyTopNAreReturnedWithConsecutiveRanks() {
        List<Candidate> many = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            many.add(ngo(i, 11.0 + i * 0.01, 76.95, 100, 20.0));
        }
        List<Result> r = RuleBasedMatcher.rank(donation(20), many, 5);
        assertEquals(5, r.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, r.get(i).rank());
        }
    }

    @Test
    void breakdownAddsUpToTheScore() {
        Result r = RuleBasedMatcher.rank(donation(20), List.of(ngo(1, 11.1, 76.95, 15, 30.0)), 5).get(0);
        BigDecimal sum = r.breakdown().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, sum.compareTo(r.score()));
        assertTrue(r.score().compareTo(new BigDecimal("100")) <= 0);
    }
}